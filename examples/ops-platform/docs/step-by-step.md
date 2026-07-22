# 16 步操作指南 — 逐项验证 Lumina 全部能力

> 每步对应一项能力，按顺序执行即可完成全部验证。
> 前置条件：Lumina standalone 已启动 + 模拟数据已生成。

---

## 准备工作

```bash
# 1. 启动 Lumina（替换为你的 API Key）
java -jar lumina-standalone.jar \
  --LLM_API_KEY=<你的LLM密钥> \
  --RAG_ENABLED=true \
  --RAG_EMBEDDING_API_KEY=<你的Embedding密钥> \
  --CODE_INTERPRETER_ENABLED=true \
  --MCP_ENABLED=true \
  --lumina.ops.enabled=true \
  --REFLECTIVE_MEMORY_ENABLED=true

# 2. 生成模拟数据（先 normal 后 critical 对比）
python3 examples/ops-platform/scripts/gen_mock_data.py --mode critical

# 3. 登录（所有 API 调用都需要 Token）
TOKEN=$(curl -sS -X POST http://localhost:8080/api/v1/base/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])")
```

---

## ① Agent 执行（ReAct / Plan-Execute / SSE）

```bash
# 创建运维 Agent
curl -sS -X POST http://localhost:8080/api/v1/agents \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "agentName": "运维巡检助手",
    "agentType": "ops-inspector",
    "tools": "ops.readLogs,ops.readMetrics",
    "rateLimit": 10,
    "maxConcurrent": 1
  }'

# 执行（记下返回的 agentId，假设为 100）
curl -sS -X POST http://localhost:8080/api/v1/agents/100/execute \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"task":"检查系统健康状态"}'
```

## ② 多模态执行

```bash
# 先生成测试图
python3 examples/ops-platform/scripts/gen_chart.py

# 上传图片
FILE_UUID=$(curl -sS -X POST http://localhost:8080/api/v1/files/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/tmp/lumina-ops/images/architecture.png" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['fileUuid'])")

# 多模态执行
curl -sS -X POST http://localhost:8080/api/v1/agents/100/execute/multimodal \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d "{\"task\":\"分析这张架构图，描述系统组件\",\"fileUuids\":[\"$FILE_UUID\"]}"
```

## ③ RAG 混合检索 + ④ 知识库管理

```bash
# 1. 前端创建知识库"运维知识库"
# 2. 上传 3 篇 SOP 文档：kb-nginx-sop.md / kb-database-sop.md / kb-incident-response.md
# 3. 将知识库挂载到运维 Agent（前端 Agent 编辑页）
# 4. 注入异常数据后执行
python3 examples/ops-platform/scripts/gen_mock_data.py --mode critical

curl -sS -X POST http://localhost:8080/api/v1/agents/100/execute \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"task":"系统出现 502 错误，请查询知识库给出排查建议"}'
# → Agent 应引用 Nginx SOP 内容
```

## ⑤ 自定义工具

已在 `OpsToolProvider.java` 实现，启动时 `--lumina.ops.enabled=true` 即自动注册。
Agent 执行时调用 `ops.readMetrics` / `ops.readLogs` 读取数据。

## ⑥ DAG 工作流

```bash
# 创建工作流（definitionYaml 填 config/workflow-dag.yaml 内容）
# 发布
curl -sS -X POST http://localhost:8080/api/v1/workflows/{id}/publish \
  -H "Authorization: Bearer $TOKEN"

# 执行
curl -sS -X POST http://localhost:8080/api/v1/workflows/{id}/execute \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"inputs":{"inspection_mode":"routine"}}'

# 查看实例
curl -sS http://localhost:8080/api/v1/workflows/instances?definitionId={id} \
  -H "Authorization: Bearer $TOKEN"
```

## ⑦ Flowable 工作流

```bash
# 创建工作流，definitionYaml 填 config/workflow-flowable.bpmn20.xml 内容
# 需要应用上下文存在 Flowable bean（standalone 默认有）
# 执行方式同 ⑥
```

## ⑧ 人工审批节点

```bash
# DAG 工作流执行到 human 节点时会暂停（status=PAUSED）
# 查看暂停的实例
curl -sS "http://localhost:8080/api/v1/workflows/instances?status=PAUSED" \
  -H "Authorization: Bearer $TOKEN"

# 审批通过（resume）
curl -sS -X POST "http://localhost:8080/api/v1/workflows/instances/{instanceId}/resume?decision=approve" \
  -H "Authorization: Bearer $TOKEN"
```

## ⑨ Cron 定时触发器

```bash
# 执行 config/trigger.sql（替换 agent_id 为实际值）
# 或通过 API 创建
curl -sS -X POST http://localhost:8080/api/v1/agent-triggers \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "name": "每小时巡检",
    "agentId": 100,
    "cronExpr": "0 0 * * * *",
    "inputText": "执行系统巡检",
    "misfirePolicy": "FIRE_ONCE"
  }'

# 验证 next_fire_at
curl -sS http://localhost:8080/api/v1/agent-triggers -H "Authorization: Bearer $TOKEN"
```

