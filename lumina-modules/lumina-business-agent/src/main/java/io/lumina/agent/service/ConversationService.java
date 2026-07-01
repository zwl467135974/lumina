package io.lumina.agent.service;

import io.lumina.agent.infrastructure.entity.ConversationDO;
import io.lumina.agent.infrastructure.entity.MessageDO;
import io.lumina.common.core.PageResult;

/**
 * 会话服务接口
 *
 * <p>管理会话生命周期与对话消息持久化。
 *
 * @author Lumina Team
 * @since 1.1.0
 */
public interface ConversationService {

    /**
     * 创建会话
     *
     * @param agentId 关联 Agent ID
     * @param title   会话标题（可空，默认取首条消息摘要）
     * @return 会话实体（含生成的 UUID）
     */
    ConversationDO createConversation(Long agentId, String title);

    /**
     * 分页查询当前租户下的会话列表
     *
     * @param agentId  Agent ID（可空，表示全部）
     * @param pageNum  页码
     * @param pageSize 每页数量
     */
    PageResult<ConversationDO> listConversations(Long agentId, Integer pageNum, Integer pageSize);

    /**
     * 按 UUID 获取会话
     */
    ConversationDO getByUuid(String uuid);

    /**
     * 按 UUID 逻辑删除会话（同时清空 Redis 记忆）
     */
    void deleteByUuid(String uuid);

    /**
     * 增加会话消息计数
     *
     * @param uuid  会话 UUID
     * @param delta 增量
     */
    void incrementMessageCount(String uuid, int delta);

    /**
     * 保存一条对话消息
     *
     * @param conversationUuid 会话 UUID
     * @param role             角色（user/assistant/system）
     * @param content          内容
     * @param tokenCount       Token 消耗量
     * @param durationMs       生成耗时（毫秒，仅 assistant）
     * @return 消息实体
     */
    MessageDO saveMessage(String conversationUuid, String role, String content, Integer tokenCount, Long durationMs);

    /**
     * 分页查询会话历史消息（按时间正序）
     */
    PageResult<MessageDO> listMessages(String conversationUuid, Integer pageNum, Integer pageSize);
}
