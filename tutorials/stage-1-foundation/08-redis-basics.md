# 08 — Redis 基础

> **前置要求**：已完成 [07-MyBatis-Plus 在 Lumina](07-mybatis-plus-in-lumina.md)
> **预计阅读**：20 分钟
> **难度**：⭐⭐☆☆☆

---

## 这节解决什么问题

后端不只是"读写数据库"。很多场景 MySQL 扛不住——比如缓存热点数据、分布式锁、限流计数器。这时候 Redis 登场。

这节讲清 Redis 是什么、Redisson 客户端怎么用、Lumina 为什么选择封装一层 `RedisCacheManager` 而不是直接用 Redisson。

---

## Redis 是什么？先建立直觉

### 类比：你的桌面便签 vs 柜子里的档案

- **MySQL** = 柜子里的档案——存得多、查得慢、按结构存
- **Redis** = 桌面便签——存得少、查得**极快**（内存操作）、随时撕掉

**Redis 是内存数据库**。数据放在内存里（不是硬盘），所以读写速度是 MySQL 的 10-100 倍。但内存贵且断电会丢，所以 Redis 适合存"丢了也能恢复的临时数据"。

### Redis 能干什么

| 用途 | 类比 | Lumina 里的场景 |
|------|------|----------------|
| **缓存** | 便签记常用信息 | 用户权限缓存、会话热记忆 |
| **分布式锁** | "我在用"的牌子 | Cron 触发器防多实例重复执行 |
| **限流计数器** | "今天只能进 3 次" | Per-Agent 限流 |
| **Token 黑名单** | 失效的门禁卡 | 登出后 JWT 失效 |
| **消息广播** | 大喇叭通知 | 跨实例 SSE 通知推送 |

> 💡 **注意**：Redis 不替代 MySQL。它们是配合关系——MySQL 存持久数据，Redis 存临时/高速数据。

---

## Redisson：Java 版 Redis 客户端

### 为什么不直接用 Redis 命令？

Java 连 Redis 需要客户端库。最底层的是 Jedis（直接发 Redis 命令），但 Jedis 只提供基本操作，分布式锁等高级功能要自己实现。

**Redisson** 是更高级的客户端——它把 Redis 的各种数据结构封装成 Java 对象：

| Redis 数据结构 | Redisson 封装 | Java 类比 |
|---------------|--------------|----------|
| String | `RBucket<T>` | 一个变量 |
| 计数器 | `RAtomicLong` | AtomicLong |
| 锁 | `RLock` | ReentrantLock |
| 队列 | `RQueue` / `RDeque` | Queue / Deque |
| 有序集合 | `RScoredSortedSet` | SortedSet |
| 发布订阅 | `RTopic` | 事件广播 |

用法就像用普通 Java 对象，底层自动翻译成 Redis 命令。

### 基本读写

```java
// 写入（设值 + 过期时间）
RBucket<String> bucket = redissonClient.getBucket("greeting");
bucket.set("hello", Duration.ofMinutes(30));   // 30 分钟后自动过期

// 读取
String value = bucket.get();   // "hello"

// 删除
bucket.delete();
```

### 原子计数器

```java
RAtomicLong counter = redissonClient.getAtomicLong("visit:count");
counter.incrementAndGet();    // +1
long current = counter.get(); // 读当前值
```

---

## Lumina 的选择：封装 RedisCacheManager

### 为什么不直接用 RedissonClient？

Lumina 的 `AGENTS.md` 有一条硬性规则：

> **禁止直接用 RedisTemplate / RedissonClient，必须走 RedisCacheManager**

原因：
1. **统一 Key 前缀管理**——所有 key 有规范前缀（如 `user:permissions:1`），避免散乱
2. **统一 TTL 策略**——不同业务有标准过期时间（权限 30 分钟、Token 黑名单按 JWT 过期时间）
3. **可替换性**——如果以后换 Redis 客户端，只改 RedisCacheManager，业务代码不动
4. **可测试性**——Mock RedisCacheManager 比 Mock RedissonClient 简单

### RedisCacheManager 提供什么

