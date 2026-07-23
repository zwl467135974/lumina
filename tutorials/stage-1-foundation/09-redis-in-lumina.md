# 09 — Redis 在 Lumina 的实战

> **前置要求**：已完成 [08-Redis 基础](08-redis-basics.md)
> **预计阅读**：25 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

上一节你学了 Redis 和 Redisson 的基本概念。这节看 Lumina 怎么用 Redis 解决两个真实的企业级问题：

1. **分布式锁**——多实例部署时，怎么保证同一任务只被一个实例执行
2. **限流**——怎么防止用户疯狂调用 AI Agent 烧钱

这两个是 Redis 在 Lumina 里最精彩的应用。

---

## 实战一：分布式锁

### 问题场景

Lumina 支持 Cron 触发器——Agent 按定时计划执行。但如果部署了 3 个实例，到点了三个实例同时触发同一个任务，会怎样？

```
08:00:00  实例1：触发 Agent "每日报告"  → 执行
08:00:00  实例2：触发 Agent "每日报告"  → 执行（重复了！）
08:00:00  实例3：触发 Agent "每日报告"  → 执行（又重复了！）
```

用户一天收到 3 份相同的报告。

### 解决方案：Redis 分布式锁

**谁先抢到锁，谁执行。抢不到的跳过。**

```
08:00:00  实例1：tryLock("trigger:1") → 抢到了！执行任务
08:00:00  实例2：tryLock("trigger:1") → 没抢到，跳过
08:00:00  实例3：tryLock("trigger:1") → 没抢到，跳过
```

### 在 Lumina 里长啥样

```java
// 文件：lumina-modules/lumina-business-agent/.../AgentTriggerServiceImpl.java（fireWithLock 方法，简化）
private boolean fireWithLock(AgentTrigger trigger) {
    String lockKey = "lumina:trigger:fire:" + trigger.getId();
    RLock lock = redissonClient.getLock(lockKey);

    try {
        // tryLock：非阻塞获取，只等 0 秒，持锁最多 300 秒
        boolean acquired = lock.tryLock(0, 300, TimeUnit.SECONDS);
        if (!acquired) {
            // 没抢到锁 = 另一个实例正在处理，跳过
            log.info("触发器已被其他实例处理，跳过: triggerId={}", trigger.getId());
            return false;
        }

        // 抢到了，执行触发
        agentTaskService.submitTask(trigger.getAgentId(), buildTaskDTO(trigger));
        return true;

    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
    } finally {
        // 释放锁（先确认这把锁还属于自己的）
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
```

### 四步范式

| 步骤 | 代码 | 作用 |
|------|------|------|
| ① 尝试获取锁 | `lock.tryLock(0, 300, TimeUnit.SECONDS)` | 非阻塞（不等），持锁最多 300 秒 |
| ② 判断是否抢到 | `if (!acquired) return false` | 没抢到就跳过 |
| ③ 执行业务 | `agentTaskService.submitTask(...)` | 抢到了才执行 |
| ④ 释放锁 | `if (lock.isHeldByCurrentThread()) lock.unlock()` | finally 里释放，先检查还持有 |

> ⚠️ **为什么要 `isHeldByCurrentThread()`？** 如果执行时间超过 300 秒（leaseTime），锁会自动释放。此时另一个实例可能拿到了锁。如果直接 unlock 会误删别人的锁。检查"这把锁还是我的吗"是安全做法。
>
> 📖 分布式锁的更深层原理（RedLock、看门狗机制）详见[第三阶 11-分布式锁](../stage-3-mastery/11-distributed-lock-theory.md)。

---

## 实战二：限流

### 问题场景

AI Agent 每次执行都要调 LLM API，按 Token 收费。如果不限制，一个用户疯狂调用，你的 API Key 账单会爆炸。

### 解决方案：固定窗口限流

规则：每个用户对每个 Agent，**每分钟最多调用 N 次**（默认 30 次，可 Per-Agent 配置）。

