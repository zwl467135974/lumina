package io.lumina.agent.engine;

import io.lumina.agent.model.AgentConfig;
import io.lumina.agent.model.ExecuteResult;
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
     * 获取引擎名称
     */
    String getEngineName();
}
