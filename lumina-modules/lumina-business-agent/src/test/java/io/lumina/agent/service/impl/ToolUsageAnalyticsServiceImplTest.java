package io.lumina.agent.service.impl;

import io.lumina.agent.api.vo.AgentToolUsageVO;
import io.lumina.agent.api.vo.ToolUsageStatsVO;
import io.lumina.agent.domain.model.Agent;
import io.lumina.agent.infrastructure.mapper.ToolUsageMapper;
import io.lumina.agent.service.AgentService;
import io.lumina.common.core.BaseContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/**
 * ToolUsageAnalyticsServiceImpl 单元测试（成功率派生、未用工具蒸馏、窗口收敛）
 *
 * @author Lumina Team
 * @since 3.12.0
 */
class ToolUsageAnalyticsServiceImplTest {

    private final ToolUsageMapper toolUsageMapper = Mockito.mock(ToolUsageMapper.class);

    private final AgentService agentService = Mockito.mock(AgentService.class);

    private final ToolUsageAnalyticsServiceImpl service =
            new ToolUsageAnalyticsServiceImpl(toolUsageMapper, agentService);

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    @Test
    void computesSuccessRateAndTotalCalls() {
        BaseContext.setTenantId(7L);
        Mockito.when(agentService.getAgentById(3L)).thenReturn(agent("search-api,code-interpreter"));
        ToolUsageStatsVO search = stat("search-api", 10L, 9L);
        ToolUsageStatsVO interpreter = stat("code-interpreter", 4L, 2L);
        Mockito.when(toolUsageMapper.aggregateByTool(eq(7L), eq(3L), any()))
                .thenReturn(List.of(search, interpreter));

        AgentToolUsageVO vo = service.getAgentToolUsage(3L, 30);

        assertThat(vo.getTotalCalls()).isEqualTo(14L);
        assertThat(vo.getTools()).hasSize(2);
        assertThat(vo.getTools().get(0).getSuccessRate()).isEqualTo(90.0);
        assertThat(vo.getTools().get(1).getSuccessRate()).isEqualTo(50.0);
        assertThat(vo.getUnusedTools()).isEmpty();
    }

    @Test
    void detectsUnusedConfiguredTools() {
        BaseContext.setTenantId(7L);
        Mockito.when(agentService.getAgentById(3L)).thenReturn(agent("search-api,code-interpreter,util.loadSkill"));
        Mockito.when(toolUsageMapper.aggregateByTool(eq(7L), eq(3L), any()))
                .thenReturn(List.of(stat("search-api", 5L, 5L)));

        AgentToolUsageVO vo = service.getAgentToolUsage(3L, 30);

        assertThat(vo.getUnusedTools()).containsExactly("code-interpreter", "util.loadSkill");
    }

    @Test
    void clampsDaysWindow() {
        BaseContext.setTenantId(7L);
        Mockito.when(agentService.getAgentById(3L)).thenReturn(agent("search-api"));
        Mockito.when(toolUsageMapper.aggregateByTool(eq(7L), eq(3L), any())).thenReturn(List.of());

        assertThat(service.getAgentToolUsage(3L, 0).getDays()).isEqualTo(1);
        assertThat(service.getAgentToolUsage(3L, 9999).getDays()).isEqualTo(365);
    }

    @Test
    void zeroCallsYieldNullSuccessRate() {
        BaseContext.setTenantId(7L);
        Mockito.when(agentService.getAgentById(3L)).thenReturn(agent("search-api"));
        ToolUsageStatsVO zero = stat("search-api", 0L, 0L);
        Mockito.when(toolUsageMapper.aggregateByTool(eq(7L), eq(3L), any())).thenReturn(List.of(zero));

        AgentToolUsageVO vo = service.getAgentToolUsage(3L, 30);

        assertThat(vo.getTools().get(0).getSuccessRate()).isNull();
        // 0 次调用也视为未用工具
        assertThat(vo.getUnusedTools()).isEmpty();
    }

    private Agent agent(String tools) {
        Agent agent = new Agent();
        agent.setAgentId(3L);
        agent.setAgentName("客服助手");
        agent.setStatus(1);
        agent.setTools(tools);
        return agent;
    }

    private ToolUsageStatsVO stat(String toolName, Long calls, Long successCalls) {
        ToolUsageStatsVO vo = new ToolUsageStatsVO();
        vo.setToolName(toolName);
        vo.setCalls(calls);
        vo.setSuccessCalls(successCalls);
        vo.setAvgDurationMs(120.0);
        vo.setMaxDurationMs(900L);
        vo.setAvgResultChars(800.0);
        return vo;
    }
}
