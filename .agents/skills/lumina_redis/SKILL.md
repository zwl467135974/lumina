# Lumina Redis 操作规范

## 核心原则

**所有 Redis 操作必须通过 `RedisCacheManager` 统一封装，禁止在业务代码中直接注入 `RedisTemplate` 或 `RedissonClient`。**

## 为什么

项目使用 Redisson 作为 Redis 客户端，`RedisConfig` 中配置了 `RedisTemplate` 使用 Jackson 序列化器。如果业务代码直接用 `RedisTemplate`，会导致：
1. **序列化器不兼容** — RedisTemplate 的 Jackson 序列化器与 Redisson 原生编码器格式不同，跨服务读写失败
2. **操作分散** — 同一个 Redis 操作逻辑散落在各 Service 中，无法统一管理（过期策略、key 命名、日志）
3. **测试困难** — 直接依赖底层客户端，Mock 困难

## RedisCacheManager 位置

```
lumina-framework/src/main/java/io/lumina/framework/cache/RedisCacheManager.java
```

## 支持的操作

| 方法 | 用途 | 示例 |
|------|------|------|
| `set(key, value, ttl)` | KV 缓存 | 权限缓存、配置缓存 |
| `get(key)` | KV 读取 | |
| `delete(key)` | KV 删除 | |
| `exists(key)` | 判断存在 | |
| `expire(key, ttl)` | 设置过期 | |
| `zAdd(key, score, member)` | ZSET 添加 | 在线用户记录 |
| `zRemove(key, member)` | ZSET 删除 | 强制下线 |
| `zRange(key)` | ZSET 查询 | 在线用户列表 |
| `zScore(key, member)` | ZSET 分数 | 登录时间 |
| `cacheUserPermissions(userId, perms)` | 业务专用 | 权限缓存 |
| `addTokenToBlacklist(token, ttl)` | 业务专用 | Token 黑名单 |

## 正确用法

```java
// ✅ 正确：通过 RedisCacheManager
@RequiredArgsConstructor
public class OnlineUserServiceImpl {
    private final RedisCacheManager redisCacheManager;
    
    public void recordLogin(Long userId, String username) {
        redisCacheManager.zAdd("online:users", System.currentTimeMillis(), 
                userId + ":" + username);
    }
}
```

## 错误用法

```java
// ❌ 错误：直接用 RedisTemplate（序列化器不兼容）
@Autowired
private RedisTemplate<String, Object> redisTemplate;

// ❌ 错误：直接用 RedissonClient（绕过封装层）
@Autowired
private RedissonClient redissonClient;
```

## 扩展 RedisCacheManager

如果 RedisCacheManager 没有你需要的数据结构操作（如 Hash、List、Set），**先扩展 RedisCacheManager**，再在业务代码中调用：

```java
// 在 RedisCacheManager 中添加
public void hSet(String key, String field, String value) {
    RMap<String, String> map = redissonClient.getMap(key);
    map.put(field, value);
}
```

## Key 命名规范

| 模式 | 示例 | 说明 |
|------|------|------|
| `{domain}:{entity}:{id}` | `user:permissions:1` | 实体缓存 |
| `{domain}:{action}` | `online:users` | 操作状态 |
| `token:blacklist:{token}` | — | 安全相关 |

## 何时使用 Redis vs 数据库

| 场景 | 用 Redis | 用数据库 |
|------|----------|----------|
| 在线状态、Session | ✅ | ❌ |
| 权限缓存（高频读） | ✅（TTL 30min） | 持久化源 |
| Token 黑名单 | ✅（TTL = token 有效期） | ❌ |
| Agent 对话记忆 | ✅（热数据） | 冷数据落库 |
| 业务配置（低频读） | ❌ | ✅ |
| 审计日志 | ❌ | ✅ |
