package io.lumina.agent.orchestration.model;

/**
 * 自主编排报告事件（v3.14 批次 3.3）
 *
 * <p>编排脚本经第 6 个桥接函数 {@code artifact.report(payload)} 发布结构化报告
 *（chart / table / metrics 三类），经 {@link WorkflowContext#getAutonomyReportNotifier()}
 * 透出给引擎监听器：SSE 实时推送 + 执行日志 REPORT 行（面板数据不进模型上下文，
 * 仅呈现——模型如需数据应经脚本返回值）。
 *
 * @author Lumina Team
 * @since 3.14.0
 */
public record AutonomyReportEvent(
        String nodeId,
        String type,
        String title,
        String json,
        int seq,
        long timestampMs) {

    /** 支持的报告类型 */
    public static final String TYPE_CHART = "chart";
    public static final String TYPE_TABLE = "table";
    public static final String TYPE_METRICS = "metrics";

    public static boolean isValidType(String type) {
        return TYPE_CHART.equals(type) || TYPE_TABLE.equals(type) || TYPE_METRICS.equals(type);
    }
}
