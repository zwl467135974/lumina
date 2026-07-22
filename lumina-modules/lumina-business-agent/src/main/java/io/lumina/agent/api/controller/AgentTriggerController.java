package io.lumina.agent.api.controller;

import io.lumina.common.annotation.RequirePermission;
import io.lumina.agent.api.dto.CreateAgentTriggerDTO;
import io.lumina.agent.api.vo.AgentTriggerVO;
import io.lumina.agent.service.AgentTriggerService;
import io.lumina.common.core.PageResult;
import io.lumina.common.core.R;
import io.lumina.framework.audit.annotation.Audit;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Agent 定时触发器 Controller
 *
 * @author Lumina Team
 * @since 3.5.0
 */
@Slf4j
@RestController
@RequirePermission("agent:trigger")
@RequestMapping("/api/v1/agents/triggers")
@Validated
@RequiredArgsConstructor
public class AgentTriggerController {

    private final AgentTriggerService agentTriggerService;

    /**
     * 创建定时触发器
     */
    @PostMapping
    @Audit(module = "agent_trigger", action = "CREATE", description = "创建定时触发器")
    public R<AgentTriggerVO> createTrigger(@Valid @RequestBody CreateAgentTriggerDTO dto) {
        log.info("创建定时触发器: name={}, agentId={}, cron={}", dto.getName(), dto.getAgentId(), dto.getCronExpr());
        return R.success(agentTriggerService.createTrigger(dto));
    }

    /**
     * 分页查询当前租户的触发器列表
     */
    @GetMapping
    public R<PageResult<AgentTriggerVO>> pageTriggers(
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        return R.success(agentTriggerService.pageTriggers(pageNum, pageSize));
    }

    /**
     * 查询触发器详情
     */
    @GetMapping("/{id}")
    public R<AgentTriggerVO> getTrigger(@PathVariable("id") Long id) {
        return R.success(agentTriggerService.getTrigger(id));
    }

    /**
     * 删除触发器
     */
    @DeleteMapping("/{id}")
    @Audit(module = "agent_trigger", action = "DELETE", description = "删除定时触发器", targetIdParam = "id")
    public R<Void> deleteTrigger(@PathVariable("id") Long id) {
        log.info("删除定时触发器: id={}", id);
        agentTriggerService.deleteTrigger(id);
        return R.success();
    }

    /**
     * 暂停触发器
     */
    @PutMapping("/{id}/pause")
    @Audit(module = "agent_trigger", action = "UPDATE", description = "暂停定时触发器", targetIdParam = "id")
    public R<Void> pause(@PathVariable("id") Long id) {
        log.info("暂停定时触发器: id={}", id);
        agentTriggerService.pause(id);
        return R.success();
    }

    /**
     * 恢复触发器（重算下次触发时间）
     */
    @PutMapping("/{id}/resume")
    @Audit(module = "agent_trigger", action = "UPDATE", description = "恢复定时触发器", targetIdParam = "id")
    public R<Void> resume(@PathVariable("id") Long id) {
        log.info("恢复定时触发器: id={}", id);
        agentTriggerService.resume(id);
        return R.success();
    }

    /**
     * 手动立即触发一次
     */
    @PostMapping("/{id}/trigger-now")
    @Audit(module = "agent_trigger", action = "EXECUTE", description = "手动触发定时触发器", targetIdParam = "id")
    public R<Map<String, Boolean>> triggerNow(@PathVariable("id") Long id) {
        log.info("手动触发定时触发器: id={}", id);
        return R.success(Map.of("submitted", agentTriggerService.triggerNow(id)));
    }
}
