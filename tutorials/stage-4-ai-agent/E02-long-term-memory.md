# E02 — 长期记忆

> **前置要求**：已完成 [E01-短期记忆](E01-short-term-memory.md)
> **预计阅读**：15 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

短期记忆只在当前会话有效——新开会话就忘了。但有些信息应该**跨会话记住**（用户偏好、关键事实）。这节讲**长期记忆**。

---

## 长期记忆是什么？先建立直觉

### 类比：日记本

便签纸（短期记忆）挂电话就扔。但如果你觉得某条信息很重要，会写进**日记本**——以后随时翻阅。日记本就是长期记忆。

Lumina 的长期记忆叫 **Reflective Memory**——用 LLM 自动提取对话中的关键事实，存入数据库，下次对话时注入。

---

## Reflective Memory 工作流程

### 写入（对话结束后）

```java
// 文件：lumina-agent-core/.../service/ReflectiveMemoryService.java
public void extractAndSave(Long userId, Long agentId, String sessionId,
                           String userMsg, String assistantReply) {
    // 1. 让 LLM 提取关键信息
    String prompt = "从以下对话中提取值得记住的关键事实（用户偏好/重要信息）。\n" +
                    "用户: " + userMsg + "\n" +
                    "AI: " + assistantReply;
    String facts = llm.call(prompt);    // LLM 提取

    // 2. 存入数据库
    LongTermMemoryDO memory = new LongTermMemoryDO();
    memory.setUserId(userId);
    memory.setAgentId(agentId);
    memory.setContent(facts);
    memory.setImportance(calculateImportance(facts));    // 重要度评分
    memoryMapper.insert(memory);
}
```

### 注入（下次对话时）

```java
// DefaultAgentExecutionEngine.java 的 buildContextMessages
// 先注入长期记忆作 SYSTEM 消息
List<LongTermMemoryDO> longTerm = reflectiveMemoryService.getLongTermMemories(userId, agentId);
if (!longTerm.isEmpty()) {
    String memoryText = longTerm.stream()
        .map(LongTermMemoryDO::getContent)
        .collect(Collectors.joining("\n"));
    messages.add(Msg.of(SYSTEM, "你之前了解到的用户信息:\n" + memoryText));
}
// 再加载短期记忆（历史对话）
```

---

## 双轨记忆模型

```
长期记忆（DB，跨会话）          短期记忆（Redis，当前会话）
    │                               │
    │ 用户偏好/关键事实              │ 最近几轮对话
    │                               │
    └─────── 都注入 Prompt ──────────┘
                    │
              LLM 回答时既有"过去的了解"又有"刚说的"
```

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| 长期记忆 | 跨会话的关键事实（Reflective Memory） |
| 提取 | LLM 自动从对话提取值得记住的信息 |
| 注入 | 下次对话时作为 SYSTEM 消息注入 |
| 双轨模型 | 长期（DB）+ 短期（Redis） |

> 🚀 [E03 — 多轮上下文 →](E03-multiturn-context.md)

---

📝 **本篇撰写期间修正的代码**：无。
