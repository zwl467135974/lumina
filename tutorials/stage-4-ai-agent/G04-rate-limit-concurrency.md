# G04 — 限流与并发控制

> **前置要求**：已完成 [G03-预算管控](G03-budget-control.md)
> **预计阅读**：10 分钟
> **难度**：⭐⭐☆☆☆

---

## 这节解决什么问题

除了预算（钱），还要控制**频率**（防刷）和**并发**（防资源占满）。Lumina 支持 Per-Agent 限流和并发控制。

---

## 限流（Rate Limit）

> 📖 详细实现在[第一阶 09-Redis 在 Lumina](../stage-1-foundation/09-redis-in-lumina.md)讲过了。

核心：Redis 原子计数器，每分钟最多 N 次，超限返回 429。

## 并发控制（Max Concurrent）

```java
// 文件：.../security/AgentConcurrencyLimiter.java
// 用信号量控制：同一 Agent 同时执行的请求数不超过 maxConcurrent
public boolean tryAcquire(Long agentId, int maxConcurrent) {
    // Redis 分布式信号量
    // 超过 maxConcurrent → 拒绝
}
```

**为什么需要**：Agent 执行占资源（LLM 调用 + RAG 检索）。如果不限并发，100 个请求同时来可能把系统压垮。

---

## Per-Agent 配置

```sql
-- lumina_agent 表（V43 迁移加的列）
| rate_limit | max_concurrent |
|------------|----------------|
| 30         | 5              |   ← 每分钟最多30次，同时最多5个
```

每个 Agent 可以独立配。

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| 限流 | 每分钟最多 N 次（Redis 计数器） |
| 并发控制 | 同时最多 N 个（信号量） |
| Per-Agent | 每个 Agent 独立配 |
| fail-closed | Redis 挂了拒绝（安全优先） |

---

## 🎉 模块 G 完成

> 🚀 [H01 — 评估框架 →](H01-evaluation-framework.md)

---

📝 **本篇撰写期间修正的代码**：无。
