# 16 步操作指南 — 逐项验证 Lumina 全部能力

> 每步对应一项能力，所有步骤均提供 **curl 命令**（可复制粘贴执行），也可通过前端 UI 完成（参考 [frontend-guide.md](frontend-guide.md)）。
> 前置条件：Lumina standalone 已启动 + 模拟数据已生成 + OpsToolProvider 已编译。

---

## 准备工作

```bash
# 0a. 编译并启动（替换为你的 API Key）
cp examples/ops-platform/java/OpsToolProvider.java \
   lumina-agent-core/src/main/java/io/lumina/agent/tool/OpsToolProvider.java
mvn -pl lumina-standalone -am -DskipTests package

java -jar lumina-standalone/target/lumina-standalone-1.0.0-SNAPSHOT.jar \
  --LLM_API_KEY=<你的LLM密钥> \
  --RAG_ENABLED=true \
  --RAG_EMBEDDING_API_KEY=<你的Embedding密钥> \
  --CODE_INTERPRETER_ENABLED=true \
  --MCP_ENABLED=true \
  --lumina.ops.enabled=true \
  --REFLECTIVE_MEMORY_ENABLED=true

# 0b. 生成模拟数据
python3 examples/ops-platform/scripts/gen_mock_data.py --mode critical

# 0c. 登录获取 Token（后续所有命令都用这个 TOKEN）
TOKEN=$(curl -sS -X POST http://localhost:8080/api/v1/base/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])")
echo "Token: ${TOKEN:0:20}..."
```

---

## ④ 知识库管理 + 隔离（先做这步，Agent 创建时需要挂载）

```bash
# 1. 创建运维知识库
KB_ID=$(curl -sS -X POST http://localhost:8080/api/v1/knowledge-bases \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"name":"运维知识库","description":"Nginx/数据库/故障分级 SOP","visibility":"PRIVATE"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "知识库 ID: $KB_ID"

# 2. 上传 3 篇 SOP 文档（等待 RAG 分片入库）
for doc in kb-nginx-sop.md kb-database-sop.md kb-incident-response.md; do
  curl -sS -X POST http://localhost:8080/api/v1/knowledge/documents \
    -H "Authorization: Bearer $TOKEN" \
    -F "file=@examples/ops-platform/data/$doc" \
    -F "kbId=$KB_ID"
  echo "  → $doc 已上传"
  sleep 2  # 等待异步入库
done
```

## ① Agent 执行 + ⑤ 自定义工具 + ④ KB 挂载

```bash
# 3. 创建运维 Agent（挂载知识库 + 工具 + 限流 + 并发）
AGENT_ID=$(curl -sS -X POST http://localhost:8080/api/v1/agents \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d "{
    \"agentName\": \"运维巡检助手\",
    \"agentType\": \"ops-inspector\",
    \"description\": \"Plan-Execute 模式运维巡检\",
    \"tools\": \"ops.readLogs,ops.readMetrics,ops.executeCommand\",
    \"knowledgeBaseIds\": [$KB_ID],
    \"rateLimit\": 10,
    \"maxConcurrent\": 1
  }" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['agentId'])")
echo "Agent ID: $AGENT_ID"

# 4. 执行巡检（Agent 会调用 ops 工具读取数据 + RAG 查 SOP）
curl -sS -X POST "http://localhost:8080/api/v1/agents/${AGENT_ID}/execute" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"task":"执行系统巡检：读取CPU/内存指标，读取Nginx和应用日志，分析异常，如有问题查知识库SOP"}'
# → Agent 调用 ops.readMetrics + ops.readLogs，引用 SOP 给建议
```

## ③ RAG 检索验证

```bash
# 5. 执行时数据是 critical 模式（有 502/OOM），Agent 应引用 Nginx SOP
curl -sS -X POST "http://localhost:8080/api/v1/agents/${AGENT_ID}/execute" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"task":"系统出现大量 502 错误，请查询知识库给出排查步骤"}'
# → 响应中应包含 "upstream" "proxy_read_timeout" 等 SOP 关键词
```

