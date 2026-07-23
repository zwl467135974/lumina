# 11 — 分布式锁原理

> **前置要求**：已完成 [10-缓存模式](10-cache-pattern.md)
> **预计阅读**：25 分钟
> **难度**：⭐⭐⭐⭐⭐

---

## 面试题引入

> **"面试官：Redis 分布式锁怎么实现？SETNX 有什么问题？RedLock 算法是什么？Redisson 的看门狗机制是怎么回事？"**

---

## 表层回答（60 分）

Redis 分布式锁用 `SET key value NX PX timeout`。SETNX 的问题是锁过期但业务没完成。Redisson 用看门狗自动续期。

---

## 深层原理（90 分）

### 演进路线

```
SETNX → SET NX PX → UUID 防误删 → Redisson 看门狗 → RedLock
```

### 第 1 代：SETNX

```bash
SETNX lock:1 "locked"    # Set if Not eXists
# 成功=拿到锁
```

**问题**：如果拿到锁后进程崩溃，锁永远不释放→死锁。

### 第 2 代：SET NX PX（过期时间）

```bash
SET lock:1 "locked" NX PX 30000    # 30 秒自动过期
```

**问题**：业务执行超过 30 秒→锁自动释放→别人拿到锁→你执行完删锁→**删了别人的锁**！

### 第 3 代：UUID 防误删

```bash
SET lock:1 "uuid-abc" NX PX 30000

# 删锁前先检查 UUID
if redis.get("lock:1") == "uuid-abc":
    redis.del("lock:1")
```

**问题**：get 和 del 不是原子操作——get 检查完、del 执行前，锁可能过期被别人拿了。

### 第 4 代：Lua 脚本原子化

```lua
-- 原子操作：检查 UUID + 删除
if redis.call("get", KEYS[1]) == ARGV[1] then
    return redis.call("del", KEYS[1])
else
    return 0
end
```

**问题**：锁过期时间难定——太短业务没完，太长崩溃后等太久。

### 第 5 代：Redisson 看门狗

```java
// Redisson 的 RLock
RLock lock = redissonClient.getLock("lock:1");
lock.lock();    // 默认 30 秒过期，但看门狗每 10 秒续期一次
```

**看门狗机制**：
```
拿到锁（30 秒 TTL）
  ↓ 每 10 秒
看门狗检查：持锁线程还活着吗？
  ├── 活着 → 续期到 30 秒
  └── 死了 → 不续期，30 秒后自动释放
```

**效果**：业务没完→无限续期；业务崩了→自动释放。不用预估执行时间。

---

## Lumina 的使用

```java
// 文件：AgentTriggerServiceImpl.java
RLock lock = redissonClient.getLock("lumina:trigger:fire:" + triggerId);
boolean acquired = lock.tryLock(0, 300, TimeUnit.SECONDS);
//                        ↑  不等  ↑ 持锁最多 300 秒
if (!acquired) return false;    // 没抢到=另一实例在处理

try {
    // 执行业务
} finally {
    if (lock.isHeldByCurrentThread()) {    // ← 检查还是我的锁
        lock.unlock();
    }
}
```

### 为什么 tryLock(0, ...)

`waitTime=0`：非阻塞——抢不到立刻返回（不等）。Cron 触发器不需要等——另一个实例在处理就跳过。

### 为什么 isHeldByCurrentThread

如果持锁超过 300 秒（leaseTime），锁自动释放，别人可能拿到。直接 unlock 会误删。检查"还是我的锁"是安全做法。

---

## RedLock 算法（了解）

### 问题

单个 Redis 实例挂了→锁全部失效。RedLock 在**多个 Redis 节点**上加锁：

```
向 5 个独立 Redis 实例请求加锁
超过半数（3 个）成功 → 加锁成功
```

### 争议

Martin Kleppmann（DDIA 作者）批评 RedLock 有时钟漂移问题。Redis 作者 antirez 反驳。**实践中**：单 Redis + 看门狗够用，RedLock 主要用于极高可靠性场景。

---

## 常见追问

### Q：为什么不用 synchronized？

**A**：synchronized 是 JVM 级别的——只锁单个 JVM。多实例部署时（3 个 Lumina 实例），synchronized 管不到其他实例。Redis 锁是跨 JVM 的。

### Q：Zookeeper 的锁和 Redis 锁有什么区别？

**A**：ZK 强一致性（CP），Redis 最终一致性（AP）。ZK 更可靠但慢，Redis 快但极端情况可能丢锁。

---

## 小结

| 演进 | 解决的问题 |
|------|-----------|
| SETNX | 基本加锁 |
| SET NX PX | 防死锁（自动过期） |
| UUID | 防误删 |
| Lua 脚本 | 原子化 |
| Redisson 看门狗 | 自动续期 |
| RedLock | 多节点高可用 |

| Lumina 实践 | 一句话 |
|------------|--------|
| tryLock(0, 300) | 非阻塞 + 300 秒上限 |
| isHeldByCurrentThread | 防误删 |
| Cron 触发器 | 防多实例重复触发 |

---

📝 **本篇撰写期间修正的代码**：无。
