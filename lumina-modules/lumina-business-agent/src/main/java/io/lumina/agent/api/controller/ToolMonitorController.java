package io.lumina.agent.api.controller;

import io.lumina.agent.monitor.ToolCircuitBreaker;
import io.lumina.agent.monitor.ToolInvocationRecord;
import io.lumina.agent.monitor.ToolInvocationRecorder;
import io.lumina.common.core.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具监控 Controller
 *
 * <p>提供工具调用统计、调用记录与熔断器状态查询接口。
 *
 * @author Lumina Team
 * @since 1.1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/tools")
public class ToolMonitorController {

    @Autowired(required = false)
    private ToolInvocationRecorder recorder;

    @Autowired(required = false)
    private ToolCircuitBreaker circuitBreaker;

    /**
     * 查询所有工具的调用统计
     */
    @GetMapping("/stats")
    public R<Map<String, Map<String, Object>>> allStats() {
        if (recorder == null) {
            return R.success(Collections.emptyMap());
        }
        return R.success(recorder.getAllStats());
    }

    /**
     * 查询指定工具的调用统计
     */
    @GetMapping("/stats/{toolName}")
    public R<Map<String, Object>> stats(@PathVariable("toolName") String toolName) {
        Map<String, Object> stats = recorder != null ? recorder.getStatsMap(toolName) : null;
        return R.success(stats);
    }

    /**
     * 查询最近的工具调用记录
     *
     * @param limit 返回条数（默认 50）
     */
    @GetMapping("/invocations")
    public R<List<ToolInvocationRecord>> invocations(
            @RequestParam(defaultValue = "50") int limit) {
        if (recorder == null) {
            return R.success(Collections.emptyList());
        }
        return R.success(recorder.getRecent(limit));
    }

    /**
     * 查询熔断器状态
     */
    @GetMapping("/breakers")
    public R<Map<String, Map<String, Object>>> breakers() {
        if (circuitBreaker == null) {
            return R.success(Collections.emptyMap());
        }
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        circuitBreaker.getBreakerStates().forEach((name, state) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("toolName", name);
            m.put("open", state.isOpen());
            m.put("consecutiveFailures", state.getConsecutiveFailures().get());
            m.put("openedAt", state.getOpenedAt());
            result.put(name, m);
        });
        return R.success(result);
    }

    /**
     * 清空调用记录与统计
     */
    @DeleteMapping("/invocations")
    public R<Void> clear() {
        if (recorder != null) {
            recorder.clear();
        }
        return R.success();
    }
}