## ⑩ 限流 + 并发控制

```bash
# 6. 限流验证：rateLimit=10，快速发 12 次
for i in $(seq 1 12); do
  CODE=$(curl -sS -X POST "http://localhost:8080/api/v1/agents/${AGENT_ID}/execute" \
    -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" \
    -d '{"task":"test"}' | python3 -c "import sys,json; print(json.load(sys.stdin).get('code',0))" 2>/dev/null)
  echo "  req$i: code=$CODE"
done
# → 第 11/12 次返回 code=429（限流触发）

# 7. 并发验证：maxConcurrent=1，同时发 2 次（需等限流窗口过期后测试）
for i in 1 2; do
  curl -sS -X POST "http://localhost:8080/api/v1/agents/${AGENT_ID}/execute" \
    -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" \
    -d '{"task":"concurrent test"}' > /tmp/resp_$i.json &
done
wait
echo "  并发结果: $(cat /tmp/resp_1.json | python3 -c 'import sys,json;print(json.load(sys.stdin).get(\"code\"))') / $(cat /tmp/resp_2.json | python3 -c 'import sys,json;print(json.load(sys.stdin).get(\"code\"))')"
# → 日志应出现 "并发限制触发: agentId=..., available=0"
```

## ⑥ DAG 工作流

```bash
# 8. 读取 YAML 并创建工作流（替换其中的 agentId 占位值）
WF_YAML=$(cat examples/ops-platform/config/workflow-dag.yaml \
  | sed "s/agentId: 1/agentId: ${AGENT_ID}/g" \
  | sed "s/agentId: 2/agentId: ${AGENT_ID}/g" \
  | sed "s/agentId: 3/agentId: ${AGENT_ID}/g")

WF_ID=$(curl -sS -X POST http://localhost:8080/api/v1/workflows \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d "$(python3 -c "import json,sys; print(json.dumps({'name':'ops-inspection','description':'运维巡检流水线','definitionYaml':open('examples/ops-platform/config/workflow-dag.yaml').read().replace('agentId: 1','agentId: ${AGENT_ID}').replace('agentId: 2','agentId: ${AGENT_ID}').replace('agentId: 3','agentId: ${AGENT_ID}')}))")" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "工作流 ID: $WF_ID"

# 9. 发布工作流
curl -sS -X POST "http://localhost:8080/api/v1/workflows/${WF_ID}/publish" \
  -H "Authorization: Bearer $TOKEN"

# 10. 执行工作流
INSTANCE_ID=$(curl -sS -X POST "http://localhost:8080/api/v1/workflows/${WF_ID}/execute" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"inputs":{"inspection_mode":"routine"}}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['instanceId'])")
echo "工作流实例 ID: $INSTANCE_ID"
```

## ⑧ 人工审批节点

```bash
# 11. 工作流在 P0 场景下会到 human 节点暂停（status=PAUSED）
curl -sS "http://localhost:8080/api/v1/workflows/instances?status=PAUSED" \
  -H "Authorization: Bearer $TOKEN"

# 12. 审批通过
curl -sS -X POST "http://localhost:8080/api/v1/workflows/instances/${INSTANCE_ID}/resume?decision=approve" \
  -H "Authorization: Bearer $TOKEN"
```

## ⑬ Webhook 通知

```bash
# 13. 启动 Python 接收端（终端 A）
python3 examples/ops-platform/scripts/webhook_receiver.py --port 9999 --secret ops-demo-secret

# 14. 创建 Webhook（终端 B）—— URL 指向接收端
WEBHOOK_SECRET=$(curl -sS -X POST http://localhost:8080/api/v1/notifications/webhooks \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "name": "运维告警通知",
    "url": "http://127.0.0.1:9999/webhook",
    "channel": "WEBHOOK",
    "events": ["TASK","TRIGGER","BUDGET","WORKFLOW"]
  }' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['secret'])")
echo "Webhook Secret: $WEBHOOK_SECRET"

# 15. 测试 Webhook
curl -sS -X POST http://localhost:8080/api/v1/notifications/webhooks/1/test \
  -H "Authorization: Bearer $TOKEN"
# → 终端 A 的接收端应打印收到的通知
```

