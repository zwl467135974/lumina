# E04 — 会话生命周期

> **前置要求**：已完成 [E03-多轮上下文](E03-multiturn-context.md)
> **预计阅读**：10 分钟
> **难度**：⭐⭐☆☆☆

---

## 这节解决什么问题

"会话"（conversation）是多轮对话的容器。怎么创建？怎么续聊？怎么删？

---

## 会话生命周期

```
创建会话（生成 UUID）
    ↓
第 1 轮对话（传 conversationId → 启用多轮上下文）
    ↓
第 2 轮对话（同一个 conversationId → 继续聊）
    ↓
...
    ↓
删除会话（清理记忆 + DB 记录）
```

---

## conversationId 的作用

```java
// AgentService.executeAgent(agentId, task, conversationId)
// conversationId = null → 单轮无状态（不记上下文）
// conversationId = "uuid-xxx" → 多轮对话（加载历史记忆）
```

前端对话时，第一次生成一个 UUID，后续每轮都传同一个——Agent 就能"记住"前面说过什么。

### conversationId 的跨线程传播

conversationId 通过 `BaseContext` 的 ThreadLocal 在整个执行链中传递：

```java
// 引擎入口设置
BaseContext.setConversationId(conversationId);

// 任何地方都能读取（Agent 工具、记忆管理、Trace 等）
String cid = BaseContext.getConversationId();

// 执行结束清理（finally 块）
BaseContext.clearConversationId();
```

在 Reactor 异步流式路径中，conversationId 还通过 `ConversationIdThreadLocalAccessor` 注册到 Spring 的 `ContextPropagation`，确保跨调度器线程可见。

---

## 存储

会话生命周期涉及 5 个存储位置：

| 存储 | 用途 | TTL/保留期 |
|------|------|-----------|
| Redis `lumina:agent:memory:{cid}` | 短期记忆（MemoryManager 管理的滑动窗口） | 24h |
| Redis `lumina:agent:state:{uid}:{cid}:agent_state` | Agent 完整状态（AgentStateStore） | 7 天 |
| MySQL `lumina_conversation` 表 | 会话元数据（标题/消息数/时间） | 永久 |
| MySQL `lumina_message` 表 | 每条消息持久化（冷存储） | 永久 |
| MySQL `lumina_agent_trace` 表 | 推理链记录（每次执行的步骤/Token/耗时） | 30 天自动清理 |

> AgentStateStore 是 AgentScope 2.0 新增的跨实例状态共享机制，详见 [E05 — 跨实例状态共享](E05-agent-state-store.md)。推理链记录详见 [K01 — 推理链可观测性](K01-trace-observability.md)。

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| 会话 | 多轮对话的容器，用 UUID 标识 |
| conversationId=null | 单轮无状态 |
| 五层存储 | Redis 短期 + Redis AgentState + conversation 表 + message 表 + trace 表 |
| 跨线程传播 | BaseContext ThreadLocal + Reactor ContextPropagation |

---

## 🎉 模块 E 即将完成

> 🚀 [E05 — 跨实例状态共享 →](E05-agent-state-store.md)

---

📝 **本篇撰写期间修正的代码**：无。
