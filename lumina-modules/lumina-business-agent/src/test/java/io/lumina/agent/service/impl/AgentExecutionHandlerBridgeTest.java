package io.lumina.agent.service.impl;

import io.lumina.agent.infrastructure.entity.AgentDO;
import io.lumina.agent.infrastructure.mapper.AgentMapper;
import io.lumina.agent.service.AgentService;
import io.lumina.common.core.BaseContext;
import io.lumina.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * AgentExecutionHandlerBridge 单元测试
 *
 * <p>验证编排引擎 → AgentService 的桥接逻辑：
 * Agent 存在性校验、租户隔离、状态检查、委托执行。
 *
 * @author Lumina Team
 * @since 3.2.0
 */
@ExtendWith(MockitoExtension.class)
class AgentExecutionHandlerBridgeTest {

    @Mock
    private AgentService agentService;

    @Mock
    private AgentMapper agentMapper;

    @InjectMocks
    private AgentExecutionHandlerBridge bridge;

    @BeforeEach
    void setUp() {
        BaseContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    @Test
    void executeAgentSuccess() {
        AgentDO agent = new AgentDO();
        agent.setAgentId(10L);
        agent.setTenantId(1L);
        agent.setStatus(1);
        when(agentMapper.selectById(10L)).thenReturn(agent);
        when(agentService.executeAgent(10L, "执行任务", null)).thenReturn("执行结果");

        String result = bridge.executeAgent(10L, "执行任务", null);

        assertThat(result).isEqualTo("执行结果");
    }

    @Test
    void executeAgentNotFoundThrows() {
        when(agentMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> bridge.executeAgent(999L, "任务", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Agent 不存在");
    }

    @Test
    void executeAgentTenantIsolationThrows() {
        // Agent 属于租户 2，但当前租户是 1
        AgentDO agent = new AgentDO();
        agent.setAgentId(20L);
        agent.setTenantId(2L);
        agent.setStatus(1);
        when(agentMapper.selectById(20L)).thenReturn(agent);

        assertThatThrownBy(() -> bridge.executeAgent(20L, "任务", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Agent 不存在");
    }

    @Test
    void executeAgentNotActiveThrows() {
        AgentDO agent = new AgentDO();
        agent.setAgentId(30L);
        agent.setTenantId(1L);
        agent.setStatus(0); // 未启用
        when(agentMapper.selectById(30L)).thenReturn(agent);

        assertThatThrownBy(() -> bridge.executeAgent(30L, "任务", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Agent 未启用");
    }

    @Test
    void executeAgentWithoutTenantContextUsesZero() {
        BaseContext.clear();

        AgentDO agent = new AgentDO();
        agent.setAgentId(40L);
        agent.setTenantId(0L); // 无租户上下文回退为 0
        agent.setStatus(1);
        when(agentMapper.selectById(40L)).thenReturn(agent);
        when(agentService.executeAgent(40L, "任务", null)).thenReturn("OK");

        String result = bridge.executeAgent(40L, "任务", null);

        assertThat(result).isEqualTo("OK");
    }

    @Test
    void executeAgentWithConversationUuid() {
        AgentDO agent = new AgentDO();
        agent.setAgentId(50L);
        agent.setTenantId(1L);
        agent.setStatus(1);
        when(agentMapper.selectById(50L)).thenReturn(agent);
        when(agentService.executeAgent(50L, "任务", "conv-123")).thenReturn("结果");

        String result = bridge.executeAgent(50L, "任务", "conv-123");

        assertThat(result).isEqualTo("结果");
    }
}
