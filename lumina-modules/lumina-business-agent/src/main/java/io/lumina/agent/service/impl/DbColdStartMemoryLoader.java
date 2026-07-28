package io.lumina.agent.service.impl;

import io.lumina.agent.infrastructure.entity.ConversationDO;
import io.lumina.agent.infrastructure.entity.MessageDO;
import io.lumina.agent.infrastructure.mapper.ConversationMapper;
import io.lumina.agent.infrastructure.mapper.MessageMapper;
import io.lumina.agent.manager.MemoryManager;
import io.lumina.agent.service.ColdStartMemoryLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * DB 冷启记忆加载器实现
 *
 * <p>当 Redis 中的短期记忆丢失时，从 {@code lumina_message} 表加载历史消息，
 * 转换为 {@link MemoryManager.Memory} 返回。加载后由 MemoryManager 回填 Redis（warm-up）。
 *
 * <p>语义说明：加载的是**最近的 N 条**消息（按 create_time DESC 取，再反转为升序），
 * 而非最早的 N 条——冷启动的目的是恢复"最近的对话上下文"。
 *
 * @author Lumina Team
 * @since 3.9.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DbColdStartMemoryLoader implements ColdStartMemoryLoader {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;

    @Override
    public List<MemoryManager.Memory> loadFromDb(String conversationUuid, int limit) {
        if (conversationUuid == null || conversationUuid.isBlank()) {
            return List.of();
        }

        try {
            // 1. uuid → conversationId（避免依赖 ConversationService 可能的循环依赖）
            ConversationDO conv = conversationMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ConversationDO>()
                            .eq(ConversationDO::getConversationUuid, conversationUuid));
            if (conv == null) {
                log.debug("冷启记忆加载: conversationUuid={} 不存在", conversationUuid);
                return List.of();
            }

            // 2. 取最近 N 条（DESC），再反转为时间升序——这是冷启动语义
            List<MessageDO> recent = messageMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MessageDO>()
                            .eq(MessageDO::getConversationId, conv.getConversationId())
                            .orderByDesc(MessageDO::getCreateTime)
                            .last("LIMIT " + Math.max(1, limit)));
            if (recent.isEmpty()) {
                log.debug("冷启记忆加载: conversationUuid={} 无 DB 记录", conversationUuid);
                return List.of();
            }
            Collections.reverse(recent); // DESC → ASC

            // 3. MessageDO → Memory
            List<MemoryManager.Memory> memories = new ArrayList<>(recent.size());
            for (MessageDO msg : recent) {
                long timestamp = msg.getCreateTime() != null
                        ? msg.getCreateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                        : System.currentTimeMillis();
                memories.add(new MemoryManager.Memory(msg.getRole(), msg.getContent(), timestamp));
            }

            log.info("冷启记忆恢复: conversationUuid={}, 从 DB 加载最近 {} 条消息", conversationUuid, memories.size());
            return memories;

        } catch (Exception e) {
            log.warn("冷启记忆加载失败: conversationUuid={}, error={}", conversationUuid, e.getMessage());
            return List.of();
        }
    }
}
