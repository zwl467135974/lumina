package io.lumina.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.lumina.agent.infrastructure.entity.ConversationDO;
import io.lumina.agent.infrastructure.entity.MessageDO;
import io.lumina.agent.infrastructure.mapper.ConversationMapper;
import io.lumina.agent.infrastructure.mapper.MessageMapper;
import io.lumina.agent.manager.MemoryManager;
import io.lumina.agent.service.ConversationService;
import io.lumina.common.core.BaseContext;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.core.PageResult;
import io.lumina.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 会话服务实现
 *
 * @author Lumina Team
 * @since 1.1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;

    @Autowired(required = false)
    private MemoryManager memoryManager;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConversationDO createConversation(Long agentId, String title) {
        Long tenantId = BaseContext.getTenantId();
        Long userId = BaseContext.getUserId();

        ConversationDO conv = new ConversationDO();
        conv.setConversationUuid(UUID.randomUUID().toString().replace("-", ""));
        conv.setAgentId(agentId);
        conv.setTenantId(tenantId != null ? tenantId : 0L);
        conv.setUserId(userId);
        conv.setTitle(title);
        conv.setMessageCount(0);
        conv.setStatus(1);

        conversationMapper.insert(conv);
        log.info("创建会话: uuid={}, agentId={}, tenantId={}", conv.getConversationUuid(), agentId, tenantId);
        return conv;
    }

    @Override
    public PageResult<ConversationDO> listConversations(Long agentId, Integer pageNum, Integer pageSize) {
        Long tenantId = BaseContext.getTenantId();

        LambdaQueryWrapper<ConversationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConversationDO::getTenantId, tenantId != null ? tenantId : 0L);
        if (agentId != null) {
            wrapper.eq(ConversationDO::getAgentId, agentId);
        }
        wrapper.orderByDesc(ConversationDO::getUpdateTime);

        Page<ConversationDO> page = new Page<>(pageNum, pageSize);
        Page<ConversationDO> result = conversationMapper.selectPage(page, wrapper);

        PageResult<ConversationDO> pageResult = new PageResult<>();
        pageResult.setList(result.getRecords());
        pageResult.setTotal(result.getTotal());
        pageResult.setPageNum(pageNum);
        pageResult.setPageSize(pageSize);
        pageResult.setPages((int) result.getPages());
        return pageResult;
    }

    @Override
    public ConversationDO getByUuid(String uuid) {
        LambdaQueryWrapper<ConversationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConversationDO::getConversationUuid, uuid);
        ConversationDO conv = conversationMapper.selectOne(wrapper);
        if (conv == null) {
            throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND, "会话不存在: " + uuid);
        }
        Long currentTenantId = BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L;
        if (!currentTenantId.equals(conv.getTenantId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return conv;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByUuid(String uuid) {
        ConversationDO conv = getByUuid(uuid);
        conversationMapper.deleteById(conv.getConversationId());

        messageMapper.delete(new LambdaQueryWrapper<MessageDO>()
                .eq(MessageDO::getConversationId, conv.getConversationId()));

        if (memoryManager != null) {
            memoryManager.clearMemories(uuid);
        }
        log.info("删除会话: uuid={}", uuid);
    }

    @Override
    public void incrementMessageCount(String uuid, int delta) {
        LambdaUpdateWrapper<ConversationDO> update = new LambdaUpdateWrapper<>();
        update.eq(ConversationDO::getConversationUuid, uuid)
                .setSql("message_count = message_count + " + delta);
        conversationMapper.update(null, update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MessageDO saveMessage(String conversationUuid, String role, String content,
                                 Integer tokenCount, Long durationMs) {
        return saveMessage(conversationUuid, role, content, tokenCount, durationMs, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MessageDO saveMessage(String conversationUuid, String role, String content,
                                 Integer tokenCount, Long durationMs, String fileIds) {
        ConversationDO conv = getByUuid(conversationUuid);

        MessageDO msg = new MessageDO();
        msg.setConversationId(conv.getConversationId());
        msg.setRole(role);
        msg.setContent(content);
        msg.setTokenCount(tokenCount != null ? tokenCount : 0);
        msg.setDurationMs(durationMs);
        msg.setFileIds(fileIds);

        messageMapper.insert(msg);
        return msg;
    }

    @Override
    public PageResult<MessageDO> listMessages(String conversationUuid, Integer pageNum, Integer pageSize) {
        ConversationDO conv = getByUuid(conversationUuid);

        LambdaQueryWrapper<MessageDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MessageDO::getConversationId, conv.getConversationId())
                .orderByAsc(MessageDO::getCreateTime);

        Page<MessageDO> page = new Page<>(pageNum, pageSize);
        Page<MessageDO> result = messageMapper.selectPage(page, wrapper);

        PageResult<MessageDO> pageResult = new PageResult<>();
        pageResult.setList(result.getRecords());
        pageResult.setTotal(result.getTotal());
        pageResult.setPageNum(pageNum);
        pageResult.setPageSize(pageSize);
        pageResult.setPages((int) result.getPages());
        return pageResult;
    }
}
