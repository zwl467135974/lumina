# E03 — 多轮对话与上下文管理

> **前置要求**：已完成 [E02-长期记忆](E02-long-term-memory.md)
> **预计阅读**：10 分钟
> **难度**：⭐⭐☆☆☆

---

## 这节解决什么问题

多轮对话时消息越积越多——第 1 轮 300 Token，第 10 轮可能 3000 Token……迟早超过上下文窗口。怎么管理？

---

## 上下文窗口裁剪

### 策略：只保留最近 N 条

```java
// MemoryManager.getRecentMemories(conversationId, maxCount)
// maxCount 由配置控制，默认 10 条
```

```
第 1 轮: [Msg1, Msg2]                      → 2 条
第 2 轮: [Msg1, Msg2, Msg3, Msg4]          → 4 条
...
第 6 轮: [Msg1...Msg12]                     → 12 条
第 7 轮: [Msg3...Msg14]（丢掉最早的 Msg1,Msg2）→ 仍 12 条
```

**就像一个滑动窗口**——新消息进来，最早的消息被挤出去。

---

## 上下文注入顺序

```java
// DefaultAgentExecutionEngine.buildContextMessages()
1. System Prompt（Agent 人设 + 工具说明）
2. 长期记忆（"你之前了解到的用户信息: ..."）
3. 历史对话（最近 N 轮）
4. 当前用户消息
```

> ⚠️ **顺序很重要**——System Prompt 必须在最前面（LLM 靠它定人设），当前消息在最后（LLM 最关注最近的）。

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| 窗口裁剪 | 只保留最近 N 条，最早的丢弃 |
| 注入顺序 | System → 长期记忆 → 历史对话 → 当前消息 |

> 🚀 [E04 — 会话生命周期 →](E04-conversation-lifecycle.md)

---

📝 **本篇撰写期间修正的代码**：无。
