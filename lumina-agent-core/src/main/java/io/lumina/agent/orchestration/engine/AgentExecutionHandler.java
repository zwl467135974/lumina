package io.lumina.agent.orchestration.engine;

/**
 * Agent 执行处理器（由业务模块实现）
 *
 * <p>编排引擎通过此接口委托 Agent 执行，避免引擎直接依赖业务模块。
 * 业务模块（{@code lumina-business-agent}）提供实现，桥接到 {@code AgentService}。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
public interface AgentExecutionHandler {

    /**
     * 同步执行 Agent
     *
     * @param agentId         Agent 数据库 ID
     * @param task            任务描述
     * @param conversationUuid 会话 UUID（可选，支持多轮上下文）
     * @return Agent 执行结果文本
     */
    String executeAgent(Long agentId, String task, String conversationUuid);
}