### 在 Lumina 里长啥样

```java
// 文件：lumina-modules/lumina-business-agent/.../security/AgentRateLimiter.java
@Component
@RequiredArgsConstructor
public class AgentRateLimiter {

    private final RedisCacheManager redisCacheManager;

    @Value("${lumina.agent.rate-limit.max-requests:30}")    // 默认每分钟 30 次
    private int maxRequests;

    @Value("${lumina.agent.rate-limit.window-seconds:60}")  // 时间窗口 60 秒
    private int windowSeconds;

    public void checkRateLimit(Long agentId, Integer perAgentLimit) {
        int effectiveMax = (perAgentLimit != null && perAgentLimit > 0) ? perAgentLimit : maxRequests;

        Long userId = BaseContext.getUserId();
        String key = "agent:rate:" + agentId + ":" + userId;

        try {
            // 原子递增 + 第一次设过期时间
            long count = redisCacheManager.incrementAndGetWithExpire(
                key, Duration.ofSeconds(windowSeconds));

            if (count > effectiveMax) {
                // 超限了
                throw new BusinessException(ErrorCode.AGENT_RATE_LIMITED);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // Redis 挂了怎么办？
            if (failOpen) {
                // fail-open：放行（可用性优先）
                log.warn("限流检查失败，fail-open 放行");
            } else {
                // fail-closed：拒绝（安全优先，默认）
                throw new BusinessException(ErrorCode.AGENT_RATE_LIMITED, "限流服务不可用");
            }
        }
    }
}
```

### 原理图解

```
用户第 1 次调用（窗口内 count = 0 → 1）
  incrementAndGetWithExpire("agent:rate:1:user1", 60s)
  → Redis: agent:rate:1:user1 = 1，设 TTL=60s
  → 1 <= 30，放行 ✓

用户第 2-30 次调用（count = 2-30）
  incrementAndGet → Redis 自增
  → 都 ≤ 30，放行 ✓

用户第 31 次调用（count = 31）
  incrementAndGet → Redis 自增到 31
  → 31 > 30，抛 AGENT_RATE_LIMITED ✗

60 秒后...
  → Key 自动过期消失
  → 下次调用 count 重新从 1 开始
```

### fail-open vs fail-closed

`AgentRateLimiter` 有一个精妙的设计——**Redis 挂了怎么办？**

| 策略 | 行为 | 适用场景 | Lumina 默认 |
|------|------|----------|-------------|
| **fail-open** | Redis 挂了就放行 | 可用性优先（宁可多花钱不能中断） | 否 |
| **fail-closed** | Redis 挂了就拒绝 | 安全优先（宁可中断不能烧钱） | **是** |

Lumina 默认 fail-closed（拒绝），因为 AI 调用涉及真金白银，宁可暂时不可用也不能失控。

> 💡 这叫**防御性编程**——考虑"依赖挂了"的极端情况该怎么处理。面试加分点。

---

## 实战三：incrementAndGetWithExpire 的故事

看这行注释：

```java
// 使用 incrementAndGetWithExpire 保证 incrementAndGet 与 expire 作用于同一个 RAtomicLong 对象，
// 避免 Redisson 下 RAtomicLong/RBucket 类型不匹配导致 TTL 不生效
```

这是 Lumina 踩过的真实坑。最初想这样实现：
```java
RAtomicLong counter = redissonClient.getAtomicLong(key);   // 拿到 RAtomicLong
counter.incrementAndGet();                                   // 原子 +1
redissonClient.getBucket(key).expire(60, SECONDS);          // ❌ 用 RBucket 设过期 → 类型不匹配报错！
```

问题：`getAtomicLong` 返回 `RAtomicLong` 类型，`getBucket` 返回 `RBucket` 类型——它们是不同的对象，不能互相操作。

**修复**：在 `RedisCacheManager` 里新增了 `incrementAndGetWithExpire` 方法，保证用**同一个 `RAtomicLong` 对象**做自增和设过期：

