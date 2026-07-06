package io.lumina.agent.orchestration.engine;

import io.lumina.agent.orchestration.model.WorkflowContext;
import io.lumina.agent.orchestration.model.WorkflowDefinition;

import java.util.Map;

/**
 * 工作流引擎接口
 *
 * <p>负责解析工作流定义，按拓扑顺序执行节点，管理上下文变量传递。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
public interface WorkflowEngine {

    /**
     * 执行工作流
     *
     * @param definition 工作流定义
     * @param inputs     输入参数
     * @return 执行上下文（含变量空间、节点结果、最终状态）
     */
    WorkflowContext execute(WorkflowDefinition definition, Map<String, Object> inputs);

    /**
     * 恢复暂停的工作流（人工审批后调用）
     *
     * @param definition 工作流定义
     * @param pausedCtx  暂停时的上下文
     * @param decision   人工决策值（如 "approved" / "rejected"）
     * @return 执行上下文（含恢复后执行的结果）
     */
    WorkflowContext resume(WorkflowDefinition definition, WorkflowContext pausedCtx, String decision);

    /**
     * 注册事件监听器（用于 SSE 推送、日志记录等）
     */
    void addListener(WorkflowEventListener listener);

    /**
     * 移除事件监听器
     */
    void removeListener(WorkflowEventListener listener);
}
