# G04 — 限流与并发控制：防刷防压垮

> **前置要求**：已完成 [G03 预算管控](G03-budget-control.md)
> **预计阅读**：18 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

除了预算（钱），还要控制**频率**（防恶意刷）和**并发**（防资源占满）。Lumina 用两种不同机制解决这两个问题：

| 问题 | 机制 | 实现层 | 数据结构 |
|------|------|--------|---------|
| 频率太快（1 秒调 100 次） | **限流**（Rate Limit） | Redis 分布式 | 原子计数器 |
| 同时太多（100 个并发执行） | **并发控制**（Concurrency） | JVM 本地 | Semaphore 信号量 |

---

## 先建立直觉：地铁早高峰

**限流** = **闸机**：每分钟只放 30 个人进站（不管里面多少人）
**并发控制** = **车厢容量**：同时最多 5 个人在车厢里（第 6 个等有人出来）

两种限制互补：闸机管"进站速度"，车厢管"同时在里面的人数"。

---

## 限流（Rate Limit）：Redis 原子计数器

### 原理

```
窗口（60 秒）
├── 第 1 次请求 → count=1（放行）
├── 第 2 次请求 → count=2（放行）
├── ...
├── 第 30 次请求 → count=30（放行，达上限）
├── 第 31 次请求 → count=31 > 30（拒绝 429）
├── ...
└── 60 秒后 → count 过期归零，重新计数
```

### 实现

```java
// 文件：lumina-business-agent/.../security/AgentRateLimiter.java
public void checkRateLimit(Long agentId, Integer perAgentLimit) {
    int limit = perAgentLimit != null ? perAgentLimit : globalLimit;

    String key = "agent:rate:" + agentId + ":" + tenantId;
    // Redisson 原子递增 + 过期时间设置（原子操作，防竞态）
    long count = redisCacheManager.incrementAndGetWithExpire(key, Duration.ofSeconds(60));

    if (count > limit) {
        throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED,
                "请求过于频繁，请稍后重试");
    }
}
```

**关键设计**：`incrementAndGetWithExpire` 保证递增和设过期是原子操作——避免"先递增再设过期"的竞态条件（如果递增后服务挂了，key 没有过期时间就永远不归零）。

### Fail-Open / Fail-Closed 策略

Redis 挂了怎么办？两种策略：

```java
// 文件：AgentRateLimiter.java
@Value("${lumina.agent.rate-limit.fail-open:false}")
private boolean failOpen;

// Redis 不可用时：
if (failOpen) {
    // 放行（不影响用户体验，但限流失效）
    log.warn("频率限制检查失败（Redis 不可用），fail-open 模式放行");
} else {
    // 拒绝（安全优先，避免限流被绕过）
    log.error("fail-closed 模式拒绝请求");
    throw new BusinessException(ErrorCode.RATE_LIMIT_CHECK_FAILED);
}
```

| 策略 | Redis 挂了时 | 适合场景 |
|------|------------|---------|
| **fail-closed**（默认） | 拒绝所有请求 | 安全优先（防 DDoS） |
| **fail-open** | 放行所有请求 | 可用性优先（宁可被刷也不能停服） |

> **经验法则**：对外 API 用 fail-closed（安全），内部服务用 fail-open（可用性）。

---

## 并发控制：JVM 信号量

### 原理

```java
// 文件：lumina-business-agent/.../security/AgentConcurrencyLimiter.java
// ⚠️ 注意：是 JVM 内 Semaphore，不是 Redis 分布式信号量！

@Component
public class AgentConcurrencyLimiter {

    // 每个 Agent 一个信号量（按 agentId 缓存）
    private final ConcurrentHashMap<Long, ConcurrencySlot> slots = new ConcurrentHashMap<>();

    private record ConcurrencySlot(int maxConcurrent, Semaphore semaphore) {}

    public boolean acquire(Long agentId, Integer maxConcurrent) {
        if (maxConcurrent == null || maxConcurrent <= 0) return false;

        // 获取或创建信号量（fair=true 公平模式，先到先得）
        ConcurrencySlot slot = slots.compute(agentId, (id, existing) -> {
            if (existing == null) {
                return new ConcurrencySlot(maxConcurrent, new Semaphore(maxConcurrent, true));
            }
            return existing;
        });

        // 非阻塞获取（拿不到立即返回 false）
        if (!slot.semaphore().tryAcquire()) {
            throw new BusinessException(ErrorCode.AGENT_CONCURRENT_LIMITED);
        }
        return true;  // 调用方需要在 finally 中 release
    }

    public void release(Long agentId) {
        ConcurrencySlot slot = slots.get(agentId);
        if (slot != null) slot.semaphore().release();
    }
}
```

