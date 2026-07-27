package io.lumina.agent.service.impl;

import io.lumina.agent.infrastructure.entity.MessageDO;
import io.lumina.agent.manager.MemoryManager;
import io.lumina.agent.service.ColdStartMemoryLoader;
import io.lumina.agent.service.ConversationService;
import io.lumina.common.core.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * DB 冷启记忆加载器实现
 *
 * <p>当 Redis 中的短期记忆丢失时，从 {@code lumina_message} 表加载历史消息，
 * 转换为 {@link MemoryManager.Memory} 返回。加载后由 MemoryManager 回填 Redis（warm-up）。
 *
 * @author Lumina Team
 * @since 3.9.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DbColdStartMemoryLoader implements ColdStartMemoryLoader {

    private final ConversationService conversationService;

    @Override
    public List<MemoryManager.Memory> loadFromDb(String conversationUuid, int limit) {
        if (conversationUuid == null || conversationUuid.isBlank()) {
            return List.of();
        }

        try {
            // 从 lumina_message 表查最近 N 条（按时间升序）
            PageResult<MessageDO> result = conversationService.listMessages(conversationUuid, 1, limit);

            if (result == null || result.getList() == null || result.getList().isEmpty()) {
                log.debug("冷启记忆加载: conversationUuid={} 无 DB 记录", conversationUuid);
                return List.of();
            }

            // MessageDO → Memory 转换
            List<MemoryManager.Memory> memories = new ArrayList<>();
            for (MessageDO msg : result.getList()) {
                long timestamp = msg.getCreateTime() != null
                        ? msg.getCreateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                        : System.currentTimeMillis();
                memories.add(new MemoryManager.Memory(msg.getRole(), msg.getContent(), timestamp));
            }

            log.info("冷启记忆恢复: conversationUuid={}, 从 DB 加载 {} 条消息", conversationUuid, memories.size());
            return memories;

        } catch (Exception e) {
            log.warn("冷启记忆加载失败: conversationUuid={}, error={}", conversationUuid, e.getMessage());
            return List.of();
        }
    }
}
