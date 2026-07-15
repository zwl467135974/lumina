package io.lumina.agent.service;

import io.lumina.agent.api.dto.ExecuteWorkflowDTO;
import io.lumina.agent.api.dto.WorkflowDTO;
import io.lumina.agent.api.dto.WorkflowTemplateVO;
import io.lumina.agent.infrastructure.entity.WorkflowDefinitionDO;
import io.lumina.agent.infrastructure.entity.WorkflowExecutionLogDO;
import io.lumina.agent.infrastructure.entity.WorkflowInstanceDO;
import io.lumina.common.core.PageResult;

import java.util.List;

/**
 * 工作流管理服务
 *
 * @author Lumina Team
 * @since 2.0.0
 */
public interface WorkflowService {

    /** 创建工作流 */
    WorkflowDefinitionDO create(WorkflowDTO dto);

    /** 更新工作流 */
    WorkflowDefinitionDO update(Long id, WorkflowDTO dto);

    /** 发布工作流（草稿 → 已发布） */
    void publish(Long id);

    /** 删除工作流（软删除） */
    void delete(Long id);

    /** 查询工作流列表 */
    PageResult<WorkflowDefinitionDO> list(String name, Integer status, int pageNum, int pageSize);

    /** 获取工作流详情 */
    WorkflowDefinitionDO getById(Long id);

    /** 执行工作流 */
    WorkflowInstanceDO execute(Long definitionId, ExecuteWorkflowDTO dto);

    /** 恢复暂停的工作流实例（人工审批后调用） */
    WorkflowInstanceDO resumeInstance(Long instanceId, String decision);

    /**
     * 流式执行工作流（SSE 推送节点执行进度）
     *
     * @return 事件流（NODE_STARTED / NODE_COMPLETED / NODE_FAILED / WORKFLOW_COMPLETED / WORKFLOW_FAILED）
     */
    reactor.core.publisher.Flux<java.util.Map<String, Object>> executeStream(Long definitionId, ExecuteWorkflowDTO dto);

    /** 查询实例列表 */
    PageResult<WorkflowInstanceDO> listInstances(Long definitionId, String status, int pageNum, int pageSize);

    /** 查询实例执行日志 */
    List<WorkflowExecutionLogDO> getInstanceLogs(Long instanceId);

    /** 获取内置工作流模板列表 */
    List<WorkflowTemplateVO> getTemplates();

    /**
     * 从模板创建工作流（一键创建）
     *
     * <p>读取模板 YAML，用 agentMapping 替换占位符（如 ${agent1} → 实际 Agent ID），
     * 然后创建并发布工作流。
     *
     * @param templateName  模板名称（如 plan-execute、group-chat）
     * @param workflowName  新工作流名称
     * @param agentMapping  占位符 → Agent ID 映射（如 {"agent1": 1, "agent2": 2}）
     * @return 创建的工作流定义
     * @since 3.3.0
     */
    WorkflowDefinitionDO createFromTemplate(String templateName, String workflowName,
                                             java.util.Map<String, Long> agentMapping);
}
