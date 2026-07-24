package io.lumina.agent.tracing;

/**
 * Trace 落库接口
 *
 * <p>agent-core 定义接口，business-agent 实现持久化。
 * 解耦引擎层和数据访问层。
 *
 * @author Lumina Team
 * @since 3.7.0
 */
public interface TraceSink {

    /**
     * 保存 Trace 到持久化存储
     *
     * @param ctx Trace 上下文（含完整步骤）
     */
    void save(TraceContext ctx);
}
