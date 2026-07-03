package io.lumina.agent.model;

/**
 * 流式事件类型常量
 *
 * <p>AgentScope 流式执行时下发的事件类型标识，统一管理避免硬编码字符串。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
public final class StreamEventType {

    private StreamEventType() {
    }

    /** 推理片段（思考过程） */
    public static final String REASONING_CHUNK = "REASONING_CHUNK";
    public static final String REASONING = "REASONING";
    public static final String POST_REASONING = "POST_REASONING";

    /** 行动片段（工具调用过程） */
    public static final String ACTING_CHUNK = "ACTING_CHUNK";
    public static final String ACTING = "ACTING";
    public static final String POST_ACTING = "POST_ACTING";

    /** 最终结果 */
    public static final String FINAL = "FINAL";
    public static final String AGENT_RESULT = "AGENT_RESULT";

    /** 错误 */
    public static final String ERROR = "ERROR";
}
