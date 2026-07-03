package io.lumina.agent.service.impl;

import io.lumina.agent.infrastructure.entity.AgentDO;
import io.lumina.agent.infrastructure.mapper.AgentMapper;
import io.lumina.agent.engine.AgentExecutionEngine;
import io.lumina.agent.model.ExecuteResult;
import io.lumina.framework.storage.FileService;
import io.lumina.agent.service.ConversationService;
import io.lumina.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * AgentServiceImpl 单元测试
 *
 * <p>覆盖 Agent 查询/删除/执行的核心校验：不存在、未启用、任务空。
 *
 * @author Lumina Team
 * @since 1.1.0
 */
@ExtendWith(MockitoExtension.class)
class AgentServiceImplTest {

    @InjectMocks
    private AgentServiceImpl agentService;

    @Mock
    private AgentMapper agentMapper;

    @Mock
    private AgentExecutionEngine agentExecutionEngine;

    @Mock
    private ConversationService conversationService;

    @Mock
    private FileService fileService;

    @Test
    void getAgentByIdNotFoundThrows() {
        when(agentMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> agentService.getAgentById(99L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deleteAgentNotFoundThrows() {
        when(agentMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> agentService.deleteAgent(99L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void executeAgentNotFoundThrows() {
        when(agentMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> agentService.executeAgent(99L, "task", null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void executeAgentNotActiveThrows() {
        AgentDO agentDO = new AgentDO();
        agentDO.setStatus(0);
        when(agentMapper.selectById(1L)).thenReturn(agentDO);

        assertThatThrownBy(() -> agentService.executeAgent(1L, "task", null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void executeAgentStreamEmptyTaskThrows() {
        assertThatThrownBy(() -> agentService.executeAgentStream(1L, "  ", null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void executeAgentStreamNotActiveThrows() {
        AgentDO agentDO = new AgentDO();
        agentDO.setStatus(0);
        when(agentMapper.selectById(1L)).thenReturn(agentDO);

        assertThatThrownBy(() -> agentService.executeAgentStream(1L, "task", null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void executeAgentMultimodalSuccessReturnsResult() {
        AgentDO agentDO = new AgentDO();
        agentDO.setAgentId(1L);
        agentDO.setAgentName("vision-agent");
        agentDO.setAgentType("assistant");
        agentDO.setStatus(1);
        when(agentMapper.selectById(1L)).thenReturn(agentDO);

        ExecuteResult executeResult = ExecuteResult.success("ok");
        when(agentExecutionEngine.executeMultimodalSync(
                eq("assistant"),
                eq("describe"),
                any(),
                any(),
                eq(null)
        )).thenReturn(executeResult);

        String result = agentService.executeAgentMultimodal(
                1L,
                "describe",
                java.util.Collections.emptyList(),
                null
        );

        assertThat(result).isEqualTo("ok");
    }

    @Test
    void executeAgentMultimodalNotActiveThrows() {
        AgentDO agentDO = new AgentDO();
        agentDO.setStatus(0);
        when(agentMapper.selectById(1L)).thenReturn(agentDO);

        assertThatThrownBy(() -> agentService.executeAgentMultimodal(
                1L,
                "describe",
                java.util.Collections.emptyList(),
                null
        )).isInstanceOf(BusinessException.class);
    }
}
