package io.lumina.agent.service;

import java.util.List;

/**
 * 反思记忆服务（Reflective Memory）
 *
 * <p>对话后异步提取关键事实，跨会话保留，下次对话注入上下文。
 *
 * @author Lumina Team
 * @since 3.3.1
 */
public interface ReflectiveMemoryService {

    /**
     * 从对话中提取关键事实并持久化（异步调用，不阻塞主流程）
     *
     * @param userId        用户 ID
     * @param agentId       Agent ID
     * @param conversationId 会话 ID
     * @param userMessage    用户消息
     * @param assistantReply 助手回复
     */
    void extractAndSave(Long userId, Long agentId, String conversationId,
                        String userMessage, String assistantReply);

    /**
     * 获取用户的长期记忆（按重要度 + 时间排序）
     *
     * @param userId  用户 ID
     * @param agentId Agent ID
     * @return 记忆内容列表
     */
    List<String> getLongTermMemories(Long userId, Long agentId);
}
