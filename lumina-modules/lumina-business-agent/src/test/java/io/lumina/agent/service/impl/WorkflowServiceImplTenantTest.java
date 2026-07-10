package io.lumina.agent.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lumina.agent.infrastructure.entity.WorkflowDefinitionDO;
import io.lumina.agent.infrastructure.entity.WorkflowInstanceDO;
import io.lumina.agent.infrastructure.mapper.WorkflowDefinitionMapper;
import io.lumina.agent.infrastructure.mapper.WorkflowExecutionLogMapper;
import io.lumina.agent.infrastructure.mapper.WorkflowInstanceMapper;
import io.lumina.agent.orchestration.engine.WorkflowEngine;
import io.lumina.agent.orchestration.loader.WorkflowLoader;
import io.lumina.common.core.BaseContext;
import io.lumina.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * WorkflowServiceImpl 租户隔离单元测试
 *
 * <p>验证跨租户访问工作流定义和实例时抛出异常。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@ExtendWith(MockitoExtension.class)
class WorkflowServiceImplTenantTest {

    @InjectMocks
    private WorkflowServiceImpl service;

    @Mock
    private WorkflowDefinitionMapper definitionMapper;

    @Mock
    private WorkflowInstanceMapper instanceMapper;

    @Mock
    private WorkflowExecutionLogMapper logMapper;

    @Mock
    private WorkflowLoader workflowLoader;

    @Mock
    private WorkflowEngine workflowEngine;

    @Mock
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        BaseContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    @Test
    void getInstanceLogs_wrongTenant_throwsException() {
        WorkflowInstanceDO instance = new WorkflowInstanceDO();
        instance.setId(1L);
        instance.setTenantId(2L);
        when(instanceMapper.selectById(1L)).thenReturn(instance);

        assertThatThrownBy(() -> service.getInstanceLogs(1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void getInstanceLogs_notFound_throwsException() {
        when(instanceMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.getInstanceLogs(99L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void getById_wrongTenant_throwsException() {
        WorkflowDefinitionDO entity = new WorkflowDefinitionDO();
        entity.setId(1L);
        entity.setTenantId(2L);
        entity.setIsDeleted(0);
        when(definitionMapper.selectById(1L)).thenReturn(entity);

        assertThatThrownBy(() -> service.getById(1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void getById_deleted_throwsException() {
        WorkflowDefinitionDO entity = new WorkflowDefinitionDO();
        entity.setId(1L);
        entity.setTenantId(1L);
        entity.setIsDeleted(1);
        when(definitionMapper.selectById(1L)).thenReturn(entity);

        assertThatThrownBy(() -> service.getById(1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void getById_sameTenant_success() {
        WorkflowDefinitionDO entity = new WorkflowDefinitionDO();
        entity.setId(1L);
        entity.setTenantId(1L);
        entity.setIsDeleted(0);
        entity.setName("test-workflow");
        when(definitionMapper.selectById(1L)).thenReturn(entity);

        WorkflowDefinitionDO result = service.getById(1L);

        org.assertj.core.api.Assertions.assertThat(result).isNotNull();
        org.assertj.core.api.Assertions.assertThat(result.getName()).isEqualTo("test-workflow");
    }

    @Test
    void resumeInstance_wrongTenant_throwsException() {
        WorkflowInstanceDO instance = new WorkflowInstanceDO();
        instance.setId(1L);
        instance.setTenantId(2L);
        when(instanceMapper.selectById(1L)).thenReturn(instance);

        assertThatThrownBy(() -> service.resumeInstance(1L, "approve"))
                .isInstanceOf(BusinessException.class);
    }
}
