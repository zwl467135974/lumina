# B03 — AgentScope SDK 与 Lumina 封装层

> **前置要求**：已完成 [B02-Plan-Execute](B02-plan-execute-pattern.md)
> **预计阅读**：15 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

Lumina 的 Agent 不是从零写的——它底层依赖 **AgentScope Java SDK**。这节讲清 Lumina 和 AgentScope 的关系：AgentScope 是什么，Lumina 在它上面封装了什么。

---

## 两层架构

```
┌─────────────────────────────────────┐
│  Lumina 业务层（lumina-business-agent）│  Agent 管理、会话、知识库挂载、成本
│  AgentController / AgentServiceImpl    │
├─────────────────────────────────────┤
│  Lumina 引擎层（lumina-agent-core）    │  配置加载、Prompt、记忆、流式封装
│  DefaultAgentExecutionEngine           │
├─────────────────────────────────────┤
│  AgentScope Java SDK 1.0.7            │  ReActAgent 核心循环、Model/Toolkit
│  io.agentscope.core.*                 │
├─────────────────────────────────────┤
│  各厂商 LLM SDK                       │  DashScope/OpenAI/Anthropic
└─────────────────────────────────────┘
```

### AgentScope 提供什么

- `ReActAgent` —— ReAct 循环的完整实现
- `Model` —— LLM 调用抽象
- `Toolkit` —— 工具管理
- `Msg` —— 消息封装（Role/Content）
- `StreamOptions` —— 流式输出控制

### Lumina 封装了什么

- **配置管理**：AgentConfig（从 DB 加载，不是硬编码）
- **多模型适配**：ChatModelFactory（统一接口适配 5+ 厂商）
- **记忆管理**：MemoryManager（Redis 热存储 + DB 冷存储）
- **安全防护**：Prompt 注入检测 + PII 脱敏
- **成本追踪**：Token 统计持久化
- **流式封装**：StreamChunk 标准化事件格式

---

## ReActAgent 的 Builder

```java
// Lumina 创建 AgentScope 的 ReActAgent
ReActAgent agent = ReActAgent.builder()
    .name(config.getAgentName())       // Agent 名称
    .model(chatModel)                   // LLM 大脑（ChatModelFactory 创建）
    .toolkit(toolkit)                   // 工具箱（EnhancedToolManager 构建）
    .memory(memory)                     // 记忆（MemoryManager 构建）
    .prompt(systemPrompt)               // System Prompt
    .streamOptions(streamOptions)       // 流式选项
    .build();
```

**AgentScope 的 ReActAgent 帮你实现了循环逻辑**——你只管配参数，不用自己写 Reason→Act→Observe 循环。

---

## Msg 消息封装

AgentScope 用 `Msg` 对象表示消息：

```java
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;

Msg userMsg = Msg.of(MsgRole.USER, "今天天气怎么样？");
Msg assistantMsg = Msg.of(MsgRole.ASSISTANT, "北京今天 28°C 晴。");
```

四种角色：`SYSTEM` / `USER` / `ASSISTANT` / `TOOL`（工具返回）。

---

## 事件流（Events）

AgentScope 执行时发出事件，Lumina 转换成 StreamChunk：

```java
// AgentScope 的 Event 类型 → Lumina 的 StreamChunk
Event.Type.AGENT_REASONING → StreamChunk("REASONING_CHUNK", ...)
Event.Type.AGENT_ACTING    → StreamChunk("ACTING_CHUNK", ...)
Event.Type.AGENT_RESULT    → StreamChunk("FINAL", ...)
```

---

## 小结

| 层 | 职责 | 示例 |
|----|------|------|
| AgentScope SDK | ReActAgent 循环、Model/Toolkit | `ReActAgent.builder().build()` |
| Lumina 引擎层 | 配置/记忆/安全/成本封装 | DefaultAgentExecutionEngine |
| Lumina 业务层 | Agent CRUD/会话/知识库 | AgentController |

> 🚀 [B04 — Agent 配置体系 →](B04-agent-config-system.md)

---

📝 **本篇撰写期间修正的代码**：无。
