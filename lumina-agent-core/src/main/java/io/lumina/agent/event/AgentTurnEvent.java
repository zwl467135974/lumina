package io.lumina.agent.event;

import io.lumina.agent.model.ExecuteResult;
import org.springframework.lang.Nullable;

/**
 * Agent 轮次执行事件（观测事件总线）
 *
 * <p>引擎在轮次生命周期的关键点发布事件，观测需求（指标/审计/计费/告警）
 * 作为消费者订阅，不再侵入引擎代码。事件是观测副本而非事实源——
 * 计费仍以 LLM 返回的 usage 落库为准。
 *
 * <p>阶段语义（借鉴 DeepSeek Harness 的 turn 终态词汇）：
 * <ul>
 *   <li>{@link Phase#STARTED} 轮次开始（配置已解析）</li>
 *   <li>{@link Phase#COMPLETED} 正常完成</li>
 *   <li>{@link Phase#FAILED} 执行失败</li>
 *   <li>{@link Phase#INTERRUPTED} 中断（流式取消/错误导致的合成闭合）——
 *       与 FAILED 的区别：已有部分输出，副作用未知</li>
 * </ul>
 *
 * @author Lumina Team
 * @since 3.11.0
 */
public record AgentTurnEvent(
        Phase phase,
        boolean streaming,
        String businessType,
        Long agentId,
        String agentName,
        String conversationId,
        String taskUuid,
        Long durationMs,
        Long totalTokens,
        String errorMessage) {

    public enum Phase {
        STARTED, COMPLETED, FAILED, INTERRUPTED
    }

    public static AgentTurnEvent started(String businessType, Long agentId, String agentName,
                                         String conversationId, boolean streaming) {
        return new AgentTurnEvent(Phase.STARTED, streaming, businessType, agentId, agentName,
                conversationId, currentTaskUuid(), null, null, null);
    }

    public static AgentTurnEvent completed(String businessType, Long agentId, String agentName,
                                           String conversationId, boolean streaming,
                                           long durationMs, @Nullable ExecuteResult.TokenUsage usage) {
        return new AgentTurnEvent(Phase.COMPLETED, streaming, businessType, agentId, agentName,
                conversationId, currentTaskUuid(), durationMs, totalTokensOf(usage), null);
    }

    public static AgentTurnEvent failed(String businessType, Long agentId, String agentName,
                                        String conversationId, boolean streaming,
                                        long durationMs, String errorMessage) {
        return new AgentTurnEvent(Phase.FAILED, streaming, businessType, agentId, agentName,
                conversationId, currentTaskUuid(), durationMs, null, errorMessage);
    }

    public static AgentTurnEvent interrupted(String businessType, Long agentId, String agentName,
                                             String conversationId, boolean streaming,
                                             String errorMessage) {
        return new AgentTurnEvent(Phase.INTERRUPTED, streaming, businessType, agentId, agentName,
                conversationId, currentTaskUuid(), null, null, errorMessage);
    }

    private static String currentTaskUuid() {
        return io.lumina.common.core.BaseContext.getTaskUuid();
    }

    private static Long totalTokensOf(@Nullable ExecuteResult.TokenUsage usage) {
        return usage != null && usage.getTotalTokens() != null ? usage.getTotalTokens().longValue() : null;
    }
}
