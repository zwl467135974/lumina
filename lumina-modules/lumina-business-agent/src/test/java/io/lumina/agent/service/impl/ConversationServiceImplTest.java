package io.lumina.agent.service.impl;

import io.lumina.agent.infrastructure.entity.ConversationDO;
import io.lumina.agent.infrastructure.mapper.ConversationMapper;
import io.lumina.agent.infrastructure.mapper.MessageMapper;
import io.lumina.agent.manager.MemoryManager;
import io.lumina.common.core.BaseContext;
import io.lumina.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * ConversationServiceImpl 单元测试
 *
 * <p>覆盖会话创建与边界校验。
 *
 * @author Lumina Team
 * @since 1.1.0
 */
@ExtendWith(MockitoExtension.class)
class ConversationServiceImplTest {

    @InjectMocks
    private ConversationServiceImpl conversationService;

    @Mock
    private ConversationMapper conversationMapper;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private MemoryManager memoryManager;

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    @Test
    void createConversationSuccess() {
        BaseContext.setTenantId(1L);
        BaseContext.setUserId(10L);

        when(conversationMapper.insert(any(ConversationDO.class))).thenAnswer(inv -> {
            ((ConversationDO) inv.getArgument(0)).setConversationId(5L);
            return 1;
        });

        ConversationDO conv = conversationService.createConversation(100L, "测试会话");

        assertThat(conv.getConversationId()).isEqualTo(5L);
        assertThat(conv.getAgentId()).isEqualTo(100L);
        assertThat(conv.getConversationUuid()).isNotBlank();
        assertThat(conv.getTenantId()).isEqualTo(1L);
        assertThat(conv.getMessageCount()).isZero();
    }

    @Test
    void createConversationWithoutTitle() {
        BaseContext.setTenantId(2L);

        when(conversationMapper.insert(any(ConversationDO.class))).thenAnswer(inv -> {
            ((ConversationDO) inv.getArgument(0)).setConversationId(6L);
            return 1;
        });

        ConversationDO conv = conversationService.createConversation(100L, null);

        assertThat(conv.getTitle()).isNull();
        assertThat(conv.getTenantId()).isEqualTo(2L);
    }

    @Test
    void getByUuidNotFoundThrows() {
        when(conversationMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> conversationService.getByUuid("nonexistent"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void listMessagesConversationNotFoundThrows() {
        when(conversationMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> conversationService.listMessages("nonexistent", 1, 10))
                .isInstanceOf(BusinessException.class);
    }
}
