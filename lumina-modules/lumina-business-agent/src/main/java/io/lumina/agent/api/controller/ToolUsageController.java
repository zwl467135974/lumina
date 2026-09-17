package io.lumina.agent.api.controller;

import io.lumina.agent.api.vo.AgentToolUsageVO;
import io.lumina.agent.service.ToolUsageAnalyticsService;
import io.lumina.common.annotation.RequirePermission;
import io.lumina.common.core.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工具使用分析 API（生产数据驱动的工具集蒸馏）
 *
 * <p>数据源为 lumina_tool_usage 明细（V54，TraceCollector 异步落库），
 * 自启用起累计；unusedTools 为窗口内零调用的已配置工具。
 *
 * @author Lumina Team
 * @since 3.12.0
 */
@Slf4j
@Tag(name = "工具使用分析", description = "按 Agent 聚合工具调用量/成功率/耗时，推荐可精简的未用工具")
@RestController
@RequirePermission("agent:list")
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
@Validated
public class ToolUsageController {

    private final ToolUsageAnalyticsService toolUsageAnalyticsService;

    @Operation(summary = "Agent 工具使用分析（days 默认 30）")
    @GetMapping("/{agentId}/tool-usage")
    public R<AgentToolUsageVO> getToolUsage(
            @PathVariable("agentId") Long agentId,
            @RequestParam(defaultValue = "30") int days) {
        return R.success(toolUsageAnalyticsService.getAgentToolUsage(agentId, days));
    }
}
