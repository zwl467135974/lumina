# 常见问题

## 启动问题

### Q: standalone 启动报 "no main manifest attribute"

A: jar 包未正确 repackage。重新构建：
```bash
mvn -pl lumina-standalone -am clean package -DskipTests
```

### Q: Flyway 报 "checksum mismatch"

A: 迁移文件被修改过但数据库记录未更新。修复：
```bash
# 查看哪个版本不匹配
mysql -uroot -p lumina_dev -e "SELECT version, checksum FROM flyway_schema_history WHERE version='26';"

# 更新 checksum 为当前文件的计算值（替换 XXX 为新的 checksum）
mysql -uroot -p lumina_dev -e "UPDATE flyway_schema_history SET checksum=XXX WHERE version='26';"
```

### Q: Agent 执行报 "LLM API Key 未配置"

A: 启动时需要传入 API Key：
```bash
java -jar lumina-standalone.jar --LLM_API_KEY=sk-your-key
```

或创建 Agent 时在 `llmConfig.apiKey` 中指定。

### Q: RAG 检索返回空结果

A: 检查：
1. `--RAG_ENABLED=true` 是否设置
2. `--RAG_EMBEDDING_API_KEY` 是否配置
3. 文档是否已上传并分片成功（前端知识库详情页查看分片数）
4. Qdrant 是否运行（`docker ps | grep qdrant`）

## 工具问题

### Q: Agent 调用 ops.readLogs 报 "文件不存在"

A: 先生成模拟数据：
```bash
python3 examples/ops-platform/scripts/gen_mock_data.py --mode normal
```

### Q: OpsToolProvider 没有注册

A: 检查启动参数是否包含 `--lumina.ops.enabled=true`。

OpsToolProvider 标注了 `@ConditionalOnProperty(prefix = "lumina.ops", name = "enabled", havingValue = "true")`，默认不激活。

### Q: OpsToolProvider 需要放哪个目录编译？

A: `OpsToolProvider.java` 是示例代码，放在 `examples/` 目录不参与 Maven 编译。
使用时需复制到 `lumina-modules/lumina-business-agent/src/main/java/io/lumina/agent/tool/` 目录：

```bash
cp examples/ops-platform/java/OpsToolProvider.java \
   lumina-modules/lumina-business-agent/src/main/java/io/lumina/agent/tool/
```

然后 `mvn compile` 即可。

## MCP 问题

### Q: MCP server 注册失败

A: 检查：
1. `--MCP_ENABLED=true` 是否设置
2. `mcp_fileserver.py` 路径是否正确（相对于工作目录或绝对路径）
3. `--root` 目录是否存在（先运行 `gen_mock_data.py` 生成 config 目录）
4. Python 3 是否在 PATH 中

### Q: MCP tools 列表为空

A: 等待几秒后重试（MCP 连接是异步的），或检查 `mcp_fileserver.py` 是否正常运行：
```bash
echo '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' | python3 mcp_fileserver.py --root /tmp/lumina-ops/config
```

## Webhook 问题

### Q: 接收端收不到通知

A: 检查：
1. `webhook_receiver.py` 是否在运行
2. Lumina 中 Webhook URL 是否正确（`http://<本机IP>:9999`，不是 `localhost`）
3. 订阅的事件是否正确（如 `TRIGGER` 对应触发器事件）
4. 网络/防火墙是否允许访问 9999 端口

### Q: 签名验证失败

A: 确保 `--secret` 参数与 Lumina Webhook 配置中的密钥一致。
创建 Webhook 时返回的 `secret` 只显示一次，需保存。

## 工作流问题

### Q: 工作流执行报 "agentId not found"

A: `workflow-dag.yaml` 中的 `agentId` 是占位值，需替换为实际创建的 Agent ID。

### Q: human 节点暂停后无法 resume

A: 确保使用正确的 instanceId（不是 definitionId）：
```bash
GET /api/v1/workflows/instances?status=PAUSED  # 找到暂停的实例 ID
POST /api/v1/workflows/instances/{instanceId}/resume?decision=approve
```

### Q: Flowable 工作流不执行

A: Lumina 中 Flowable 异步执行器默认关闭。确保：
1. Flowable 依赖存在（standalone 默认包含）
2. 工作流定义中没有依赖定时器/异步任务节点
3. 使用同步 ServiceTask + UserTask + Gateway

## 预算/成本问题

### Q: 成本显示为 0

A: 异步任务（trigger 触发的）可能未正确记录 Token 数。
使用同步执行（`POST /agents/{id}/execute`）可确保 Token 被追踪。
查看 `lumina_agent_task` 表的 `total_tokens` 字段。

### Q: 预算未触发阻止

A: 检查：
1. 预算规则的 `scopeType` 和 `scopeId` 是否正确（AGENT + agentId）
2. `limitAmount` 是否设得太低
3. `periodType` 是否正确（DAILY / MONTHLY）
4. 执行的 Agent 是否在预算规则范围内