## ⑩ 限流 + 并发控制

```bash
# 限流验证：rateLimit=10，快速发 12 次
for i in $(seq 1 12); do
  curl -sS -X POST http://localhost:8080/api/v1/agents/100/execute \
    -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $TOKEN" \
    -d '{"task":"test"}' | python3 -c "import sys,json; d=json.load(sys.stdin); print(f'req{$i}: code={d[\"code\"]}')"
done
# → 第 11/12 次应返回 code=429

# 并发验证：maxConcurrent=1，同时发 2 次
for i in 1 2; do
  curl -sS -X POST http://localhost:8080/api/v1/agents/100/execute \
    -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $TOKEN" \
    -d '{"task":"long task"}' &
done
wait
# → 日志应出现 "并发限制触发"
```

## ⑪ 预算控制

```bash
# 创建预算规则（日限 1 元，会很快触发）
curl -sS -X POST http://localhost:8080/api/v1/budget/rules \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "ruleName": "运维巡检日预算",
    "scopeType": "AGENT",
    "scopeId": 100,
    "periodType": "DAILY",
    "limitAmount": 1.00,
    "alertThreshold": 80
  }'

# 执行几次后查看用量
curl -sS http://localhost:8080/api/v1/budget/usage -H "Authorization: Bearer $TOKEN"
# → 超预算后执行会返回 BUDGET_EXCEEDED
```

## ⑫ Code Interpreter

```bash
# Agent 的 tools 配置加上 code.execute
# 然后执行需要生成图表的任务
curl -sS -X POST http://localhost:8080/api/v1/agents/100/execute \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"task":"用 Python 生成一个 CPU 使用率的折线图"}'
# → Agent 调用 code.execute 执行 Python 代码
```

## ⑬ Webhook 通知

```bash
# 终端 1：启动接收端
python3 examples/ops-platform/scripts/webhook_receiver.py --secret my-secret

# 终端 2：在 Lumina 前端创建 Webhook
#   URL: http://<本机IP>:9999/webhook
#   Secret: my-secret
#   订阅事件: TRIGGER, TASK, BUDGET

# 触发通知（执行触发器失败 5 次或任务完成时）
# → 终端 1 会打印收到的告警
```

## ⑭ MCP 接入

```bash
# 注册自建的 MCP 文件服务器
curl -sS -X POST http://localhost:8080/api/v1/mcp/servers \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "name": "ops-fileserver",
    "transport": "stdio",
    "command": "python3",
    "args": ["examples/ops-platform/scripts/mcp_fileserver.py", "--root", "/tmp/lumina-ops/config"]
  }'

# 验证连接
curl -sS http://localhost:8080/api/v1/mcp/servers/ops-fileserver/health \
  -H "Authorization: Bearer $TOKEN"

# 查看 MCP 工具
curl -sS http://localhost:8080/api/v1/mcp/tools -H "Authorization: Bearer $TOKEN"
# → 应看到 mcp__ops-fileserver__list_files 和 mcp__ops-fileserver__read_file
```

## ⑮ 评估框架 + A/B 测试

```bash
# 导入评估数据集
curl -sS -X POST http://localhost:8080/api/v1/evaluations/datasets/import \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@examples/ops-platform/data/eval-dataset.yaml" \
  -F "name=运维巡检评估" \
  -F "agentType=ops-inspector"

# 运行评估（LLM Judge 评分）
curl -sS -X POST http://localhost:8080/api/v1/evaluations/datasets/{id}/runs \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"agentId": 100, "scoringMethod": "LLM_JUDGE", "threshold": 0.7}'

# 查看结果
curl -sS http://localhost:8080/api/v1/evaluations/runs/{runId} -H "Authorization: Bearer $TOKEN"

# A/B 测试：参考 config/ab-test.json 在前端创建
```

## ⑯ OpenAI 兼容端点

```bash
# 创建 API Token
API_TOKEN=$(curl -sS -X POST http://localhost:8080/api/v1/base/api-tokens \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"name":"ops-demo"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])")

# 用 OpenAI 格式调用
curl -sS http://localhost:8080/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $API_TOKEN" \
  -d '{
    "model": "agent-100",
    "messages": [{"role":"user","content":"检查系统状态"}],
    "stream": false
  }'

# 列出可用模型
curl -sS http://localhost:8080/v1/models -H "Authorization: Bearer $API_TOKEN"
```

---

## 贯穿能力验证

| 能力 | 验证方式 |
|------|---------|
| 审计日志 | 前端「系统管理 → 审计日志」查看每次操作 |
| Reflective Memory | 前端查看「长期记忆」，或多轮对话验证记忆 |
| 成本追踪 | `GET /api/v1/cost/summary` |
| 会话管理 | 创建会话 → 多轮对话 → 验证上下文 |
| Prompt 管理 | 创建 Prompt 版本 → 发布 → Agent 自动加载 |
| 多租户 | 创建新租户 → Agent/KB 隔离验证 |
