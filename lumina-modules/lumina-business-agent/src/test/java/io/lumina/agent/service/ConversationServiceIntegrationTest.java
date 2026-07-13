package io.lumina.agent.service;

import io.lumina.agent.BaseIntegrationTest;
import io.lumina.agent.infrastructure.entity.ConversationDO;
import io.lumina.agent.infrastructure.entity.MessageDO;
import io.lumina.common.core.BaseContext;
import io.lumina.common.core.PageResult;
import io.lumina.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ConversationService 集成测试
 *
 * <p>验证会话 CRUD、消息保存、租户隔离。
 *
 * @author Lumina Team
 * @since 3.1.0
 */
@Transactional
class ConversationServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private AgentService agentService;

    private static final Long TENANT_A = 9401L;
    private static final Long TENANT_B = 9402L;

    @BeforeEach
    void setUp() {
        BaseContext.setTenantId(TENANT_A);
        BaseContext.setUserId(TENANT_A);
    }

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    @Test
    void createConversationSuccess() {
        Long agentId = createAgent();

        ConversationDO conv = conversationService.createConversation(agentId, "测试会话");

        assertThat(conv.getConversationId()).isNotNull();
        assertThat(conv.getConversationUuid()).isNotBlank();
        assertThat(conv.getTitle()).isEqualTo("测试会话");
        assertThat(conv.getTenantId()).isEqualTo(TENANT_A);
    }

    @Test
    void getByUuidSuccess() {
        Long agentId = createAgent();
        ConversationDO created = conversationService.createConversation(agentId, "查询测试");

        ConversationDO found = conversationService.getByUuid(created.getConversationUuid());

        assertThat(found.getConversationUuid()).isEqualTo(created.getConversationUuid());
    }

    @Test
    void crossTenantGetByUuidThrows() {
        BaseContext.setTenantId(TENANT_A);
        Long agentId = createAgent();
        ConversationDO conv = conversationService.createConversation(agentId, "租户A会话");

        BaseContext.setTenantId(TENANT_B);
        assertThatThrownBy(() -> conversationService.getByUuid(conv.getConversationUuid()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void saveMessageAndList() {
        Long agentId = createAgent();
        ConversationDO conv = conversationService.createConversation(agentId, "消息测试");

        conversationService.saveMessage(conv.getConversationUuid(), "user", "你好", 5, 100L);
        conversationService.saveMessage(conv.getConversationUuid(), "assistant", "你好，有什么可以帮你的吗？", 15, 200L);

        PageResult<MessageDO> messages = conversationService.listMessages(conv.getConversationUuid(), 1, 10);
        assertThat(messages.getList()).hasSize(2);
        assertThat(messages.getList()).extracting(MessageDO::getRole)
                .containsExactly("user", "assistant");
    }

    @Test
    void incrementMessageCount() {
        Long agentId = createAgent();
        ConversationDO conv = conversationService.createConversation(agentId, "计数测试");

        conversationService.incrementMessageCount(conv.getConversationUuid(), 2);

        ConversationDO reloaded = conversationService.getByUuid(conv.getConversationUuid());
        assertThat(reloaded.getMessageCount()).isGreaterThanOrEqualTo(2);
    }

    private Long createAgent() {
        io.lumina.agent.domain.model.Agent agent = new io.lumina.agent.domain.model.Agent();
        agent.setAgentName("会话测试Agent-" + System.nanoTime());
        agent.setAgentType("ReAct");
        return agentService.createAgent(agent).getAgentId();
    }
}
