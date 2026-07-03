package io.lumina.agent.service;

import io.lumina.agent.api.dto.ExecuteWorkflowDTO;
import io.lumina.agent.api.dto.WorkflowDTO;
import io.lumina.agent.infrastructure.entity.WorkflowDefinitionDO;
import io.lumina.agent.infrastructure.entity.WorkflowExecutionLogDO;
import io.lumina.agent.infrastructure.entity.WorkflowInstanceDO;

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
    List<WorkflowDefinitionDO> list(String name, Integer status, int pageNum, int pageSize);

    /** 获取工作流详情 */
    WorkflowDefinitionDO getById(Long id);

    /** 执行工作流 */
    WorkflowInstanceDO execute(Long definitionId, ExecuteWorkflowDTO dto);

    /** 查询实例列表 */
    List<WorkflowInstanceDO> listInstances(Long definitionId, String status, int pageNum, int pageSize);

    /** 查询实例执行日志 */
    List<WorkflowExecutionLogDO> getInstanceLogs(Long instanceId);
}