## ⑨ Cron 定时触发器

```bash
# 16. 创建触发器
curl -sS -X POST http://localhost:8080/api/v1/agent-triggers \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d "{
    \"name\": \"每小时巡检\",
    \"agentId\": ${AGENT_ID},
    \"cronExpr\": \"0 0 * * * *\",
    \"inputText\": \"执行系统巡检\",
    \"misfirePolicy\": \"FIRE_ONCE\"
  }"

# 验证 next_fire_at
curl -sS http://localhost:8080/api/v1/agent-triggers -H "Authorization: Bearer $TOKEN"
```

## ⑪ 预算控制

```bash
# 17. 创建预算规则（日限 5 元）
curl -sS -X POST http://localhost:8080/api/v1/budget/rules \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d "{
    \"ruleName\": \"运维巡检日预算\",
    \"scopeType\": \"AGENT\",
    \"scopeId\": ${AGENT_ID},
    \"periodType\": \"DAILY\",
    \"limitAmount\": 5.00,
    \"alertThreshold\": 80
  }"

# 18. 查看用量
curl -sS http://localhost:8080/api/v1/budget/usage -H "Authorization: Bearer $TOKEN"
```

## ⑫ Code Interpreter

```bash
# 19. 确保 Agent 配置了 code.execute 工具（创建时加上或更新）
curl -sS -X PUT "http://localhost:8080/api/v1/agents/${AGENT_ID}" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "agentName": "运维巡检助手",
    "agentType": "ops-inspector",
    "tools": "ops.readLogs,ops.readMetrics,ops.executeCommand,code.execute"
  }'

# 20. 执行需要生成图表的任务
curl -sS -X POST "http://localhost:8080/api/v1/agents/${AGENT_ID}/execute" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"task":"用 Python 生成一个简单的 CPU 使用率折线图，x轴是时间，y轴是百分比"}'
# → Agent 调用 code.execute 执行 Python
```

## ② 多模态执行

```bash
# 21. 生成测试图
python3 examples/ops-platform/scripts/gen_chart.py

# 22. 上传图片
FILE_UUID=$(curl -sS -X POST http://localhost:8080/api/v1/files/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/tmp/lumina-ops/images/architecture.png" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['fileUuid'])")
echo "文件 UUID: $FILE_UUID"

# 23. 多模态执行
curl -sS -X POST "http://localhost:8080/api/v1/agents/${AGENT_ID}/execute/multimodal" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d "{\"task\":\"分析这张系统架构图，描述各个组件及其关系\",\"fileUuids\":[\"$FILE_UUID\"]}"
```

## ⑭ MCP 接入

```bash
# 24. 注册自建 MCP 文件服务器
curl -sS -X POST http://localhost:8080/api/v1/mcp/servers \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "name": "ops-fileserver",
    "transport": "stdio",
    "command": "python3",
    "args": ["examples/ops-platform/scripts/mcp_fileserver.py", "--root", "/tmp/lumina-ops/config"]
  }'

# 25. 验证连接 + 查看工具
curl -sS http://localhost:8080/api/v1/mcp/servers/ops-fileserver/health \
  -H "Authorization: Bearer $TOKEN"
curl -sS http://localhost:8080/api/v1/mcp/tools -H "Authorization: Bearer $TOKEN"
# → 应看到 mcp__ops-fileserver__list_files 和 mcp__ops-fileserver__read_file
```

## ⑮ 评估框架 + A/B 测试

