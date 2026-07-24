# E06 — 上下文压缩与长对话管理

> **前置要求**：已完成 [E03 多轮上下文](E03-multiturn-context.md) + [E05 跨实例状态共享](E05-agent-state-store.md)
> **预计阅读**：18 分钟
> **难度**：⭐⭐⭐⭐☆

---

## 这节解决什么问题

E03 讲了滑动窗口——对话超过 20 条时，最早的消息直接丢弃。但**丢弃 = 信息丢失**：

```
第 1 轮: 用户 "我叫张三，我是做 Java 的"
第 2 轮: Agent "你好张三！"
...
第 11 轮: 窗口满了，第 1 轮被丢弃 ❌
第 12 轮: 用户 "我之前说过我是做什么的？"
         Agent: "抱歉，我不知道" ← 信息丢了！
```

**上下文压缩**解决这个：不直接丢弃旧消息，而是先用 LLM 把它们**压缩成摘要**，保留关键信息。

---

## 先建立直觉：会议纪要

你参加了一个 3 小时的会议，有人问你 2 小时前讨论了什么。你不会逐字复述——你会给一个**会议纪要**："之前讨论了 A 方案的优缺点，决定采用 B 方案，待办是写技术方案。"

上下文压缩就是让 Agent 自动生成"对话纪要"——把旧对话压缩成要点，腾出 Token 空间给新对话。

---

## 压缩策略：分割 + 摘要 + 保留

```
对话历史（18 条消息，超过阈值 15）
├── [0..12] 旧消息（13 条）──→ LLM 摘要 ──→ "用户讨论了 X、Y，决定了 Z"
│                                                        │
├── [13..17] 最近消息（5 条）──→ 保留原始 ──┘
│                                                        │
▼                                                        ▼
最终上下文：                                              
[SYSTEM: 对话历史摘要: 用户讨论了 X、Y，决定了 Z]  ← 压缩后的摘要
[USER: 最近第 5 条消息]
[ASSISTANT: 最近第 4 条消息]
...
[USER: 当前消息]                                   ← 保留的原始消息
```

---

## 配置

```yaml
# application.yml
lumina:
  agent:
    memory:
      compression:
        enabled: true              # 启用压缩（默认 false）
        threshold: 15              # 超过 15 条消息触发压缩
        recent-keep-count: 5       # 保留最近 5 条不压缩
        summary-max-tokens: 500    # 摘要最大 Token 数
```

| 参数 | 含义 | 推荐值 |
|------|------|-------|
| `threshold` | 触发阈值 | 15（约 15 轮对话后压缩） |
| `recentKeepCount` | 保留条数 | 5（最近 5 条原始消息 + 1 条摘要） |
| `summaryMaxTokens` | 摘要长度 | 500（足够保留关键信息） |

---

## 实现架构

### ContextSummarizer 接口

```java
// 文件：lumina-agent-core/.../service/ContextSummarizer.java
public interface ContextSummarizer {
    /**
     * 对旧消息列表生成摘要
     * @param olderMessages 需要压缩的消息（按时间顺序）
     * @param agentName     Agent 名称
     * @return 摘要文本
     */
    String summarize(List<MemoryManager.Memory> olderMessages, String agentName);
}
```

### ContextSummarizerImpl 实现

```java
// 文件：lumina-business-agent/.../service/impl/ContextSummarizerImpl.java
// 调用 LLM 生成摘要，参考 ReflectiveMemoryServiceImpl 的调用模式

private static final String SUMMARIZER_PROMPT = """
    你是对话摘要助手。将给定的对话历史压缩成简洁的摘要。
    规则：
    - 保留关键信息：用户意图、已做决策、重要数据、未解决的问题
    - 丢弃无关内容：寒暄、重复确认、情绪表达
    - 用第三人称客观描述，不要编造未提及的信息
    """;
```

### 引擎层集成

```java
// 文件：DefaultAgentExecutionEngine.java buildContextMessages()

if (contextSummarizer != null && compressionConfig.isEnabled()
        && history.size() > compressionConfig.getThreshold()) {

    int splitIndex = history.size() - compressionConfig.getRecentKeepCount();
    List<Memory> toCompress = history.subList(0, splitIndex);  // 旧消息
    List<Memory> toKeep = history.subList(splitIndex, history.size()); // 最近消息

    String summary = contextSummarizer.summarize(toCompress, agentName);

    // 摘要作为 SYSTEM 消息注入
    messages.add(Msg.builder().role(MsgRole.SYSTEM)
            .textContent("[对话历史摘要] " + summary)
            .build());

    // 只保留最近的消息
    for (Memory m : toKeep) {
        messages.add(Msg.builder().role(...).textContent(m.content()).build());
    }
}
```

---

## 压缩前后的 Token 对比

假设每条消息平均 100 Token：

| 场景 | 压缩前（直接丢弃） | 压缩后（摘要） |
|------|-------------------|---------------|
| 18 条历史 | 20 条 × 100 = 2000 Token（最早的丢了） | 500（摘要）+ 5 × 100 = 1000 Token |
| 30 条历史 | 20 条 × 100 = 2000 Token（丢了 10 条信息） | 500 + 5 × 100 = 1000 Token |
| 100 条历史 | 20 条 × 100 = 2000 Token（丢了 80 条信息） | 500 + 5 × 100 = 1000 Token |

压缩后 Token 消耗**更低**（1000 vs 2000），且**信息保留更多**（摘要包含关键信息）。

---

## 降级机制

如果 LLM 摘要失败（网络超时、API Key 无效等），不会中断对话：

```java
try {
    String summary = contextSummarizer.summarize(toCompress, agentName);
    // ... 使用摘要
} catch (Exception e) {
    log.warn("上下文压缩失败，降级为全量加载: {}", e.getMessage());
    // 降级：不做压缩，加载全部历史（和之前的行为一致）
}
```

---

## 三种记忆策略对比

| 策略 | 实现 | Token 消耗 | 信息保留 | 适用场景 |
|------|------|-----------|---------|---------|
| 直接丢弃（E03 原始策略） | 滑动窗口，超出的扔掉 | 中 | ❌ 差 | 短对话 |
| 上下文压缩（本节） | LLM 摘要旧消息 | 低 | ✅ 好 | 长对话 |
| AgentStateStore（E05） | Redis 持久化完整状态 | 不影响上下文 | ✅ 完整 | 跨实例部署 |

三者**互补**：AgentStateStore 保证状态不丢（跨实例），压缩控制给 LLM 看多少（省 Token），滑动窗口是压缩关闭时的兜底。

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| 上下文压缩 | 旧消息用 LLM 摘要替代，而非直接丢弃 |
| threshold | 触发压缩的消息条数（默认 15） |
| recentKeepCount | 保留最近 N 条不压缩（默认 5） |
| 降级 | 压缩失败时回退到全量加载 |

### 自测题

1. 为什么不把所有历史都传给 LLM？（提示：Token 限制 + 成本）
2. 压缩后的摘要注入在什么位置？（SYSTEM 消息，和长期记忆同一层）
3. threshold=15 + recentKeepCount=5 时，18 条历史会怎样分割？
4. 压缩失败时为什么不抛异常而是降级？

> 🚀 返回 [AI 专项导读](README.md)

---

📝 **本篇撰写期间修正的代码**：无（上下文压缩能力为本次新增）。
