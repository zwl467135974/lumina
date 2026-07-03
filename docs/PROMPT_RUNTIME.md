# Prompt 运行时生效规则

## 生效链路

Agent 执行时由 `AgentServiceImpl` 构建运行配置，并按以下顺序解析 Prompt：

1. 将 `agentType` 转为小写作为 Prompt 名称，例如 `ReAct` -> `react`。
2. 查询当前租户下已发布且激活的 Prompt。
3. 当前租户未命中时，回退查询全局租户 `tenant_id=0` 的激活 Prompt。
4. DB 未命中时，由 `agent-core` 回退到 classpath 内置文件 `prompts/{name}.txt`。

## 前端展示

- Agent 列表展示每个 Agent 当前使用 `DB 激活` 还是 `内置回退`。
- Agent 创建/编辑页会根据 Agent 类型实时预览匹配到的激活 Prompt。
- Agent 详情页展示运行时 Prompt 内容，便于执行前确认。
- Prompt 管理页说明发布激活后的生效规则。

## 当前限制

- Agent 当前不固定绑定 Prompt 版本，运行时总是使用匹配名称的激活版本。
- Workflow Agent 节点通过 `AgentService.executeAgent()` 间接复用同一规则。
- 如需固定版本，应后续增加 Agent 的 `promptName` / `promptVersion` 配置字段。
