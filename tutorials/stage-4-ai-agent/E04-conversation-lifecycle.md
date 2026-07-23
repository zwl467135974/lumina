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

---

## 存储

- **Redis**：短期记忆（会话最近消息，24h 过期）
- **MySQL** `lumina_conversation` 表：会话元数据（标题/创建时间）
- **MySQL** `lumina_message` 表：每条消息的持久化（冷存储）

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| 会话 | 多轮对话的容器，用 UUID 标识 |
| conversationId=null | 单轮无状态 |
| 三层存储 | Redis（热）+ conversation 表（元）+ message 表（冷） |

---

## 🎉 模块 E 完成

> 🚀 [F01 — 流式输出 →](F01-streaming-sse.md)

---

📝 **本篇撰写期间修正的代码**：无。
