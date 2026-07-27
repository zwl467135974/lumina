package io.lumina.agent.service;

import io.lumina.agent.manager.MemoryManager;

import java.util.List;

/**
 * 冷启记忆加载器（Cold-Start Memory Loader）
 *
 * <p>当 Redis 中的短期记忆过期或丢失时，从 MySQL {@code lumina_message} 表加载历史消息，
 * 恢复 Agent 的对话上下文。
 *
 * <p>端口接口模式：接口定义在 lumina-agent-core，实现在 lumina-business-agent，
 * 通过 {@code @Autowired(required = false)} 注入，保持 core 模块独立。
 *
 * @author Lumina Team
 * @since 3.9.0
 */
public interface ColdStartMemoryLoader {

    /**
     * 从数据库加载会话的最近 N 条消息
     *
     * @param conversationUuid 会话 UUID
     * @param limit            最大加载条数
     * @return 按时间升序排列的记忆列表（最早→最近）；无数据时返回空列表
     */
    List<MemoryManager.Memory> loadFromDb(String conversationUuid, int limit);
}
