package io.lumina.agent.service.impl;

import io.lumina.agent.domain.model.Agent;
import io.lumina.agent.engine.AgentExecutionEngine;
import io.lumina.agent.infrastructure.entity.AgentDO;
import io.lumina.agent.infrastructure.mapper.AgentMapper;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AgentServiceImpl 缓存失效单元测试
 *
 * <p>验证 updateAgent 和 deleteAgent 操作后正确调用 evictCache。
 *
 * @author Lumina Team
 * @since 1.1.0
 */
@ExtendWith(MockitoExtension.class)
class AgentServiceImplCacheTest {

    @InjectMocks
    private AgentServiceImpl agentService;

    @Mock
    private AgentMapper agentMapper;

    @Mock
    private AgentExecutionEngine agentExecutionEngine;

    @BeforeEach
    void setUp() {
        BaseContext.setTenantId(1L);
        BaseContext.setUserId(100L);
    }

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    @Test
    void updateAgent_evictsCache() {
        AgentDO existing = new AgentDO();
        existing.setAgentId(1L);
        existing.setAgentName("test-agent");
        existing.setAgentType("assistant");
        existing.setStatus(1);
        existing.setTenantId(1L);
        when(agentMapper.selectById(1L)).thenReturn(existing);

        Agent update = new Agent();
        update.setDescription("updated description");

        agentService.updateAgent(1L, update);

        verify(agentExecutionEngine).evictCache(1L);
    }

    @Test
    void deleteAgent_evictsCache() {
        AgentDO existing = new AgentDO();
        existing.setAgentId(1L);
        existing.setTenantId(1L);
        when(agentMapper.selectById(1L)).thenReturn(existing);

        agentService.deleteAgent(1L);

        verify(agentExecutionEngine).evictCache(1L);
    }

    @Test
    void updateAgent_wrongTenant_doesNotEvictCache() {
        AgentDO existing = new AgentDO();
        existing.setAgentId(1L);
        existing.setTenantId(2L);
        when(agentMapper.selectById(1L)).thenReturn(existing);

        Agent update = new Agent();
        update.setDescription("updated");

        assertThatThrownBy(() -> agentService.updateAgent(1L, update))
                .isInstanceOf(BusinessException.class);

        verify(agentExecutionEngine, never()).evictCache(1L);
    }

    @Test
    void deleteAgent_wrongTenant_doesNotEvictCache() {
        AgentDO existing = new AgentDO();
        existing.setAgentId(1L);
        existing.setTenantId(2L);
        when(agentMapper.selectById(1L)).thenReturn(existing);

        assertThatThrownBy(() -> agentService.deleteAgent(1L))
                .isInstanceOf(BusinessException.class);

        verify(agentExecutionEngine, never()).evictCache(1L);
    }
}
