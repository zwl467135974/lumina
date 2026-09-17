package io.lumina.agent.tracing;

/**
 * 工具使用落库出口（业务模块实现，镜像 {@link TraceSink} 模式）
 *
 * <p>agent-core 不依赖持久层；business-agent 提供实现写入 lumina_tool_usage。
 * 未注册实现时（如 base 模块测试上下文）自动跳过，不影响执行主路径。
 *
 * @author Lumina Team
 * @since 3.12.0
 */
public interface ToolUsageSink {

    /**
     * 异步线程中调用（实现需自行容错，异常不应外抛）
     */
    void record(ToolUsageRecord record);
}
