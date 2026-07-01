package io.lumina.agent.monitor;

/**
 * 工具调用记录
 *
 * <p>记录单次工具调用的入参、出参、耗时与执行状态，用于可观测与统计。
 *
 * @author Lumina Team
 * @since 1.1.0
 */
public record ToolInvocationRecord(
        /** 工具名称 */
        String toolName,
        /** 工具分类 */
        String category,
        /** 入参 JSON */
        String input,
        /** 出参（成功时） */
        String output,
        /** 错误信息（失败时） */
        String error,
        /** 耗时（毫秒） */
        long durationMs,
        /** 是否成功 */
        boolean success,
        /** 时间戳（毫秒） */
        long timestamp,
        /** 会话 ID（可空，用于关联会话上下文） */
        String conversationId
) {

    /**
     * 创建成功记录
     */
    public static ToolInvocationRecord success(String toolName, String category, String input,
                                               String output, long durationMs, String conversationId) {
        return new ToolInvocationRecord(toolName, category, input, output, null, durationMs,
                true, System.currentTimeMillis(), conversationId);
    }

    /**
     * 创建失败记录
     */
    public static ToolInvocationRecord failure(String toolName, String category, String input,
                                               String error, long durationMs, String conversationId) {
        return new ToolInvocationRecord(toolName, category, input, null, error, durationMs,
                false, System.currentTimeMillis(), conversationId);
    }
}
