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
     * 执行 Agent
     *
     * @param businessType 业务类型（如：customer-service、data-analysis）
     * @param task         任务描述
     * @param config       Agent 配置
     * @return 执行结果（响应式）
     */
    Mono<ExecuteResult> execute(String businessType, String task, AgentConfig config);

    /**
     * 异步执行 Agent（阻塞等待结果）
     *
     * @param businessType 业务类型
     * @param task         任务描述
     * @param config       Agent 配置
     * @return 执行结果
     */
    ExecuteResult executeSync(String businessType, String task, AgentConfig config);

    /**
     * 流式执行 Agent，逐片段返回（用于 SSE 打字机效果）
     *
     * <p>片段类型包含推理片段（REASONING_CHUNK）、行动片段（ACTING_CHUNK）、
     * 最终结果（FINAL）、错误（ERROR）等，前端可据此渲染思考链与工具调用过程。
     *
     * @param businessType 业务类型
     * @param task         任务描述
     * @param config       Agent 配置
     * @return 流式片段流
     */
    Flux<StreamChunk> executeStream(String businessType, String task, AgentConfig config);

    /**
     * 获取引擎名称
     */
    String getEngineName();
}
