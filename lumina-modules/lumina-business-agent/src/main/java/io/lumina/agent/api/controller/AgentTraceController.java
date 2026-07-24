package io.lumina.agent.api.controller;

import io.lumina.agent.api.vo.AgentTraceVO;
import io.lumina.agent.service.AgentTraceService;
import io.lumina.common.annotation.RequirePermission;
import io.lumina.common.core.PageResult;
import io.lumina.common.core.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Agent 推理链追踪 Controller
 *
 * @author Lumina Team
 * @since 3.7.0
 */
@Slf4j
@RestController
@RequirePermission("agent:trace")
@RequestMapping("/api/v1/agent-traces")
@RequiredArgsConstructor
@Validated
@Tag(name = "推理链追踪", description = "Agent 执行推理链 Trace 查询")
public class AgentTraceController {

    private final AgentTraceService agentTraceService;

    @GetMapping
    @Operation(summary = "分页查询推理链列表")
    public R<PageResult<AgentTraceVO>> list(
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.success(agentTraceService.list(agentId, status, pageNum, pageSize));
    }

    @GetMapping("/{traceUuid}")
    @Operation(summary = "查询推理链详情（含完整步骤）")
    public R<AgentTraceVO> getByUuid(@PathVariable String traceUuid) {
        return R.success(agentTraceService.getByUuid(traceUuid));
    }
}
