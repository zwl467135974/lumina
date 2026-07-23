# B05 — Provider Failover 与 LLM 容错

> **前置要求**：已完成 [B04-Agent 配置](B04-agent-config-system.md)
> **预计阅读**：20 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

LLM API 不是 100% 可靠——会超时、会限流、服务会挂。如果调一次失败就直接报错给用户，体验很差。

Lumina 用 **Resilience4j** 实现容错：**重试 + 熔断**，让 LLM 调用更健壮。这节讲清这两层保护怎么工作。

---

## 两个问题

### 问题 1：偶发失败 → 重试

LLM API 偶尔超时或网络抖动。重试 2-3 次大概率能成功。

### 问题 2：持续故障 → 熔断

如果 LLM 服务**持续**挂了，每次请求都重试 3 次然后失败——浪费资源、拖慢响应。熔断器在"错误率太高"时**直接拒绝**，不再尝试。

---

## 重试（Retry）

```java
// 文件：lumina-agent-core/.../resilience/LlmResilienceWrapper.java:53-58
this.retry = Retry.of("llm-retry", RetryConfig.custom()
    .maxAttempts(3)                                    // 最多重试 3 次
    .waitDuration(Duration.ofMillis(500))              // 每次等 500ms
    .retryOnException(this::isRetryable)               // 只重试可恢复的异常
    .build());
```

### 哪些异常值得重试

```java
private boolean isRetryable(Throwable e) {
    // 超时、限流（429）、网络错误 → 重试
    // 参数错误（400）、认证失败（401） → 不重试（重试也没用）
}
```

> 💡 **聪明重试**：不是所有错误都重试。参数错误/认证失败是"你自己的问题"，重试 100 次还是错。只有网络/超时/限流这种"偶发可恢复"的才重试。

---

## 熔断器（Circuit Breaker）

### 三状态机

```
    Closed（正常）──错误率>50%──► Open（熔断，拒绝所有请求）
       ▲                              │
       │                              │ 等 10 秒
       │                              ▼
       └──成功率恢复── Half-Open（半开，放行少量请求试探）
```

### Lumina 的配置

```java
// 文件：LlmResilienceWrapper.java:60-68
this.circuitBreaker = CircuitBreaker.of("llm-circuit-breaker", CircuitBreakerConfig.custom()
    .failureRateThreshold(50.0f)               // 错误率 > 50% 触发熔断
    .slowCallRateThreshold(80.0f)              // 慢调用占比 > 80% 也算失败
    .slowCallDurationThreshold(Duration.ofSeconds(30))  // 超过 30s 算慢
    .waitDurationInOpenState(Duration.ofSeconds(10))    // 熔断 10 秒后试半开
    .slidingWindowSize(10)                     // 滑动窗口 10 次调用
    .minimumNumberOfCalls(5)                   // 至少 5 次才统计
    .permittedNumberOfCallsInHalfOpenState(3)  // 半开放行 3 个试探
    .build());
```

---

## 洋葱式装饰器（重试套熔断）

```java
// 文件：LlmResilienceWrapper.java:95-98
public <T> T execute(String callName, Supplier<T> operation) {
    Supplier<T> retryable = Retry.decorateSupplier(retry, operation);        // 先包重试
    Supplier<T> resilient = CircuitBreaker.decorateSupplier(circuitBreaker, retryable); // 再包熔断
    return resilient.get();
}
```

**执行顺序**（从外到内）：

```
请求进来
  ↓
熔断器检查（Closed 放行 / Open 拒绝）
  ↓
执行重试逻辑
  ↓ 尝试 1：失败
  ↓ 等 500ms
  ↓ 尝试 2：失败
  ↓ 等 500ms
  ↓ 尝试 3：成功 ← 返回
  ↓
熔断器记录（这次调用成功/失败）
```

---

## Provider 优先级（多供应商降级）

除了重试+熔断，Lumina 还支持**多 LLM 供应商配置**，按优先级排序：

```java
// LlmProviderServiceImpl.java
// 按 priority 升序排列（数字越小优先级越高）
wrapper.orderByAsc(LlmProviderDO::getPriority);
```

```
Provider 1: 智谱 GLM (priority=1)    ← 主力
Provider 2: 阿里 DashScope (priority=2) ← 备用
Provider 3: 本地 Ollama (priority=3)  ← 兜底
```

主挂了用备用，备用也挂了用兜底。**三层防线**。

> 📖 熔断器的底层原理（状态机详解）见[第三阶 13-容错模式](../stage-3-mastery/13-resilience-pattern.md)。

---

## 小结

| 层 | 作用 | 配置 |
|----|------|------|
| 重试 | 偶发失败自动重试 | 3 次，间隔 500ms |
| 熔断 | 持续故障快速失败 | 错误率 > 50% 熔断 10s |
| Provider 优先级 | 多供应商降级 | priority 升序排列 |
| 洋葱装饰器 | 重试套熔断 | `Retry.decorateSupplier` → `CircuitBreaker.decorateSupplier` |

---

## 🎉 模块 B 完成

你已经学完了 Agent 核心（B01-B05），现在你懂得：
- ReAct 循环怎么转
- Plan-Execute 三阶段架构
- AgentScope SDK 和 Lumina 封装层的关系
- AgentConfig 配置体系
- 容错（重试+熔断+多 Provider）

---

## 下一步

进入 [模块 C：工具系统](README.md)——Function Calling、@AgentTool 注解、MCP 协议。

> 🚀 [C01 — 工具调用原理 →](C01-tool-calling-principle.md)

---

## 自测题

1. **重试为什么不重试"参数错误"（400）？**
   <details><summary>答案</summary>参数错误是"你自己的问题"，重试 100 次还是错。只有网络/超时/限流这种"偶发可恢复"的错误才值得重试。</details>

2. **熔断器的三个状态是什么？怎么转换？**
   <details><summary>答案</summary>Closed（正常）→错误率>50%→ Open（熔断拒绝）→等10秒→ Half-Open（半开试探）→成功→ Closed / 失败→ Open。</details>

---

📝 **本篇撰写期间修正的代码**：无。
