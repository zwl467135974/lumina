package io.lumina.framework.cache;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
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
public class RedisCacheManager {

    @Autowired
    private RedissonClient redissonClient;

    // ==================== 缓存 Key 前缀 ====================
    private static final String USER_PERMISSIONS_KEY_PREFIX = "user:permissions:";
    private static final String ROLE_PERMISSIONS_KEY_PREFIX = "role:permissions:";
    private static final String TOKEN_BLACKLIST_KEY_PREFIX = "token:blacklist:";

    // ==================== 缓存过期时间 ====================
    private static final Duration USER_PERMISSIONS_TTL = Duration.ofMinutes(30);
    private static final Duration ROLE_PERMISSIONS_TTL = Duration.ofHours(1);

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
}
