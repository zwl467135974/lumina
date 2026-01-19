package io.lumina.agent.manager;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
     * 会话记忆存储
     */
    private final Map<String, List<Memory>> memoryStore = new ConcurrentHashMap<>();

    /**
     * 最大记忆条数
     */
    private static final int MAX_MEMORY_SIZE = 100;

    /**
     * 添加记忆
     *
     * @param sessionId 会话 ID
     * @param role      角色（user/assistant/system）
     * @param content   内容
     */
    public void addMemory(String sessionId, String role, String content) {
        memoryStore.computeIfAbsent(sessionId, k -> new ArrayList<>())
                   .add(new Memory(role, content, System.currentTimeMillis()));

        // 限制记忆条数
        List<Memory> memories = memoryStore.get(sessionId);
        if (memories.size() > MAX_MEMORY_SIZE) {
            memories.remove(0);
            log.debug("会话 {} 记忆超出限制，移除最旧记录", sessionId);
        }
    }

    /**
     * 获取会话记忆
     *
     * @param sessionId 会话 ID
     * @return 记忆列表
     */
    public List<Memory> getMemories(String sessionId) {
        return memoryStore.getOrDefault(sessionId, new ArrayList<>());
    }

    /**
     * 清空会话记忆
     *
     * @param sessionId 会话 ID
     */
    public void clearMemories(String sessionId) {
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
     * 记忆
     */
    public record Memory(String role, String content, Long timestamp) {
    }
}
