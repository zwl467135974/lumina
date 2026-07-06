package io.lumina.agent.api.controller;

import io.lumina.agent.api.dto.ExecuteWorkflowDTO;
import io.lumina.agent.api.dto.WorkflowDTO;
import io.lumina.agent.api.dto.WorkflowTemplateVO;
import io.lumina.agent.infrastructure.entity.WorkflowDefinitionDO;
import io.lumina.agent.infrastructure.entity.WorkflowExecutionLogDO;
import io.lumina.agent.infrastructure.entity.WorkflowInstanceDO;
import io.lumina.agent.service.WorkflowService;
import io.lumina.common.core.PageResult;
import io.lumina.common.core.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
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
public class WorkflowController {

    private final WorkflowService workflowService;

    @PostMapping
    public R<WorkflowDefinitionDO> create(@Valid @RequestBody WorkflowDTO dto) {
        return R.success(workflowService.create(dto));
    }

    @PutMapping("/{id}")
    public R<WorkflowDefinitionDO> update(@PathVariable Long id, @Valid @RequestBody WorkflowDTO dto) {
        return R.success(workflowService.update(id, dto));
    }

    @PostMapping("/{id}/publish")
    public R<Void> publish(@PathVariable Long id) {
        workflowService.publish(id);
        return R.success();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        workflowService.delete(id);
        return R.success();
    }

    @GetMapping
    public R<List<WorkflowDefinitionDO>> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.success(workflowService.list(name, status, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<WorkflowDefinitionDO> getById(@PathVariable Long id) {
        return R.success(workflowService.getById(id));
    }

    @PostMapping("/{id}/execute")
    public R<WorkflowInstanceDO> execute(@PathVariable Long id, @RequestBody ExecuteWorkflowDTO dto) {
        return R.success(workflowService.execute(id, dto));
    }

    /**
     * 流式执行工作流（SSE 推送节点执行进度）
     *
     * <p>每个 SSE 事件的 event 字段为事件类型（NODE_STARTED / NODE_COMPLETED / NODE_FAILED / WORKFLOW_COMPLETED / WORKFLOW_FAILED），
     * data 字段为事件 JSON。
     */
    @PostMapping(value = "/{id}/execute/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Map<String, Object>>> executeStream(
            @PathVariable Long id,
            @RequestBody ExecuteWorkflowDTO dto) {
        log.info("流式执行工作流: definitionId={}", id);
        return workflowService.executeStream(id, dto)
                .map(event -> ServerSentEvent.<Map<String, Object>>builder()
                        .event((String) event.getOrDefault("event", "UPDATE"))
                        .data(event)
                        .build());
    }

    @GetMapping("/instances")
    public R<List<WorkflowInstanceDO>> listInstances(
            @RequestParam(required = false) Long definitionId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.success(workflowService.listInstances(definitionId, status, pageNum, pageSize));
    }

    @GetMapping("/instances/{instanceId}/logs")
    public R<List<WorkflowExecutionLogDO>> getInstanceLogs(@PathVariable Long instanceId) {
        return R.success(workflowService.getInstanceLogs(instanceId));
    }

    /**
     * 获取内置工作流模板列表
     */
    @GetMapping("/templates")
    public R<List<WorkflowTemplateVO>> getTemplates() {
        return R.success(workflowService.getTemplates());
    }
}
