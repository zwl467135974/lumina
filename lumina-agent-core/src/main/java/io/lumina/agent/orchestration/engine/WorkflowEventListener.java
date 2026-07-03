package io.lumina.agent.orchestration.engine;

import io.lumina.agent.orchestration.model.WorkflowContext;

/**
 * 工作流事件监听器
 *
 * <p>引擎在节点执行的关键节点回调此接口，用于 SSE 推送、日志持久化、指标埋点。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
public interface WorkflowEventListener {

    /** 节点开始执行 */
    default void onNodeStarted(String nodeId, String nodeName, WorkflowContext ctx) {}

    /** 节点执行完成 */
    default void onNodeCompleted(String nodeId, Object result, long durationMs) {}

    /** 节点执行失败 */
    default void onNodeFailed(String nodeId, Throwable error) {}

    /** 节点被跳过 */
    default void onNodeSkipped(String nodeId) {}

    /** 工作流执行完成 */
    default void onWorkflowCompleted(WorkflowContext ctx) {}

    /** 工作流执行失败 */
    default void onWorkflowFailed(WorkflowContext ctx, String error) {}
}
