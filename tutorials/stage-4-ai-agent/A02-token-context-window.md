# A02 — Token 计量与上下文窗口

> **前置要求**：已完成 [A01-LLM 基础](A01-llm-fundamentals.md)
> **预计阅读**：15 分钟
> **难度**：⭐⭐☆☆☆

---

## 这节解决什么问题

上一篇你了解了 Token 和上下文窗口的概念。这节深入：Token 到底怎么算的？上下文窗口超了怎么办？还有哪些关键参数？

---

## Token 精确计量

### 大约换算

| 内容类型 | Token 换算 | 举例 |
|----------|-----------|------|
| 英文单词 | 1 词 ≈ 1-1.5 Token | "hello world" ≈ 2 Token |
| 中文字 | 1 字 ≈ 1-2 Token | "你好世界" ≈ 4-6 Token |
| 代码 | 不固定 | `System.out.println("hi");` ≈ 10 Token |
| JSON | 结构也算 Token | `{"key":"value"}` ≈ 6 Token |

### 实际计算

不同模型的 Tokenizer 不同，精确计算要用对应模型的工具。但**估算**够用了：

```
1000 个中文字 ≈ 1500-2000 Token
一篇 3000 字的文章 ≈ 5000 Token
```

### 在 Lumina 里怎么统计

```java
// 文件：lumina-agent-core/.../model/ExecuteResult.java
@Data
public static class TokenUsage implements Serializable {
    private Integer promptTokens;       // 输入 Token（你发的）
    private Integer completionTokens;   // 输出 Token（AI 回的）
    private Integer totalTokens;        // 总计
}
```

每次调 LLM，AgentScope SDK 返回 `ChatUsage`，Lumina 提取后存入 `ExecuteResult.TokenUsage`：

```java
// 文件：DefaultAgentExecutionEngine.java 的 extractTokenUsage 方法
TokenUsage usage = new TokenUsage();
usage.setPromptTokens(chatUsage.getPromptTokens());
usage.setCompletionTokens(chatUsage.getCompletionTokens());
usage.setTotalTokens(chatUsage.getTotalTokens());
```

这个数据最终持久化到 `lumina_agent_task` 表，用于成本计算。

---

## 上下文窗口管理

### 问题

多轮对话时，每一轮的消息都累加：

```
第 1 轮：用户提问 100 Token + AI 回答 200 Token = 300 Token
第 2 轮：上面 300 + 新提问 100 + 新回答 200 = 600 Token
第 3 轮：上面 600 + 新提问 100 + 新回答 200 = 900 Token
...
第 N 轮：可能超过上下文窗口！
```

### 解决方案：窗口裁剪

当消息总量接近窗口上限时，**丢弃最早的消息**，只保留最近的 N 条：

```java
// 文件：lumina-agent-core/.../manager/MemoryManager.java 的 getRecentMemories
public List<Message> getRecentMemories(String conversationId, int maxCount) {
    // 从 Redis 取最近 maxCount 条消息
    // 丢弃更早的——防止超过上下文窗口
}
```

> 📖 完整的记忆管理见 [AI 模块 E-记忆与对话](README.md)。

---

## 关键参数详解

```java
// 文件：AgentConfig.java 的 LLMConfig
private Double temperature;    // 温度（上一篇讲过）
private Integer maxTokens;     // 最大输出长度
private Boolean stream;        // 是否流式输出
```

### maxTokens（最大输出长度）

```java
maxTokens = 2000    // AI 最多回答 2000 Token（约 1500 字）
```

**注意**：这是**输出**的限制，不是输入。如果设太小，AI 可能说到一半被截断。

### stream（流式输出）

```java
stream = true    // 流式输出（打字机效果）
stream = false   // 等全部生成完再返回
```

> 📖 流式输出详见 [AI 模块 F01-流式输出](F01-streaming-sse.md)。

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| Token 计量 | 中文 1 字 ≈ 1-2 Token，英文 1 词 ≈ 1 Token |
| promptTokens | 你发给 AI 的 Token 数 |
| completionTokens | AI 回复的 Token 数 |
| 上下文窗口 | 一次能处理的最大 Token 数，超了丢最早的消息 |
| maxTokens | AI 单次最多回多少 Token |

> 🚀 [A03 — Prompt 工程 →](A03-prompt-engineering-basics.md)

---

📝 **本篇撰写期间修正的代码**：无。
