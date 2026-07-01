package io.lumina.agent.engine;

import io.lumina.agent.model.AgentConfig;
import io.lumina.agent.model.ExecuteResult;
import io.lumina.agent.model.StreamChunk;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Agent 执行引擎接口
 *
 * <p>Agent 执行引擎是 Lumina 框架的核心组件，负责根据业务类型和任务配置执行 Agent。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
public interface AgentExecutionEngine {

    /**
     * 执行 Agent（响应式，带会话上下文）
     *
     * @param businessType    业务类型（如：customer-service、data-analysis）
     * @param task            任务描述
     * @param config          Agent 配置
     * @param conversationId  会话 ID（null 表示无会话上下文，即单轮无状态执行）
     * @return 执行结果（响应式）
     */
    Mono<ExecuteResult> execute(String businessType, String task, AgentConfig config, String conversationId);

    /**
     * 异步执行 Agent（阻塞等待结果，带会话上下文）
     *
     * @param conversationId 会话 ID（null 表示无会话上下文）
     */
    ExecuteResult executeSync(String businessType, String task, AgentConfig config, String conversationId);

    /**
     * 流式执行 Agent，逐片段返回（用于 SSE 打字机效果，带会话上下文）
     *
     * @param conversationId 会话 ID（null 表示无会话上下文）
     */
    Flux<StreamChunk> executeStream(String businessType, String task, AgentConfig config, String conversationId);

    /**
     * 获取引擎名称
     */
    String getEngineName();

    /**
     * 兼容重载：执行 Agent（无会话上下文）
     */
    default Mono<ExecuteResult> execute(String businessType, String task, AgentConfig config) {
        return execute(businessType, task, config, null);
    }

    /**
     * 兼容重载：异步执行 Agent（无会话上下文）
     */
    default ExecuteResult executeSync(String businessType, String task, AgentConfig config) {
        return executeSync(businessType, task, config, null);
    }

    /**
     * 兼容重载：流式执行 Agent（无会话上下文）
     */
    default Flux<StreamChunk> executeStream(String businessType, String task, AgentConfig config) {
        return executeStream(businessType, task, config, null);
    }
}
