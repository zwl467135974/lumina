package io.lumina.agent.model;

/**
 * 流式输出块
 *
 * <p>Agent 流式执行时下发给前端的事件单元，对应 AgentScope 的 {@code Event}。
 *
 * @param type    事件类型（如 REASONING_CHUNK 推理片段、ACTING_CHUNK 行动片段、FINAL 最终结果、ERROR 错误）
 * @param content 本次片段的文本内容
 * @param last    是否为最后一个片段
 * @author Lumina Team
 * @since 1.0.0
 */
public record StreamChunk(
        String type,
        String content,
        boolean last
) {
}
