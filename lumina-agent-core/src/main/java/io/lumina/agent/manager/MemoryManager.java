package io.lumina.agent.manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.lumina.framework.cache.RedisCacheManager;
import io.lumina.agent.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

/**
 * 记忆管理器
 *
 * <p>管理 Agent 的对话记忆和历史记录。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class MemoryManager {

    /**
     * Redis Key 前缀
     */
    private static final String REDIS_KEY_PREFIX = "lumina:agent:memory:";

    /**
     * 会话记忆存储（内存备用，当 Redis 不可用时使用）
     *
     * <p>使用 Caffeine 缓存限制全局会话数量（最大 10000 个会话），
     * 并在 30 分钟无访问后自动过期，避免内存无限增长。
     */
    private final Cache<String, List<Memory>> memoryStore = Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .build();

    /**
     * Redis 缓存管理器（可选，如果未配置则使用内存存储）
     */
    @Autowired(required = false)
    private RedisCacheManager redisCacheManager;

    /**
     * ObjectMapper 用于序列化/反序列化
     */
    private final ObjectMapper objectMapper = JsonUtils.OBJECT_MAPPER;

    /**
     * 最大记忆条数
     */
    private static final int MAX_MEMORY_SIZE = 100;

    /**
     * 记忆过期时间（秒），默认 7 天
     */
    @Value("${lumina.agent.memory.ttl:604800}")
    private long memoryTtl;

    /**
     * 添加记忆
     *
     * @param sessionId 会话 ID
     * @param role      角色（user/assistant/system）
     * @param content   内容
     */
    public void addMemory(String sessionId, String role, String content) {
        Memory newMemory = new Memory(role, content, System.currentTimeMillis());

        if (redisCacheManager != null) {
            addMemoryToRedis(sessionId, newMemory);
        } else {
            memoryStore.asMap().computeIfAbsent(sessionId, k -> Collections.synchronizedList(new LinkedList<>()))
                       .add(newMemory);

            // 限制记忆条数
            List<Memory> memories = memoryStore.getIfPresent(sessionId);
            if (memories != null && memories.size() > MAX_MEMORY_SIZE) {
                memories.remove(0);
                log.debug("会话 {} 记忆超出限制，移除最旧记录", sessionId);
            }
        }
    }

    /**
     * 添加记忆到 Redis
     *
     * <p>顺序调用 RPUSH + LTRIM + EXPIRE 三次 Redis 操作来维护记忆列表的长度和过期。
     */
    private void addMemoryToRedis(String sessionId, Memory newMemory) {
        try {
            String key = getRedisKey(sessionId);

            redisCacheManager.pushToList(key, newMemory);
            redisCacheManager.trimList(key, -MAX_MEMORY_SIZE, -1);
            redisCacheManager.expire(key, Duration.ofSeconds(memoryTtl));

            log.debug("记忆已保存到 Redis: sessionId={}", sessionId);

        } catch (Exception e) {
            log.error("保存记忆到 Redis 失败，降级到内存存储: sessionId={}", sessionId, e);
            memoryStore.asMap().computeIfAbsent(sessionId, k -> Collections.synchronizedList(new LinkedList<>()))
                       .add(newMemory);
        }
    }

    /**
     * 获取会话记忆
     *
     * @param sessionId 会话 ID
     * @return 记忆列表
     */
    public List<Memory> getMemories(String sessionId) {
        if (redisCacheManager != null) {
            // 从 Redis 获取
            return getMemoriesFromRedis(sessionId);
        } else {
            // 从内存获取
            List<Memory> memories = memoryStore.getIfPresent(sessionId);
            return memories != null ? memories : new ArrayList<>();
        }
    }

    /**
     * 从 Redis 获取记忆
     */
    private List<Memory> getMemoriesFromRedis(String sessionId) {
        try {
            String key = getRedisKey(sessionId);
            List<?> rawList = redisCacheManager.getList(key);

            if (rawList == null || rawList.isEmpty()) {
                return new ArrayList<>();
            }

            List<Memory> memories = new ArrayList<>(rawList.size());
            for (Object item : rawList) {
                if (item instanceof Memory memory) {
                    memories.add(memory);
                } else if (item instanceof Map) {
                    memories.add(objectMapper.convertValue(item, Memory.class));
                }
            }
            return memories;

        } catch (Exception e) {
            log.error("从 Redis 获取记忆失败，尝试从内存获取: sessionId={}", sessionId, e);
            List<Memory> memories = memoryStore.getIfPresent(sessionId);
            return memories != null ? memories : new ArrayList<>();
        }
    }

    /**
     * 清空会话记忆
     *
     * @param sessionId 会话 ID
     */
    public void clearMemories(String sessionId) {
        if (redisCacheManager != null) {
            // 从 Redis 删除
            try {
                String key = getRedisKey(sessionId);
                redisCacheManager.delete(key);
                log.info("从 Redis 清空会话记忆: {}", sessionId);
            } catch (Exception e) {
                log.error("从 Redis 清空记忆失败: sessionId={}", sessionId, e);
            }
        }
        
        // 同时清除内存中的记录
        memoryStore.invalidate(sessionId);
        log.info("清空会话记忆: {}", sessionId);
    }

    /**
     * 获取最近的 N 条记忆
     *
     * @param sessionId 会话 ID
     * @param n         条数
     * @return 记忆列表
     */
    public List<Memory> getRecentMemories(String sessionId, int n) {
        List<Memory> memories = getMemories(sessionId);
        int size = memories.size();
        if (size <= n) {
            return new ArrayList<>(memories);
        }
        return new ArrayList<>(memories.subList(size - n, size));
    }

    /**
     * 获取 Redis Key
     */
    private String getRedisKey(String sessionId) {
        return REDIS_KEY_PREFIX + sessionId;
    }

    /**
     * 记忆
     *
     * <p>使用 record 类型，支持 Jackson 序列化/反序列化
     */
    public record Memory(String role, String content, Long timestamp) {
    }
}