```java
// 文件：lumina-framework/.../cache/RedisCacheManager.java
@Component
@RequiredArgsConstructor
public class RedisCacheManager {

    private final RedissonClient redissonClient;    // ← 底层还是 Redisson

    // ==================== 缓存 Key 前缀 ====================
    private static final String USER_PERMISSIONS_KEY_PREFIX = "user:permissions:";
    private static final String TOKEN_BLACKLIST_KEY_PREFIX = "token:blacklist:";

    // ==================== 缓存过期时间 ====================
    private static final Duration USER_PERMISSIONS_TTL = Duration.ofMinutes(30);

    // 缓存用户权限
    public void cacheUserPermissions(Long userId, Set<String> permissions) {
        String key = USER_PERMISSIONS_KEY_PREFIX + userId;
        RBucket<Set<String>> bucket = redissonClient.getBucket(key);
        bucket.set(permissions, USER_PERMISSIONS_TTL);          // ← 带 TTL
    }

    // 读取用户权限
    public Set<String> getUserPermissions(Long userId) {
        String key = USER_PERMISSIONS_KEY_PREFIX + userId;
        return redissonClient.getBucket(key).get();
    }

    // 删除用户权限缓存（权限变更时调用）
    public void evictUserPermissions(Long userId) {
        String key = USER_PERMISSIONS_KEY_PREFIX + userId;
        redissonClient.getBucket(key).delete();
    }
}
```

业务代码这样用：
```java
// 查权限时先查 Redis 缓存
Set<String> perms = redisCacheManager.getUserPermissions(userId);
if (perms == null) {
    // 缓存没有，查数据库
    perms = permissionMapper.selectByUserId(userId);
    // 写入缓存
    redisCacheManager.cacheUserPermissions(userId, perms);
}
```

---

## Redis 的核心数据结构

### String（RBucket）

最简单的 key-value。用于缓存单个对象。

```java
RBucket<User> bucket = redissonClient.getBucket("user:1");
bucket.set(user, Duration.ofMinutes(30));
User cached = bucket.get();
```

### 计数器（RAtomicLong）

原子递增/递减。用于限流计数。

```java
RAtomicLong counter = redissonClient.getAtomicLong("rate:agent:1");
long count = counter.incrementAndGet();   // 原子 +1 并返回新值
if (count == 1) {
    counter.expire(Duration.ofMinutes(1)); // 第一次访问时设过期时间
}
```

> 📖 Lumina 的限流实现在[下一篇 09-Redis 在 Lumina](09-redis-in-lumina.md)详解。

### 分布式锁（RLock）

多实例环境下的互斥锁。详见下一篇。

---

## 动手试试

1. **打开 `RedisCacheManager.java`**：看看它定义了哪些 Key 前缀（`user:permissions:`、`token:blacklist:` 等）
2. **注意 TTL 常量**：不同业务不同过期时间
3. **在项目里搜索 `RedisCacheManager` 的使用**：看看哪些 Service 注入并使用了它

---

## 小结

| 你现在应该知道 | 一句话记忆 |
|---------------|-----------|
| Redis 是什么 | 内存数据库，比 MySQL 快 10-100 倍，适合缓存/锁/计数 |
| Redisson | Java 版 Redis 客户端，把 Redis 结构封装成 Java 对象 |
| RBucket | 存单个对象（缓存） |
| RAtomicLong | 原子计数器（限流） |
| RedisCacheManager | Lumina 的统一封装，禁止直接用 RedissonClient |

---

## 下一步

下一篇 [Redis 在 Lumina 的实战](09-redis-in-lumina.md)——分布式锁怎么防多实例重复触发、限流怎么实现。

> 🚀 **现在继续**：[09 — Redis 在 Lumina →](09-redis-in-lumina.md)

---

## 自测题

1. **Redis 和 MySQL 的区别是什么？什么时候用 Redis？**
   <details><summary>答案</summary>Redis 是内存数据库，极快但容量有限、断电可能丢。适合缓存、锁、计数器等临时数据。MySQL 是硬盘数据库，持久可靠但慢。两者配合使用。</details>

2. **Lumina 为什么禁止直接用 RedissonClient，必须走 RedisCacheManager？**
   <details><summary>答案</summary>统一 Key 前缀管理、统一 TTL 策略、可替换底层客户端、方便单元测试 Mock。</details>

3. **`cacheUserPermissions` 设了 30 分钟 TTL，30 分钟后会发生什么？**
   <details><summary>答案</summary>Redis 自动删除这个 Key。下次查权限时缓存命中不了（返回 null），业务代码再从数据库查并重新写入缓存。</details>

---

📝 **本篇撰写期间修正的代码**：无。
