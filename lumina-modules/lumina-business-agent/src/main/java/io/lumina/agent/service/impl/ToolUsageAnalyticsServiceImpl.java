package io.lumina.agent.service.impl;

import io.lumina.agent.api.vo.AgentToolUsageVO;
import io.lumina.agent.api.vo.ToolUsageStatsVO;
import io.lumina.agent.domain.model.Agent;
import io.lumina.agent.infrastructure.mapper.ToolUsageMapper;
import io.lumina.agent.service.AgentService;
import io.lumina.agent.service.ToolUsageAnalyticsService;
import io.lumina.common.core.BaseContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工具使用分析服务实现
 *
 * <p>蒸馏逻辑（借鉴 open-code-review"从生产调用轨迹精简工具集"）：
 * SQL 只做纯聚合，成功率等派生指标在 Java 侧计算；unusedTools =
 * Agent 配置工具集 − 窗口内实际调用集——未用工具仍随每次请求进入
 * 模型上下文（token 成本），是精简的第一候选。
 *
 * @author Lumina Team
 * @since 3.12.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolUsageAnalyticsServiceImpl implements ToolUsageAnalyticsService {

    private static final int MAX_DAYS = 365;

    private final ToolUsageMapper toolUsageMapper;

    private final AgentService agentService;

    @Override
    public AgentToolUsageVO getAgentToolUsage(Long agentId, int days) {
        Agent agent = agentService.getAgentById(agentId);
        int windowDays = Math.min(Math.max(days, 1), MAX_DAYS);
        Long tenantId = BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L;

        List<ToolUsageStatsVO> stats = toolUsageMapper.aggregateByTool(
                tenantId, agentId, LocalDateTime.now().minusDays(windowDays));
        long totalCalls = 0;
        for (ToolUsageStatsVO stat : stats) {
            stat.setSuccessRate(stat.getCalls() != null && stat.getCalls() > 0 && stat.getSuccessCalls() != null
                    ? Math.round(stat.getSuccessCalls() * 1000.0 / stat.getCalls()) / 10.0
                    : null);
            if (stat.getCalls() != null) {
                totalCalls += stat.getCalls();
            }
        }

        AgentToolUsageVO vo = new AgentToolUsageVO();
        vo.setAgentId(agentId);
        vo.setAgentName(agent.getAgentName());
        vo.setDays(windowDays);
        vo.setTotalCalls(totalCalls);
        vo.setTools(stats);
        vo.setUnusedTools(unusedTools(agent.getTools(), stats));
        return vo;
    }

    /**
     * 配置工具集（逗号分隔）− 窗口内有调用的工具集
     */
    private List<String> unusedTools(String configuredTools, List<ToolUsageStatsVO> stats) {
        if (configuredTools == null || configuredTools.isBlank()) {
            return List.of();
        }
        Set<String> used = stats.stream()
                .map(ToolUsageStatsVO::getToolName)
                .collect(Collectors.toSet());
        return Arrays.stream(configuredTools.split(","))
                .map(String::trim)
                .filter(tool -> !tool.isBlank() && !used.contains(tool))
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
