package io.lumina.agent.service.impl;

import io.lumina.agent.api.dto.AgentTaskRequestDTO;
import io.lumina.agent.domain.model.Agent;
import io.lumina.agent.infrastructure.entity.AgentTaskDO;
import io.lumina.agent.infrastructure.mapper.AgentTaskMapper;
import io.lumina.agent.model.ExecuteResult;
import io.lumina.agent.service.AgentService;
import io.lumina.agent.service.TaskProgressRegistry;
import io.lumina.common.core.BaseContext;
import io.lumina.common.core.LoginContext;
import io.lumina.common.exception.BusinessException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.lumina.common.core.PageResult;
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

    @Mock
    private TaskProgressRegistry progressRegistry;

    @Mock
    private io.lumina.agent.infrastructure.mapper.AgentMapper agentMapper;

    @Mock
    private io.lumina.agent.service.RunningTaskRegistry runningTaskRegistry;

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
        ExecuteResult execResult = ExecuteResult.success("result-text");
        when(agentService.executeAgentForResult(eq(100L), eq("hello"), any())).thenReturn(execResult);

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
        when(agentService.executeAgentForResult(eq(100L), eq("bad input"), any()))
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
        ExecuteResult multimodalResult = ExecuteResult.success("image description");
        when(agentService.executeAgentMultimodalForResult(eq(100L), eq("describe image"), any(), any()))
                .thenReturn(multimodalResult);

        agentTaskService.executeTask("multi-uuid", LoginContext.empty());

        verify(agentService).executeAgentMultimodalForResult(eq(100L), eq("describe image"), any(), any());
        verify(agentService, never()).executeAgentForResult(any(), any(), any());

        ArgumentCaptor<AgentTaskDO> captor = ArgumentCaptor.forClass(AgentTaskDO.class);
        verify(agentTaskMapper, times(2)).updateById(captor.capture());
        assertThat(captor.getAllValues().get(1).getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void cancelTaskQueuedSucceeds() {
        BaseContext.setTenantId(1L);
        AgentTaskDO task = new AgentTaskDO();
        task.setId(1L);
        task.setTaskUuid("cancel-uuid");
        task.setStatus("QUEUED");
        task.setTenantId(1L);
        task.setIsDeleted(0);
        when(agentTaskMapper.selectOne(any())).thenReturn(task);

        AgentTaskDO result = agentTaskService.cancelTask("cancel-uuid");

        assertThat(result.getStatus()).isEqualTo("CANCELLED");
        verify(agentTaskMapper).updateById(any(AgentTaskDO.class));
    }

    @Test
    void cancelTaskAlreadyCompletedThrows() {
        BaseContext.setTenantId(1L);
        AgentTaskDO task = new AgentTaskDO();
        task.setId(1L);
        task.setTaskUuid("done-uuid");
        task.setStatus("COMPLETED");
        task.setTenantId(1L);
        task.setIsDeleted(0);
        when(agentTaskMapper.selectOne(any())).thenReturn(task);

        assertThatThrownBy(() -> agentTaskService.cancelTask("done-uuid"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void pageTasksReturnsPaginatedResults() {
        BaseContext.setTenantId(1L);
        AgentTaskDO task1 = new AgentTaskDO();
        task1.setTaskUuid("t1");
        task1.setStatus("COMPLETED");
        AgentTaskDO task2 = new AgentTaskDO();
        task2.setTaskUuid("t2");
        task2.setStatus("RUNNING");

        Page<AgentTaskDO> page = new Page<>(1, 10);
        page.setRecords(java.util.List.of(task1, task2));
        page.setTotal(2);
        when(agentTaskMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<AgentTaskDO> result = agentTaskService.pageTasks(null, null, 1, 10);

        assertThat(result.getList()).hasSize(2);
        assertThat(result.getTotal()).isEqualTo(2);
    }
}
