package io.lumina.agent.service.impl;

import io.lumina.agent.api.dto.AgentTaskRequestDTO;
import io.lumina.agent.domain.model.Agent;
import io.lumina.agent.infrastructure.entity.AgentTaskDO;
import io.lumina.agent.infrastructure.mapper.AgentTaskMapper;
import io.lumina.agent.service.AgentService;
import io.lumina.common.core.BaseContext;
import io.lumina.common.core.LoginContext;
import io.lumina.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AgentTaskServiceImpl 单元测试
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@ExtendWith(MockitoExtension.class)
class AgentTaskServiceImplTest {

    @InjectMocks
    private AgentTaskServiceImpl agentTaskService;

    @Mock
    private AgentTaskMapper agentTaskMapper;

    @Mock
    private AgentService agentService;

    @Mock
    private Executor agentTaskExecutor;

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    @Test
    void submitTaskCreatesQueuedTask() {
        BaseContext.setTenantId(1L);
        BaseContext.setUserId(10L);
        Agent agent = new Agent();
        agent.setStatus(1);
        when(agentService.getAgentById(100L)).thenReturn(agent);
        when(agentTaskMapper.insert(any(AgentTaskDO.class))).thenAnswer(inv -> {
            ((AgentTaskDO) inv.getArgument(0)).setId(1L);
            return 1;
        });

        AgentTaskRequestDTO dto = new AgentTaskRequestDTO();
        dto.setTask("async task");

        AgentTaskDO task = agentTaskService.submitTask(100L, dto);

        ArgumentCaptor<AgentTaskDO> captor = ArgumentCaptor.forClass(AgentTaskDO.class);
        verify(agentTaskMapper).insert(captor.capture());
        verify(agentTaskExecutor).execute(any(Runnable.class));
        assertThat(task.getTaskUuid()).isNotBlank();
        assertThat(captor.getValue().getStatus()).isEqualTo("QUEUED");
        assertThat(captor.getValue().getTenantId()).isEqualTo(1L);
        assertThat(captor.getValue().getCreateBy()).isEqualTo(10L);
    }

    @Test
    void submitTaskRejectsInactiveAgent() {
        Agent agent = new Agent();
        agent.setStatus(0);
        when(agentService.getAgentById(100L)).thenReturn(agent);
        AgentTaskRequestDTO dto = new AgentTaskRequestDTO();
        dto.setTask("async task");

        assertThatThrownBy(() -> agentTaskService.submitTask(100L, dto))
                .isInstanceOf(BusinessException.class);
        verify(agentTaskMapper, never()).insert(any(AgentTaskDO.class));
    }

    @Test
    void getTaskNotFoundThrows() {
        when(agentTaskMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> agentTaskService.getTask("missing"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void executeTaskCompletesSuccessfully() {
        AgentTaskDO task = new AgentTaskDO();
        task.setId(1L);
        task.setTaskUuid("test-uuid");
        task.setAgentId(100L);
        task.setInputText("hello");
        task.setStatus("QUEUED");
        task.setFileIds(null);

        when(agentTaskMapper.selectOne(any())).thenReturn(task);
        when(agentService.executeAgent(eq(100L), eq("hello"), any())).thenReturn("result-text");

        agentTaskService.executeTask("test-uuid", LoginContext.empty());

        // markRunning → updateById called
        // markCompleted → updateById called
        verify(agentTaskMapper, times(2)).updateById(any(AgentTaskDO.class));

        ArgumentCaptor<AgentTaskDO> captor = ArgumentCaptor.forClass(AgentTaskDO.class);
        verify(agentTaskMapper, times(2)).updateById(captor.capture());
        assertThat(captor.getAllValues().get(0).getStatus()).isEqualTo("RUNNING");
        assertThat(captor.getAllValues().get(1).getStatus()).isEqualTo("COMPLETED");
        assertThat(captor.getAllValues().get(1).getResult()).isEqualTo("result-text");
    }

    @Test
    void executeTaskHandlesFailure() {
        AgentTaskDO task = new AgentTaskDO();
        task.setId(1L);
        task.setTaskUuid("fail-uuid");
        task.setAgentId(100L);
        task.setInputText("bad input");
        task.setStatus("QUEUED");
        task.setFileIds(null);

        when(agentTaskMapper.selectOne(any())).thenReturn(task);
        when(agentService.executeAgent(eq(100L), eq("bad input"), any()))
                .thenThrow(new RuntimeException("LLM error"));

        agentTaskService.executeTask("fail-uuid", LoginContext.empty());

        verify(agentTaskMapper, times(2)).updateById(any(AgentTaskDO.class));

        ArgumentCaptor<AgentTaskDO> captor = ArgumentCaptor.forClass(AgentTaskDO.class);
        verify(agentTaskMapper, times(2)).updateById(captor.capture());
        assertThat(captor.getAllValues().get(0).getStatus()).isEqualTo("RUNNING");
        assertThat(captor.getAllValues().get(1).getStatus()).isEqualTo("FAILED");
        assertThat(captor.getAllValues().get(1).getErrorMessage()).contains("LLM error");
    }

    @Test
    void executeTaskWithFileUuidsUsesMultimodal() {
        AgentTaskDO task = new AgentTaskDO();
        task.setId(1L);
        task.setTaskUuid("multi-uuid");
        task.setAgentId(100L);
        task.setInputText("describe image");
        task.setStatus("QUEUED");
        task.setFileIds("uuid-1,uuid-2");

        when(agentTaskMapper.selectOne(any())).thenReturn(task);
        when(agentService.executeAgentMultimodal(eq(100L), eq("describe image"), any(), any()))
                .thenReturn("image description");

        agentTaskService.executeTask("multi-uuid", LoginContext.empty());

        verify(agentService).executeAgentMultimodal(eq(100L), eq("describe image"), any(), any());
        verify(agentService, never()).executeAgent(any(), any(), any());

        ArgumentCaptor<AgentTaskDO> captor = ArgumentCaptor.forClass(AgentTaskDO.class);
        verify(agentTaskMapper, times(2)).updateById(captor.capture());
        assertThat(captor.getAllValues().get(1).getStatus()).isEqualTo("COMPLETED");
    }
}
