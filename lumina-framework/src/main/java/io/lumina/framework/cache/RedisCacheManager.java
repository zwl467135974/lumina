package io.lumina.framework.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RDeque;
import org.redisson.api.RKeys;
import org.redisson.api.RList;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 缓存管理器
 * 用于管理用户权限、角色权限、Token 黑名单等缓存
 *
 * @author Lumina Framework
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisCacheManager {

    private final RedissonClient redissonClient;

    // ==================== 缓存 Key 前缀 ====================
    private static final String USER_PERMISSIONS_KEY_PREFIX = "user:permissions:";
    private static final String ROLE_PERMISSIONS_KEY_PREFIX = "role:permissions:";
    private static final String TOKEN_BLACKLIST_KEY_PREFIX = "token:blacklist:";
    private static final String PERM_SNAPSHOT_KEY_PREFIX = "user:perms:";

    // ==================== 缓存过期时间 ====================
    private static final Duration USER_PERMISSIONS_TTL = Duration.ofMinutes(30);
    private static final Duration ROLE_PERMISSIONS_TTL = Duration.ofHours(1);
    private static final Duration PERM_SNAPSHOT_TTL = Duration.ofHours(2);

    // ==================== 用户权限缓存 ====================

    /**
     * 缓存用户权限列表
     *
     * @param userId 用户ID
     * @param permissions 权限代码集合
     */
    public void cacheUserPermissions(Long userId, Set<String> permissions) {
        String key = USER_PERMISSIONS_KEY_PREFIX + userId;
        RBucket<Set<String>> bucket = redissonClient.getBucket(key);
        bucket.set(permissions, USER_PERMISSIONS_TTL);
        log.debug("缓存用户权限: userId={}, permissions={}", userId, permissions);
    }

    /**
     * 获取用户权限缓存
     *
     * @param userId 用户ID
     * @return 权限代码集合，如果缓存不存在返回 null
     */
    public Set<String> getUserPermissions(Long userId) {
        String key = USER_PERMISSIONS_KEY_PREFIX + userId;
        RBucket<Set<String>> bucket = redissonClient.getBucket(key);
        Set<String> permissions = bucket.get();
        log.debug("获取用户权限缓存: userId={}, hit={}", userId, permissions != null);
        return permissions;
    }

    /**
     * 删除用户权限缓存
     *
     * @param userId 用户ID
     */
    public void evictUserPermissions(Long userId) {
        String key = USER_PERMISSIONS_KEY_PREFIX + userId;
        redissonClient.getBucket(key).delete();
        log.debug("删除用户权限缓存: userId={}", userId);
    }

    /**
     * 批量删除用户权限缓存
     *
     * @param userIds 用户ID集合
     */
    public void evictUserPermissionsBatch(Collection<Long> userIds) {
        userIds.forEach(this::evictUserPermissions);
        log.debug("批量删除用户权限缓存: count={}", userIds.size());
    }

    // ==================== 角色权限缓存 ====================

    /**
     * 缓存角色权限列表
     *
     * @param roleId 角色ID
     * @param permissions 权限代码集合
     */
    public void cacheRolePermissions(Long roleId, Set<String> permissions) {
        String key = ROLE_PERMISSIONS_KEY_PREFIX + roleId;
        RBucket<Set<String>> bucket = redissonClient.getBucket(key);
        bucket.set(permissions, ROLE_PERMISSIONS_TTL);
        log.debug("缓存角色权限: roleId={}, permissions={}", roleId, permissions);
    }

    /**
     * 获取角色权限缓存
     *
     * @param roleId 角色ID
     * @return 权限代码集合，如果缓存不存在返回 null
     */
    public Set<String> getRolePermissions(Long roleId) {
        String key = ROLE_PERMISSIONS_KEY_PREFIX + roleId;
        RBucket<Set<String>> bucket = redissonClient.getBucket(key);
        Set<String> permissions = bucket.get();
        log.debug("获取角色权限缓存: roleId={}, hit={}", roleId, permissions != null);
        return permissions;
    }

    /**
     * 删除角色权限缓存
     *
     * @param roleId 角色ID
     */
    public void evictRolePermissions(Long roleId) {
        String key = ROLE_PERMISSIONS_KEY_PREFIX + roleId;
        redissonClient.getBucket(key).delete();
        log.debug("删除角色权限缓存: roleId={}", roleId);
    }

    /**
     * 批量删除角色权限缓存
     *
     * @param roleIds 角色ID集合
     */
    public void evictRolePermissionsBatch(Collection<Long> roleIds) {
        roleIds.forEach(this::evictRolePermissions);
        log.debug("批量删除角色权限缓存: count={}", roleIds.size());
    }

    // ==================== Token 黑名单 ====================

    /**
     * 将 Token 加入黑名单
     *
     * @param token JWT Token
     * @param ttl 过期时间（秒），通常与 Token 的过期时间一致
     */
    public void addTokenToBlacklist(String token, long ttl) {
        String key = TOKEN_BLACKLIST_KEY_PREFIX + token;
        RBucket<Boolean> bucket = redissonClient.getBucket(key);
        bucket.set(true, ttl, TimeUnit.SECONDS);
        log.debug("Token 加入黑名单: ttl={}s", ttl);
    }

    /**
     * 检查 Token 是否在黑名单中
     *
     * @param token JWT Token
     * @return true 如果在黑名单中，false 否则
     */
    public boolean isTokenBlacklisted(String token) {
        String key = TOKEN_BLACKLIST_KEY_PREFIX + token;
        RBucket<Boolean> bucket = redissonClient.getBucket(key);
        Boolean blacklisted = bucket.get();
        boolean result = Boolean.TRUE.equals(blacklisted);
        log.debug("检查 Token 黑名单: blacklisted={}", result);
        return result;
    }

    /**
     * 从黑名单中移除 Token（通常不需要，因为会自动过期）
     *
     * @param token JWT Token
     */
    public void removeTokenFromBlacklist(String token) {
        String key = TOKEN_BLACKLIST_KEY_PREFIX + token;
        redissonClient.getBucket(key).delete();
        log.debug("从黑名单移除 Token");
    }

    // ==================== 权限快照缓存（Gateway 实时读取） ====================

    /**
     * 缓存用户权限快照（逗号分隔字符串，与 Gateway StringRedisTemplate 兼容）
     *
     * @param userId      用户ID
     * @param permissions 权限代码逗号分隔字符串
     */
    public void cachePermissionSnapshot(Long userId, String permissions) {
        String key = PERM_SNAPSHOT_KEY_PREFIX + userId;
        RBucket<String> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
        bucket.set(permissions, PERM_SNAPSHOT_TTL);
        log.debug("缓存权限快照: userId={}", userId);
    }

    /**
     * 删除用户权限快照
     *
     * @param userId 用户ID
     */
    public void evictPermissionSnapshot(Long userId) {
        String key = PERM_SNAPSHOT_KEY_PREFIX + userId;
        redissonClient.getBucket(key).delete();
        log.debug("删除权限快照: userId={}", userId);
    }

    /**
     * 删除所有用户权限快照（角色/权限变更时使用）
     */
    public void evictAllPermissionSnapshots() {
        RKeys keys = redissonClient.getKeys();
        long deleted = keys.deleteByPattern(PERM_SNAPSHOT_KEY_PREFIX + "*");
        log.debug("批量删除权限快照: count={}", deleted);
    }

    // ==================== 通用缓存方法 ====================

    /**
     * 设置缓存
     *
     * @param key 缓存键
     * @param value 缓存值
     * @param ttl 过期时间
     */
    public <T> void set(String key, T value, Duration ttl) {
        RBucket<T> bucket = redissonClient.getBucket(key);
        bucket.set(value, ttl);
        log.debug("设置缓存: key={}, ttl={}s", key, ttl.getSeconds());
    }

    /**
     * 获取缓存
     *
     * @param key 缓存键
     * @return 缓存值，如果不存在返回 null
     */
    public <T> T get(String key) {
        RBucket<T> bucket = redissonClient.getBucket(key);
        T value = bucket.get();
        log.debug("获取缓存: key={}, hit={}", key, value != null);
        return value;
    }

    /**
     * 删除缓存
     *
     * @param key 缓存键
     */
    public void delete(String key) {
        redissonClient.getBucket(key).delete();
        log.debug("删除缓存: key={}", key);
    }

    /**
     * 按模式批量删除缓存（如 {@code webhook:user:1:event:*}）
     *
     * @param pattern key 匹配模式（支持 * 通配符）
     * @return 删除的 key 数量
     */
    public long deleteByPattern(String pattern) {
        RKeys keys = redissonClient.getKeys();
        long deleted = keys.deleteByPattern(pattern);
        log.debug("按模式删除缓存: pattern={}, count={}", pattern, deleted);
        return deleted;
    }

    /**
     * 检查缓存是否存在
     *
     * @param key 缓存键
     * @return true 如果存在，false 否则
     */
    public boolean exists(String key) {
        RBucket<Object> bucket = redissonClient.getBucket(key);
        return bucket.isExists();
    }

    /**
     * 设置缓存的过期时间
     *
     * @param key 缓存键
     * @param ttl 过期时间
     */
    public void expire(String key, Duration ttl) {
        RBucket<Object> bucket = redissonClient.getBucket(key);
        bucket.expire(ttl);
        log.debug("设置缓存过期时间: key={}, ttl={}s", key, ttl.getSeconds());
    }

    /**
     * 获取缓存的剩余过期时间
     *
     * @param key 缓存键
     * @return 剩余时间（秒），-1 表示永不过期，-2 表示键不存在
     */
    public long getRemainingTTL(String key) {
        RBucket<Object> bucket = redissonClient.getBucket(key);
        long ttl = bucket.remainTimeToLive();
        log.debug("获取缓存剩余时间: key={}, ttl={}s", key, ttl / 1000);
        return ttl / 1000; // 转换为秒
    }

    // ==================== 原子计数器 ====================

    /**
     * 原子递增计数器并返回递增后的值
     *
     * @param key 计数器键
     * @return 递增后的值
     */
    public long incrementAndGet(String key) {
        RAtomicLong counter = redissonClient.getAtomicLong(key);
        long value = counter.incrementAndGet();
        log.debug("INCR: key={}, value={}", key, value);
        return value;
    }

    // ==================== 有序集合（ZSET）方法 ====================

    /**
     * 向有序集合添加成员
     *
     * @param key    集合键
     * @param score  分数
     * @param member 成员
     * @return true 如果是新添加的
     */
    public boolean zAdd(String key, double score, String member) {
        RScoredSortedSet<String> set = redissonClient.getScoredSortedSet(key);
        boolean added = set.add(score, member);
        log.debug("ZADD: key={}, score={}, member={}, added={}", key, score, member, added);
        return added;
    }

    /**
     * 从有序集合删除成员
     *
     * @param key    集合键
     * @param member 成员
     * @return true 如果删除成功
     */
    public boolean zRemove(String key, String member) {
        RScoredSortedSet<String> set = redissonClient.getScoredSortedSet(key);
        boolean removed = set.remove(member);
        log.debug("ZREM: key={}, member={}, removed={}", key, member, removed);
        return removed;
    }

    /**
     * 获取有序集合的所有成员
     *
     * @param key 集合键
     * @return 成员集合
     */
    public Collection<String> zRange(String key) {
        RScoredSortedSet<String> set = redissonClient.getScoredSortedSet(key);
        return set.readAll();
    }

    /**
     * 获取有序集合中成员的分数
     *
     * @param key    集合键
     * @param member 成员
     * @return 分数，null 如果成员不存在
     */
    public Double zScore(String key, String member) {
        RScoredSortedSet<String> set = redissonClient.getScoredSortedSet(key);
        return set.getScore(member);
    }

    // ==================== 列表（LIST）方法 ====================

    /**
     * 获取列表的全部元素
     *
     * @param key 列表键
     * @return 元素列表
     */
    public <T> List<T> getList(String key) {
        RList<T> list = redissonClient.getList(key);
        List<T> result = list.readAll();
        log.debug("获取列表: key={}, size={}", key, result.size());
        return result;
    }

    /**
     * 向列表尾部添加元素（RPUSH）
     *
     * @param key   列表键
     * @param value 元素
     */
    public <T> void pushToList(String key, T value) {
        RList<T> list = redissonClient.getList(key);
        list.add(value);
        log.debug("RPUSH 列表: key={}", key);
    }

    /**
     * 向列表尾部批量添加元素
     *
     * @param key    列表键
     * @param values 元素集合
     */
    public <T> void pushAllToList(String key, Collection<T> values) {
        RList<T> list = redissonClient.getList(key);
        list.addAll(values);
        log.debug("批量 RPUSH 列表: key={}, count={}", key, values.size());
    }

    /**
     * 弹出列表尾部元素（RPOP）
     *
     * @param key 列表键
     * @return 弹出的元素，列表为空时返回 null
     */
    public <T> T popFromList(String key) {
        RDeque<T> deque = redissonClient.getDeque(key);
        T value = deque.pollLast();
        log.debug("RPOP 列表: key={}", key);
        return value;
    }

    /**
     * 修剪列表，只保留指定索引范围的元素
     *
     * @param key        列表键
     * @param startIndex 起始索引（支持负数，-1 表示最后一个元素）
     * @param endIndex   结束索引（支持负数）
     */
    public void trimList(String key, int startIndex, int endIndex) {
        RList<Object> list = redissonClient.getList(key);
        list.trim(startIndex, endIndex);
        log.debug("LTRIM 列表: key={}, start={}, end={}", key, startIndex, endIndex);
    }
}