```java
// RedisCacheManager 的修复方法
public long incrementAndGetWithExpire(String key, Duration ttl) {
    RAtomicLong counter = redissonClient.getAtomicLong(key);
    long value = counter.incrementAndGet();
    if (value == 1L) {
        counter.expire(ttl);    // ← 同一个对象设过期，类型匹配
    }
    return value;
}
```

> 📝 **这是"教学即代码审查"的典型例子**——这个 bug 的修复过程本身就是绝佳的教学素材，展示了为什么不能混用不同类型的 Redisson 对象。

---

## Redis 在 Lumina 里的完整用途清单

| 用途 | 实现类 | 数据结构 |
|------|--------|----------|
| 用户权限缓存 | `RedisCacheManager` | RBucket |
| Token 黑名单 | `RedisCacheManager` | RBucket（按 JWT 过期时间 TTL） |
| Agent 执行限流 | `AgentRateLimiter` | RAtomicLong |
| Cron 触发器分布式锁 | `AgentTriggerServiceImpl` | RLock |
| 会话短期记忆 | `MemoryManager` | RList |
| 通知 SSE 广播 | `NotificationSseRegistry` | RTopic |
| 预算告警去重 | `BudgetServiceImpl` | RBucket |

---

## 动手试试

1. **打开 `AgentRateLimiter.java`**：找到 `failOpen` 配置项和 `fail-open/fail-closed` 逻辑
2. **找到 `fireWithLock` 方法**（AgentTriggerServiceImpl）：数数分布式锁的四步范式
3. **思考**：如果不设 `leaseTime`（锁的自动过期时间），实例崩溃了锁会怎样？

---

## 小结

| 实战 | 一句话记忆 |
|------|-----------|
| 分布式锁 | `tryLock(0, leaseTime)` + `isHeldByCurrentThread` + finally unlock |
| 限流 | `incrementAndGetWithExpire` + 固定窗口计数 + 超限抛异常 |
| fail-open/fail-closed | Redis 挂了时，放行（可用性优先）或拒绝（安全优先） |
| 原子操作 | 同一个 Redisson 对象上做多个操作，避免类型不匹配 |

---

## 下一步

后端基础技术栈还剩最后一个：[Flyway 数据库迁移](10-flyway-basics.md)——数据库表结构怎么版本化管理。

> 🚀 **现在继续**：[10 — Flyway 迁移 →](10-flyway-basics.md)

---

## 自测题

1. **为什么 `fireWithLock` 用 `tryLock(0, ...)` 而不是 `lock()`？**
   <details><summary>答案</summary>tryLock(0, ...) 是非阻塞的——抢不到立刻返回 false 跳过；lock() 会阻塞等待，三个实例会排队执行，达不到"只执行一次"的效果。</details>

2. **限流的固定窗口算法有什么局限？**
   <details><summary>答案</summary>窗口边界问题——如果用户在 59 秒时调 30 次，1 秒后新窗口开始又调 30 次，1 秒内实际 60 次。滑动窗口算法可以缓解。Lumina 用的是简单固定窗口，够用。</details>

3. **`isHeldByCurrentThread()` 检查是防什么？**
   <details><summary>答案</summary>如果持锁时间超过 leaseTime，锁自动释放后可能被别人拿走。直接 unlock 会误删别人的锁，所以先检查"这把锁还属于我吗"。</details>

4. **fail-closed 策略下，Redis 挂了用户会看到什么？**
   <details><summary>答案</summary>调用 Agent 时收到"限流服务暂时不可用，请稍后重试"错误。虽然影响可用性，但防止了 Redis 挂掉时限流失效导致的 API 费用失控。</details>

---

📝 **本篇撰写期间修正的代码**：无。本篇引用的 `incrementAndGetWithExpire` 方法及其注释（"RAtomicLong/RBucket 类型不匹配"）已经是之前修复后的正确版本，注释清晰记录了坑点和原因，是好的教学素材。
