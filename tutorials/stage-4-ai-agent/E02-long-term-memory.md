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
// 文件：lumina-agent-core/.../service/ReflectiveMemoryService.java（接口）
// 实现在 lumina-business-agent/.../service/impl/ReflectiveMemoryServiceImpl.java
void extractAndSave(Long userId, Long agentId, String conversationId,
                   String userMessage, String assistantReply);

// 实现逻辑（简化）：
// 1. 拼 prompt，让 LLM 从对话中提取值得记住的关键事实（用户偏好/重要信息）
// 2. LLM 对每条事实打重要度分（importance）
// 3. 存入 lumina_long_term_memory 表（LongTermMemoryDO）
// 注：参数是 conversationId（非 sessionId），与 ConversationService 术语一致
```

### 注入（下次对话时）

```java
// DefaultAgentExecutionEngine.java 的 buildContextMessages（约 486-490 行）
// 先注入长期记忆作 SYSTEM 消息
List<String> longTerm = reflectiveMemoryService.getLongTermMemories(userId, agentId);
//                                              ↑ 返回 List<String>（已格式化的记忆文本），不是 DO
if (longTerm != null && !longTerm.isEmpty()) {
    String memoryText = "你之前了解到的用户信息:\n" + String.join("\n", longTerm);
    messages.add(Msg.of(SYSTEM, memoryText));
}
// 再加载短期记忆（历史对话）
```

> 💡 `getLongTermMemories` 返回 `List<String>` 而非 `List<LongTermMemoryDO>`——Service 层已把 DO 转成可直接拼接的文本，引擎层无需关心数据库结构。

---

## 长期记忆的查询与删除（Service 层 + 安全鉴权）

长期记忆的查询/删除通过 `LongTermMemoryService` 暴露给 Controller，**所有方法强制用户鉴权**：

```java
// 文件：lumina-business-agent/.../service/impl/LongTermMemoryServiceImpl.java
public List<LongTermMemoryVO> list(Long userId, Long agentId, int limit) {
    requireAuthenticated(userId);  // ← userId 为空直接拒绝（防全表删除/越权）
    // ... 按 userId 查询，返回 VO（非 DO）
}

public void delete(Long userId, Long id) {
    requireAuthenticated(userId);
    // ... 校验记忆属主（memory.userId == userId），非本人返回 NOT_FOUND
}

private void requireAuthenticated(Long userId) {
    if (userId == null) {
        throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
    }
}
```

> ⚠️ **v3.10 安全修复**：此前 Controller 直接注入 Mapper 且 `delete`/`deleteAll` 缺 `userId==null` 校验，匿名请求触发 `eq(userId, null)` 会被 MyBatis-Plus 渲染为 `IS NULL`，存在全表删除风险。现已抽 Service 层 + 强制鉴权修复。

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
