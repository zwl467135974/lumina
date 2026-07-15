package io.lumina.agent.api.controller;

import io.lumina.agent.api.dto.CreateFromTemplateDTO;
import io.lumina.agent.api.dto.ExecuteWorkflowDTO;
import io.lumina.agent.api.dto.WorkflowDTO;
import io.lumina.agent.api.dto.WorkflowTemplateVO;
import io.lumina.agent.api.vo.WorkflowDefinitionVO;
import io.lumina.agent.api.vo.WorkflowExecutionLogVO;
import io.lumina.agent.api.vo.WorkflowInstanceVO;
import io.lumina.agent.infrastructure.entity.WorkflowDefinitionDO;
import io.lumina.agent.infrastructure.entity.WorkflowInstanceDO;
import io.lumina.agent.service.WorkflowService;
import io.lumina.common.core.PageResult;
import io.lumina.common.core.R;
import io.lumina.framework.audit.annotation.Audit;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 工作流管理 API
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
@Validated
public class WorkflowController {

    private final WorkflowService workflowService;

    @Audit(module = "workflow", action = "CREATE", description = "创建工作流")
    @PostMapping
    public R<WorkflowDefinitionVO> create(@Valid @RequestBody WorkflowDTO dto) {
        return R.success(WorkflowDefinitionVO.from(workflowService.create(dto)));
    }

    @Audit(module = "workflow", action = "UPDATE", description = "更新工作流")
    @PutMapping("/{id}")
    public R<WorkflowDefinitionVO> update(@PathVariable Long id, @Valid @RequestBody WorkflowDTO dto) {
        return R.success(WorkflowDefinitionVO.from(workflowService.update(id, dto)));
    }

    @Audit(module = "workflow", action = "UPDATE", description = "发布工作流")
    @PostMapping("/{id}/publish")
    public R<Void> publish(@PathVariable Long id) {
        workflowService.publish(id);
        return R.success();
    }

    @Audit(module = "workflow", action = "DELETE", description = "删除工作流")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        workflowService.delete(id);
        return R.success();
    }

    @GetMapping
    public R<PageResult<WorkflowDefinitionVO>> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageResult<WorkflowDefinitionDO> page = workflowService.list(name, status, pageNum, pageSize);
        List<WorkflowDefinitionVO> voList = page.getList().stream()
                .map(WorkflowDefinitionVO::from)
                .toList();
        return R.success(PageResult.of(voList, page.getTotal(), page.getPageNum(), page.getPageSize()));
    }

    @GetMapping("/{id}")
    public R<WorkflowDefinitionVO> getById(@PathVariable Long id) {
        return R.success(WorkflowDefinitionVO.from(workflowService.getById(id)));
    }

    @Audit(module = "workflow", action = "EXECUTE", description = "执行工作流")
    @PostMapping("/{id}/execute")
    public R<WorkflowInstanceVO> execute(@PathVariable Long id, @Valid @RequestBody ExecuteWorkflowDTO dto) {
        return R.success(WorkflowInstanceVO.from(workflowService.execute(id, dto)));
    }

    /**
     * 恢复暂停的工作流实例（人工审批后调用）
     */
    @Audit(module = "workflow", action = "EXECUTE", description = "恢复工作流实例")
    @PostMapping("/instances/{instanceId}/resume")
    public R<WorkflowInstanceVO> resumeInstance(
            @PathVariable Long instanceId,
            @RequestParam String decision) {
        log.info("恢复工作流实例: instanceId={}, decision={}", instanceId, decision);
        return R.success(WorkflowInstanceVO.from(workflowService.resumeInstance(instanceId, decision)));
    }

    /**
     * 流式执行工作流（SSE 推送节点执行进度）
     *
     * <p>每个 SSE 事件的 event 字段为事件类型（NODE_STARTED / NODE_COMPLETED / NODE_FAILED / WORKFLOW_COMPLETED / WORKFLOW_FAILED），
     * data 字段为事件 JSON。
     */
    @Audit(module = "workflow", action = "EXECUTE", description = "流式执行工作流")
    @PostMapping(value = "/{id}/execute/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Map<String, Object>>> executeStream(
            @PathVariable Long id,
            @Valid @RequestBody ExecuteWorkflowDTO dto) {
        log.info("流式执行工作流: definitionId={}", id);
        return workflowService.executeStream(id, dto)
                .map(event -> ServerSentEvent.<Map<String, Object>>builder()
                        .event((String) event.getOrDefault("event", "UPDATE"))
                        .data(event)
                        .build());
    }

    @GetMapping("/instances")
    public R<PageResult<WorkflowInstanceVO>> listInstances(
            @RequestParam(required = false) Long definitionId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageResult<WorkflowInstanceDO> page = workflowService.listInstances(definitionId, status, pageNum, pageSize);
        List<WorkflowInstanceVO> voList = page.getList().stream()
                .map(WorkflowInstanceVO::from)
                .toList();
        return R.success(PageResult.of(voList, page.getTotal(), page.getPageNum(), page.getPageSize()));
    }

    @GetMapping("/instances/{instanceId}/logs")
    public R<List<WorkflowExecutionLogVO>> getInstanceLogs(@PathVariable Long instanceId) {
        return R.success(workflowService.getInstanceLogs(instanceId).stream()
                .map(WorkflowExecutionLogVO::from)
                .toList());
    }

    /**
     * 获取内置工作流模板列表
     */
    @GetMapping("/templates")
    public R<List<WorkflowTemplateVO>> getTemplates() {
        return R.success(workflowService.getTemplates());
    }

    /**
     * 从模板一键创建工作流
     *
     * <p>传入模板名 + Agent 映射，自动替换占位符并创建发布。
     *
     * @since 3.3.0
     */
    @Audit(module = "workflow", action = "CREATE", description = "从模板创建工作流")
    @PostMapping("/from-template")
    public R<WorkflowDefinitionVO> createFromTemplate(@Valid @RequestBody CreateFromTemplateDTO dto) {
        return R.success(WorkflowDefinitionVO.from(
                workflowService.createFromTemplate(dto.getTemplateName(), dto.getWorkflowName(), dto.getAgentMapping())));
    }
}