### 为什么用 JVM Semaphore 而不是 Redis

| 维度 | JVM Semaphore | Redis 分布式锁 |
|------|--------------|----------------|
| 性能 | 纳秒级（内存操作） | 毫秒级（网络往返） |
| 多实例 | ❌ 只限本实例 | ✅ 全局限 |
| 复杂度 | 简单（标准库） | 复杂（锁续期/死锁处理） |
| 适用 | 单实例够用 | 多实例必须 |

Lumina 选择 JVM Semaphore 是因为：
1. Agent 执行的主要瓶颈是 **LLM API 调用**（秒级），不是并发控制本身（纳秒级 vs 毫秒级差异可忽略）
2. 单实例的 maxConcurrent=5 足以保护后端——即使多实例，每个实例 5 个并发也不会压垮 LLM
3. 如果需要严格全局并发控制，可以改为 Redisson `RSemaphore`

### 使用方式

```java
// AgentServiceImpl 中
boolean acquired = false;
try {
    acquired = concurrencyLimiter.acquire(agentId, agent.getMaxConcurrent());
    // ... 执行 Agent ...
} finally {
    if (acquired) {
        concurrencyLimiter.release(agentId);  // 必须在 finally 中释放
    }
}
```

---

## Per-Agent 配置

```sql
-- lumina_agent 表（V43 迁移添加的列）
ALTER TABLE lumina_agent
    ADD COLUMN rate_limit INT DEFAULT 0 COMMENT '每分钟最大请求数（0=用全局默认）',
    ADD COLUMN max_concurrent INT DEFAULT 0 COMMENT '最大并发数（0=不限制）';
```

每个 Agent 可以独立配：

| Agent | rate_limit | max_concurrent | 效果 |
|-------|-----------|----------------|------|
| 轻量问答 | 60 | 10 | 高频高并发 |
| 重量分析 | 10 | 2 | 低频低并发（省 Token） |
| Cron 触发 | 5 | 1 | 只允许串行执行 |

---

## 三层成本/资源控制全景

Lumina 有三层控制，各管一摊：

```
请求进入
    │
    ▼ ① 限流（RateLimiter）：每分钟最多 N 次 → 防 DDoS
    │
    ▼ ② 并发（ConcurrencyLimiter）：同时最多 N 个 → 防资源占满
    │
    ▼ ③ 预算（BudgetService）：累计花费不超过 N 元 → 防烧钱
    │
    ▼ Agent 执行
```

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| 限流 | Redis 原子计数器，每分钟最多 N 次 |
| 并发控制 | JVM Semaphore 信号量，同时最多 N 个 |
| fail-closed | Redis 挂了拒绝请求（安全优先） |
| fail-open | Redis 挂了放行请求（可用性优先） |
| Per-Agent | rate_limit + max_concurrent 每个 Agent 独立配 |
| 三层控制 | 限流 → 并发 → 预算 |

### 自测题

1. 限流和并发控制有什么区别？
   <details><summary>答案</summary>限流管"频率"（每分钟几次），用 Redis 计数器；并发管"同时在执行的数量"，用 JVM Semaphore。</details>

2. 为什么并发控制用 JVM Semaphore 而不是 Redis？
   <details><summary>答案</summary>JVM Semaphore 纳秒级无网络开销；单实例的并发限制足以保护 LLM（瓶颈在 LLM 不在控制层）。如需全局严格限制可改 RSemaphore。</details>

3. fail-closed 和 fail-open 各适合什么场景？
   <details><summary>答案</summary>fail-closed（默认）适合安全优先（防 DDoS 绕过）；fail-open 适合可用性优先（宁可被刷也不能停服）。</details>

4. `incrementAndGetWithExpire` 为什么不能拆成两步？
   <details><summary>答案</summary>先 increment 再 expire 有竞态：如果 increment 后服务挂了，key 没有过期时间就永远不归零。原子操作避免这个问题。</details>

---

## 🎉 模块 G 完成

> 🚀 [H01 — 评估框架 →](H01-evaluation-framework.md)

---

📝 **本篇撰写期间修正的代码**：无（修正了教学中的事实错误：并发控制是 JVM Semaphore 而非 Redis 信号量）。
