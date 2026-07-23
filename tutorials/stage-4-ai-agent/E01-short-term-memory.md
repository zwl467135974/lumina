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

public void addMemory(String conversationId, Msg message) {
    String key = REDIS_KEY_PREFIX + conversationId;
    // 存到 Redis List（按时间顺序）
    redisCacheManager.addToList(key, message, Duration.ofHours(24));  // 24h 过期
}

public List<Msg> getRecentMemories(String conversationId, int maxCount) {
    String key = REDIS_KEY_PREFIX + conversationId;
    return redisCacheManager.getFromList(key, maxCount);    // 取最近 N 条
}
```

### 为什么用 Redis

- **快**——内存操作，毫秒级
- **自动过期**——24h 后自动清理，不占空间
- **可扩展**——多实例共享（Redis 是集中存储）

### Caffeine 降级

```java
// Redis 不可用时，降级为 Caffeine 内存缓存
private final Cache<String, LinkedList<Msg>> localCache = Caffeine.newBuilder()
    .maximumSize(1000)
    .expireAfterAccess(24, TimeUnit.HOURS)
    .build();
```

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| 短期记忆 | 当前会话最近几条消息 |
| Redis 热存储 | List 结构，24h 过期 |
| Caffeine 降级 | Redis 挂了用内存缓存 |
| getRecentMemories | 取最近 N 条（控制上下文窗口） |

> 🚀 [E02 — 长期记忆 →](E02-long-term-memory.md)

---

📝 **本篇撰写期间修正的代码**：无。
