# 13 — 容错模式（熔断/限流/降级）

> **前置要求**：已完成 [12-JWT 安全](12-jwt-security-deep.md)
> **预计阅读**：20 分钟
> **难度**：⭐⭐⭐⭐☆

---

## 面试题引入

> **"面试官：熔断器的三个状态是什么？为什么需要熔断？限流算法有哪些？"**

---

## 深层原理

### 熔断器三状态机

```
Closed（正常）
  │ 错误率 > 50%
  ▼
Open（熔断，快速失败，不调下游）
  │ 等 10 秒
  ▼
Half-Open（半开，放 3 个试探请求）
  │
  ├── 成功 → Closed（恢复）
  └── 失败 → Open（继续熔断）
```

### 为什么需要熔断

**没有熔断**：下游服务挂了→每次请求都等超时（30 秒）→线程池耗尽→本服务也挂了→**级联故障**。

**有熔断**：下游挂了→熔断器打开→请求立刻失败（0 秒）→保护本服务不被拖垮。

---

## Lumina 的 Resilience4j

```java
// 文件：LlmResilienceWrapper.java:60-68
CircuitBreaker.of("llm-circuit-breaker", CircuitBreakerConfig.custom()
    .failureRateThreshold(50.0f)               // 错误率 50% 触发
    .slowCallRateThreshold(80.0f)              // 慢调用 80% 也算
    .slowCallDurationThreshold(Duration.ofSeconds(30))
    .waitDurationInOpenState(Duration.ofSeconds(10))   // 熔断 10 秒
    .slidingWindowSize(10)                      // 滑动窗口 10 次
    .minimumNumberOfCalls(5)                    // 至少 5 次才统计
    .permittedNumberOfCallsInHalfOpenState(3)   // 半开放 3 个试探
    .build());
```

---

## 限流算法

| 算法 | 原理 | 优缺点 |
|------|------|--------|
| **固定窗口** | 每分钟计数 | 简单但有窗口边界问题 |
| **滑动窗口** | 时间段内计数 | 更平滑 |
| **令牌桶** | 固定速率发令牌 | 允许突发 |
| **漏桶** | 固定速率漏出 | 严格匀速 |

### Lumina 的限流

```java
// AgentRateLimiter.java —— 固定窗口（RAtomicLong 计数器）
// 简单够用，窗口边界问题可接受
```

---

## 重试 + 熔断的洋葱装饰

```java
// LlmResilienceWrapper.java:95-98
Supplier<T> retryable = Retry.decorateSupplier(retry, operation);
Supplier<T> resilient = CircuitBreaker.decorateSupplier(circuitBreaker, retryable);
```

```
请求 → 熔断器检查 → 重试（最多3次）→ 执行
       (外层)         (中层)          (内层)
```

---

## 常见追问

### Q：熔断和降级有什么区别？

**A**：熔断是"自动切断"（下游挂了我就不调了）。降级是"返回备选方案"（下游挂了我返回缓存/默认值）。Lumina 的 `fail-open/fail-closed` 是降级策略。

### Q：滑动窗口和固定窗口的区别？

**A**：固定窗口在窗口边界有问题（59秒30次+1秒30次=实际60次）。滑动窗口更平滑（任意时间窗口内计数）。

---

## 小结

| 知识点 | 一句话 |
|--------|--------|
| 熔断三态 | Closed→Open→Half-Open |
| 熔断目的 | 防级联故障 |
| 限流算法 | 固定窗口/滑动窗口/令牌桶/漏桶 |
| 重试套熔断 | 洋葱式装饰器 |

---

📝 **本篇撰写期间修正的代码**：无。
