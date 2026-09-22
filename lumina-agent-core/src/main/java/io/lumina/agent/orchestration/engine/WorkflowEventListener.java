package io.lumina.agent.orchestration.engine;

import io.lumina.agent.orchestration.model.AutonomyPhaseEvent;
import io.lumina.agent.orchestration.model.AutonomyReportEvent;
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

    /**
     * 自主编排节点内脚本声明了阶段（{@code phase(title)}）
     *
     * <p>纯展示性进度事件：同一节点执行内可多次触发，序号递增，
     * 观察者异常不会中断脚本执行。
     *
     * @since 3.13.0
     */
    default void onAutonomyPhase(AutonomyPhaseEvent event) {}

    /**
     * 自主编排节点内脚本发布了结构化报告（{@code artifact.report(payload)}）
     *
     * <p>chart / table / metrics 三类，payload 为物化后的 JSON 字符串；
     * 报告仅呈现（面板数据不进模型上下文），观察者异常不会中断脚本执行。
     *
     * @since 3.14.0
     */
    default void onAutonomyReport(AutonomyReportEvent event) {}

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
