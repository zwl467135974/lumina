# E01 — 短期记忆

> **前置要求**：已完成 [模块 D 全部](README.md)
> **预计阅读**：15 分钟
> **难度**：⭐⭐☆☆☆

---

## 这节解决什么问题

用户和 Agent 多轮对话时，AI 怎么"记住"前面说过什么？这节讲**短期记忆**——当前会话的最近几条消息。

---

## 短期记忆是什么？先建立直觉

### 类比：便签纸

你在打电话时，手边放一张便签纸记下对方说的关键信息——挂电话后就扔掉。这就是短期记忆：**当前会话用，结束即弃**。

---

## Lumina 的实现：Redis 热存储

```java
// 文件：lumina-agent-core/.../manager/MemoryManager.java
private static final String REDIS_KEY_PREFIX = "lumina:agent:memory:";
private static final int MAX_MEMORY_SIZE = 100;          // 每个会话最多 100 条

@Value("${lumina.agent.memory.ttl:604800}")              // 默认 7 天（604800 秒）
private long memoryTtl;

public void addMemory(String sessionId, String role, String content) {
    Memory newMemory = new Memory(role, content, System.currentTimeMillis());
    String key = REDIS_KEY_PREFIX + sessionId;
    // RPUSH 入队 + LTRIM 裁剪到最近 100 条 + EXPIRE 续期
    redisCacheManager.pushToList(key, newMemory);
    redisCacheManager.trimList(key, -MAX_MEMORY_SIZE, -1);
    redisCacheManager.expire(key, Duration.ofSeconds(memoryTtl));
}

// 取最近 n 条：先 getList 读全部，再截取尾部 n 条
public List<Memory> getRecentMemories(String sessionId, int n) {
    List<Memory> all = getMemories(sessionId);   // 内部调用 redisCacheManager.getList(key)
    int size = all.size();
    return size <= n ? new ArrayList<>(all)
                     : new ArrayList<>(all.subList(size - n, size));
}

// Memory 是一个 record，可被 Jackson 序列化后存入 Redis
public record Memory(String role, String content, Long timestamp) {}
```

> **关键点**：写入不是"存进去就完事"，而是 `pushToList` + `trimList` + `expire` 三连——每条新记忆都会把列表裁剪到最多 `MAX_MEMORY_SIZE=100` 条，并刷新整个 key 的 TTL（默认 7 天）。这样既控制了单会话的上下文长度，又让活跃会话永不过期、冷会话自动清理。

### 为什么用 Redis

- **快**——内存操作，毫秒级
- **自动过期**——默认 7 天后自动清理，活跃会话靠 `expire` 续期，不占空间
- **可扩展**——多实例共享（Redis 是集中存储）

### Caffeine 降级

```java
// Redis 不可用时，降级为 Caffeine 内存缓存
private final Cache<String, List<Memory>> memoryStore = Caffeine.newBuilder()
    .maximumSize(10000)                          // 最多缓存 10000 个会话
    .expireAfterAccess(30, TimeUnit.MINUTES)     // 30 分钟无访问即过期
    .build();
```

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| 短期记忆 | 当前会话最近几条消息 |
| Redis 热存储 | List 结构，默认 7 天 TTL，裁剪到 100 条 |
| Caffeine 降级 | Redis 挂了用内存缓存（最多 10000 会话，30 分钟过期） |
| getRecentMemories | 取最近 N 条（控制上下文窗口） |

> 🚀 [E02 — 长期记忆 →](E02-long-term-memory.md)

---

📝 **本篇撰写期间修正的代码**：无。
