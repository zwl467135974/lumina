package io.lumina.agent.tracing;

/**
 * 单次工具调用使用记录（供落库聚合分析）
 *
 * <p>与 TraceStep 的分工：TraceStep 是调试细节（入参/出参截断 500 字符），
 * 本记录是聚合统计的最小事实（工具名/成败/耗时/字符量），append-only。
 *
 * @author Lumina Team
 * @since 3.12.0
 */
public class ToolUsageRecord {

    private final Long agentId;
    private final String agentName;
    private final Long tenantId;
    private final String toolName;
    private final boolean success;
    private final long durationMs;
    private final int inputChars;
    private final int resultChars;
    private final long occurredAt;

    public ToolUsageRecord(Long agentId, String agentName, Long tenantId, String toolName,
                           boolean success, long durationMs, int inputChars, int resultChars, long occurredAt) {
        this.agentId = agentId;
        this.agentName = agentName;
        this.tenantId = tenantId;
        this.toolName = toolName;
        this.success = success;
        this.durationMs = durationMs;
        this.inputChars = inputChars;
        this.resultChars = resultChars;
        this.occurredAt = occurredAt;
    }

    public Long getAgentId() { return agentId; }
    public String getAgentName() { return agentName; }
    public Long getTenantId() { return tenantId; }
    public String getToolName() { return toolName; }
    public boolean isSuccess() { return success; }
    public long getDurationMs() { return durationMs; }
    public int getInputChars() { return inputChars; }
    public int getResultChars() { return resultChars; }
    public long getOccurredAt() { return occurredAt; }
}
