package io.lumina.agent.service;

import io.lumina.agent.api.vo.AgentToolUsageVO;

/**
 * 工具使用分析服务（生产数据驱动的工具集蒸馏）
 *
 * @author Lumina Team
 * @since 3.12.0
 */
public interface ToolUsageAnalyticsService {

    /**
     * 按时间窗聚合指定 Agent 的工具使用统计，并给出未用工具（蒸馏候选）
     *
     * @param agentId Agent ID（当前租户内校验）
     * @param days    统计时间窗（天，1-365，默认 30）
     */
    AgentToolUsageVO getAgentToolUsage(Long agentId, int days);
}