```bash
# 26. 导入评估数据集
DS_ID=$(curl -sS -X POST http://localhost:8080/api/v1/evaluations/datasets/import \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@examples/ops-platform/data/eval-dataset.yaml" \
  -F "name=运维巡检评估" \
  -F "agentType=ops-inspector" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "数据集 ID: $DS_ID"

# 27. 运行评估（LLM Judge）
curl -sS -X POST "http://localhost:8080/api/v1/evaluations/datasets/${DS_ID}/runs" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d "{\"agentId\": ${AGENT_ID}, \"scoringMethod\": \"LLM_JUDGE\", \"threshold\": 0.7}"

# 28. A/B 测试（先创建两个 Prompt 版本）
curl -sS -X POST http://localhost:8080/api/v1/prompts \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"name":"ops-inspector-concise","content":"你是运维工程师，简洁输出巡检结果（3行）：状态/异常/严重度","agentType":"ops-inspector","variables":"task"}'

curl -sS -X POST http://localhost:8080/api/v1/prompts \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"name":"ops-inspector-detailed","content":"你是资深运维工程师，输出详细巡检报告（指标摘要/日志分析/异常发现/建议措施/严重度）","agentType":"ops-inspector-detailed","variables":"task"}'

# 29. 创建 A/B 实验
AB_ID=$(curl -sS -X POST http://localhost:8080/api/v1/ab-tests \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d "{
    \"name\": \"巡检报告格式对比\",
    \"agentId\": ${AGENT_ID},
    \"trafficPercent\": 100,
    \"variants\": [
      {\"name\":\"简洁版\",\"weight\":50,\"promptName\":\"ops-inspector-concise\"},
      {\"name\":\"详细版\",\"weight\":50,\"promptName\":\"ops-inspector-detailed\"}
    ]
  }" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")

# 30. 启动实验
curl -sS -X PUT "http://localhost:8080/api/v1/ab-tests/${AB_ID}/start" \
  -H "Authorization: Bearer $TOKEN"

# 执行 10+ 次后查看报告
curl -sS "http://localhost:8080/api/v1/ab-tests/${AB_ID}" -H "Authorization: Bearer $TOKEN"
```

## ⑯ OpenAI 兼容端点

```bash
# 31. 创建 API Token
API_TOKEN=$(curl -sS -X POST http://localhost:8080/api/v1/base/api-tokens \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"name":"ops-demo"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])")

# 32. 用 OpenAI 格式调用（非流式）
curl -sS http://localhost:8080/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $API_TOKEN" \
  -d "{
    \"model\": \"agent-${AGENT_ID}\",
    \"messages\": [{\"role\":\"user\",\"content\":\"检查系统状态\"}],
    \"stream\": false
  }"

# 33. 列出可用模型
curl -sS http://localhost:8080/v1/models -H "Authorization: Bearer $API_TOKEN"
```

## ⑦ Flowable 工作流

```bash
# 34. 创建 Flowable BPMN 流程（definitionYaml 填 XML 内容）
BPMN_XML=$(cat examples/ops-platform/config/workflow-flowable.bpmn20.xml \
  | sed "s/>1</>${AGENT_ID}</g" | sed "s/>2</>${AGENT_ID}</g" | sed "s/>3</>${AGENT_ID}</g")

curl -sS -X POST http://localhost:8080/api/v1/workflows \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d "$(python3 -c "import json; print(json.dumps({'name':'ops-inspection-flowable','description':'Flowable BPMN 运维流程','definitionYaml':open('examples/ops-platform/config/workflow-flowable.bpmn20.xml').read()}))")"
# → Flowable 引擎处理 BPMN XML
```

---

## 贯穿能力验证

| 能力 | API | 前端路径 |
|------|-----|---------|
| 审计日志 | `GET /api/v1/base/audit-logs` | 系统管理 → 审计日志 |
| Reflective Memory | `GET /api/v1/long-term-memories` | — |
| 成本追踪 | `GET /api/v1/cost/summary` | 成本分析 |
| 会话管理 | `POST /api/v1/conversations` | 会话列表 |
| Prompt 管理 | `GET /api/v1/prompts` | Prompt 管理 |
| 多租户 | 创建新租户 + Agent 隔离 | 租户管理 |
