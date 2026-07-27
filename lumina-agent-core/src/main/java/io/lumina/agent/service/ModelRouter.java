package io.lumina.agent.service;

import io.lumina.agent.model.AgentConfig;

/**
 * 动态模型路由（Model Router）
 *
 * <p>根据用户请求的复杂度，动态选择 LLM 模型：
 * <ul>
 *   <li>简单问题（如 "你好"、"1+1"）→ 便宜模型（GLM-4-Flash）</li>
 *   <li>复杂问题（如 "分析这段代码的架构问题"）→ 强力模型（GLM-4 / GPT-4）</li>
 * </ul>
 *
 * <p>实现方通过一次轻量 LLM 调用判断复杂度，返回对应的 LLMConfig。
 * 引擎层在 createReActAgent 之前调用，如果返回非 null 则覆盖默认配置。
 *
 * @author Lumina Team
 * @since 3.8.0
 */
public interface ModelRouter {

    /**
     * 根据任务复杂度路由到合适的模型配置
     *
     * @param task   用户任务文本
     * @param config 当前 Agent 配置（含默认 LLM 配置）
     * @return 路由后的 LLM 配置；null 表示不路由（用默认配置）
     */
    AgentConfig.LLMConfig route(String task, AgentConfig config);
}
