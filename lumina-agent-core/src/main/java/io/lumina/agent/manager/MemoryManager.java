package io.lumina.agent.manager;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
     */
    private final Map<String, List<Memory>> memoryStore = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Redis 模板（可选，如果未配置则使用内存存储）
     */
    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * ObjectMapper 用于序列化/反序列化
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

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

        if (redisTemplate != null) {
            // 使用 Redis 持久化
            addMemoryToRedis(sessionId, newMemory);
        } else {
            // 使用内存存储
            memoryStore.computeIfAbsent(sessionId, k -> new ArrayList<>())
                       .add(newMemory);

            // 限制记忆条数
            List<Memory> memories = memoryStore.get(sessionId);
            if (memories.size() > MAX_MEMORY_SIZE) {
                memories.remove(0);
                log.debug("会话 {} 记忆超出限制，移除最旧记录", sessionId);
            }
        }
    }

    /**
     * 添加记忆到 Redis
     */
    private void addMemoryToRedis(String sessionId, Memory newMemory) {
        try {
            String key = getRedisKey(sessionId);
            List<Memory> memories = getMemoriesFromRedis(sessionId);
            
            // 添加新记忆
            memories.add(newMemory);

            // 限制记忆条数
            if (memories.size() > MAX_MEMORY_SIZE) {
                memories.remove(0);
                log.debug("会话 {} 记忆超出限制，移除最旧记录", sessionId);
            }

            // 保存到 Redis，设置过期时间
            redisTemplate.opsForValue().set(
                    key,
                    memories,
                    Duration.ofSeconds(memoryTtl)
            );

            log.debug("记忆已保存到 Redis: sessionId={}, memoryCount={}", sessionId, memories.size());

        } catch (Exception e) {
            log.error("保存记忆到 Redis 失败，降级到内存存储: sessionId={}", sessionId, e);
            // 降级到内存存储
            memoryStore.computeIfAbsent(sessionId, k -> new ArrayList<>())
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
        if (redisTemplate != null) {
            // 从 Redis 获取
            return getMemoriesFromRedis(sessionId);
        } else {
            // 从内存获取
            return memoryStore.getOrDefault(sessionId, new ArrayList<>());
        }
    }

    /**
     * 从 Redis 获取记忆
     */
    @SuppressWarnings("unchecked")
    private List<Memory> getMemoriesFromRedis(String sessionId) {
        try {
            String key = getRedisKey(sessionId);
            Object value = redisTemplate.opsForValue().get(key);
            
            if (value == null) {
                return new ArrayList<>();
            }

            // 如果是 List，尝试转换为 Memory 列表
            if (value instanceof List) {
                List<?> list = (List<?>) value;
                List<Memory> memories = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Memory) {
                        memories.add((Memory) item);
                    } else if (item instanceof Map) {
                        // 如果是 Map，转换为 Memory
                        Memory memory = objectMapper.convertValue(item, Memory.class);
                        memories.add(memory);
                    }
                }
                return memories;
            }

            // 尝试直接反序列化
            return objectMapper.convertValue(value, new TypeReference<List<Memory>>() {});

        } catch (Exception e) {
            log.error("从 Redis 获取记忆失败，尝试从内存获取: sessionId={}", sessionId, e);
            // 降级到内存存储
            return memoryStore.getOrDefault(sessionId, new ArrayList<>());
        }
    }

    /**
     * 清空会话记忆
     *
     * @param sessionId 会话 ID
     */
    public void clearMemories(String sessionId) {
        if (redisTemplate != null) {
            // 从 Redis 删除
            try {
                String key = getRedisKey(sessionId);
                redisTemplate.delete(key);
                log.info("从 Redis 清空会话记忆: {}", sessionId);
            } catch (Exception e) {
                log.error("从 Redis 清空记忆失败: sessionId={}", sessionId, e);
            }
        }
        
        // 同时清除内存中的记录
        memoryStore.remove(sessionId);
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
            return memories;
        }
        return memories.subList(size - n, size);
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
