package io.lumina.agent.api.vo;

import lombok.Data;

import java.util.List;

/**
 * Agent 工具使用分析 VO（生产数据驱动的工具集蒸馏）
 *
 * <p>unusedTools：Agent 已配置但时间窗内零调用的工具——仍占用上下文 token，
 * 是精简工具集的第一候选。
 *
 * @author Lumina Team
 * @since 3.12.0
 */
@Data
public class AgentToolUsageVO {

    private Long agentId;

    private String agentName;

    /** 统计时间窗（天） */
    private Integer days;

    private Long totalCalls;

    /** 按调用量降序的各工具统计 */
    private List<ToolUsageStatsVO> tools;

    /** 已配置但窗口内零调用的工具（蒸馏候选） */
    private List<String> unusedTools;
}
