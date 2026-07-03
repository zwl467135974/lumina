package io.lumina.agent.orchestration.model;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 工作流运行时上下文
 *
 * <p>贯穿一次工作流执行的全生命周期，持有变量空间、节点结果和状态信息。
 * 所有 SpEL 表达式都以此上下文为求值根对象。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Data
public class WorkflowContext {

    /** 工作流实例 ID（数据库主键） */
    private Long instanceId;

    /** 工作流定义名称 */
    private String workflowName;

    /** 租户 ID */
    private Long tenantId;

    /** 操作人 ID */
    private Long userId;

    /** 全局变量空间（节点间数据传递的载体） */
    private Map<String, Object> variables = new HashMap<>();

    /** 各节点的执行结果（key = nodeId） */
    private Map<String, Object> nodeResults = new HashMap<>();

    /** 各节点的执行状态 */
    private Map<String, WorkflowNodeStatus> nodeStatuses = new HashMap<>();

    /** 当前执行节点 ID */
    private String currentNodeId;

    /** 工作流整体状态 */
    private WorkflowStatus status = WorkflowStatus.PENDING;

    /** 错误信息（失败时） */
    private String errorMessage;

    /**
     * 向变量空间存入值
     */
    public void setVariable(String key, Object value) {
        variables.put(key, value);
    }

    /**
     * 从变量空间取值
     */
    @SuppressWarnings("unchecked")
    public <T> T getVariable(String key) {
        return (T) variables.get(key);
    }

    /**
     * 记录节点执行结果
     */
    public void setNodeResult(String nodeId, Object result) {
        nodeResults.put(nodeId, result);
    }

    /**
     * 获取节点执行结果
     */
    @SuppressWarnings("unchecked")
    public <T> T getNodeResult(String nodeId) {
        return (T) nodeResults.get(nodeId);
    }

    /**
     * 构造供 SpEL 求值的根对象（变量空间 + 节点结果合并）
     */
    public Map<String, Object> toEvaluationRoot() {
        Map<String, Object> root = new HashMap<>(variables);
        root.putAll(nodeResults);
        return root;
    }
}
