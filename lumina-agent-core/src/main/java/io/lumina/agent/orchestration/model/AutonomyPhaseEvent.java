package io.lumina.agent.orchestration.model;

/**
 * 自主编排阶段事件（{@code phase(title)} 桥接函数产出）
 *
 * <p>脚本领里的 {@code phase(title)} 是纯展示性阶段声明：不改变控制流、
 * 不等待、不携带数据——只标记"接下来这段脚本做什么"。事件经
 * {@link WorkflowContext#getAutonomyPhaseNotifier()} 透出给
 * {@code WorkflowEventListener#onAutonomyPhase}（SSE 推送 / 执行日志 / 指标）。
 *
 * @param nodeId      自主编排节点 ID
 * @param title       阶段名（非空，长度不超过 120）
 * @param seq         本次节点执行内的递增序号（从 1 开始）
 * @param timestampMs 事件产生时间（epoch ms）
 * @author Lumina Team
 * @since 3.13.0
 */
public record AutonomyPhaseEvent(String nodeId, String title, int seq, long timestampMs) {
}
