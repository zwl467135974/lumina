# 10 — 缓存模式（穿透/雪崩/击穿）

> **前置要求**：已完成 [09-HTTP/SSE](09-http-sse-protocol.md)
> **预计阅读**：15 分钟
> **难度**：⭐⭐⭐⭐☆

---

## 面试题引入

> **"面试官：缓存穿透、雪崩、击穿分别是什么？怎么解决？"**

---

## 三大缓存问题

### 穿透（Penetration）

**问题**：查一个不存在的数据，缓存没有→查 DB→DB 也没有→每次都打到 DB。

**攻击场景**：恶意请求查 id=-1（不存在），每次都穿透到 DB。

**解决**：
1. **缓存空值**：DB 查不到也缓存 `null`（短 TTL，如 60s）
2. **布隆过滤器**：提前过滤不存在的 key

### 雪崩（Avalanche）

**问题**：大量缓存**同时过期**→所有请求打到 DB→DB 崩溃。

**场景**：1000 个 key 都设了 30 分钟 TTL，30 分钟后同时失效。

**解决**：
1. **随机 TTL**：`30分钟 + 随机0-5分钟`，避免同时过期
2. **多级缓存**：Redis + 本地（Caffeine）

### 击穿（Breakdown）

**问题**：一个**热点 key** 过期瞬间，大量并发请求同时查 DB。

**场景**：首页热门数据缓存失效，1000 个请求同时来。

**解决**：
1. **互斥锁**：只让一个线程查 DB，其他等待
2. **逻辑过期**：不设物理 TTL，后台异步刷新

---

## Lumina 的缓存实践

### RedisCacheManager 的 TTL 策略

```java
// 文件：RedisCacheManager.java
private static final Duration USER_PERMISSIONS_TTL = Duration.ofMinutes(30);
private static final Duration ROLE_PERMISSIONS_TTL = Duration.ofHours(1);
// 不同业务不同 TTL → 天然避免雪崩
```

### Caffeine 多级缓存

```java
// 文件：MemoryManager.java
// Redis 不可用时降级为 Caffeine 本地缓存
private final Cache<String, LinkedList<Msg>> localCache = Caffeine.newBuilder()
    .maximumSize(1000)
    .expireAfterAccess(24, TimeUnit.HOURS)
    .build();
```

---

## 三者对比

| 问题 | 本质 | 解决 |
|------|------|------|
| 穿透 | 查不存在的数据 | 缓存空值 / 布隆过滤器 |
| 雪崩 | 大量 key 同时过期 | 随机 TTL / 多级缓存 |
| 击穿 | 热点 key 过期 | 互斥锁 / 逻辑过期 |

---

## 小结

| 知识点 | 一句话 |
|--------|--------|
| 穿透 | 查不存在的→缓存空值 |
| 雪崩 | 同时过期→随机 TTL |
| 击穿 | 热点过期→互斥锁 |
| Lumina | 不同业务不同 TTL |

---

📝 **本篇撰写期间修正的代码**：无。
