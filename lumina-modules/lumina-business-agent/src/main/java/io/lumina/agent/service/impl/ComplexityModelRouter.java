package io.lumina.agent.service.impl;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.lumina.agent.config.LuminaAgentProperties;
import io.lumina.agent.model.AgentConfig;
import io.lumina.agent.model.ChatModelFactory;
import io.lumina.agent.service.ModelRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 基于复杂度的动态模型路由实现
 *
 * <p>用一次轻量 LLM 调用判断用户请求的复杂度（SIMPLE / COMPLEX），
 * 简单问题用便宜模型（如 glm-4-flash），复杂问题用强力模型（如 glm-4）。
 *
 * <p>仅在 {@code lumina.agent.model-routing.enabled=true} 时生效。
 *
 * @author Lumina Team
 * @since 3.8.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "lumina.agent.model-routing", name = "enabled", havingValue = "true")
public class ComplexityModelRouter implements ModelRouter {

    private final ChatModelFactory chatModelFactory;
    private final LuminaAgentProperties agentProperties;

    private static final String COMPLEXITY_PROMPT = """
            判断以下用户请求的复杂度。只输出一个词：SIMPLE 或 COMPLEX。

            判断标准：
            - SIMPLE: 闲聊、简单事实问答、翻译短句、基础数学
            - COMPLEX: 代码分析、长文写作、多步推理、专业领域问题

            用户请求：%s

            只输出 SIMPLE 或 COMPLEX，不要输出其他内容。
            """;

    @Override
    public AgentConfig.LLMConfig route(String task, AgentConfig config) {
        if (task == null || task.isBlank()) {
            return null;
        }

        LuminaAgentProperties.ModelRoutingConfig routingConfig = agentProperties.getModelRouting();
        if (routingConfig.getSimpleModel() == null && routingConfig.getComplexModel() == null) {
            log.debug("模型路由未配置 simpleModel/complexModel，跳过");
            return null;
        }

        try {
            // 用默认模型判断复杂度
            String complexity = judgeComplexity(task);

            // 根据复杂度选择模型
            String targetModel = "COMPLEX".equalsIgnoreCase(complexity)
                    ? routingConfig.getComplexModel()
                    : routingConfig.getSimpleModel();

            if (targetModel == null) {
                log.debug("复杂度={} 但未配置对应模型，跳过", complexity);
                return null;
            }

            // 构建路由后的 LLM 配置（继承原配置，只改模型名）
            AgentConfig.LLMConfig original = config.getLlmConfig() != null
                    ? config.getLlmConfig() : buildClassifierLlmConfig();
            AgentConfig.LLMConfig routed = new AgentConfig.LLMConfig();
            routed.setModelType(original.getModelType());
            routed.setModelName(targetModel);
            routed.setApiKey(original.getApiKey());
            routed.setBaseUrl(original.getBaseUrl());
            routed.setTemperature(original.getTemperature());
            routed.setMaxTokens(original.getMaxTokens());

            log.info("模型路由: 复杂度={}, 选择模型={} (原模型={})",
                    complexity, targetModel, original.getModelName());
            return routed;

        } catch (Exception e) {
            log.warn("模型路由失败，使用默认模型: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 用轻量 LLM 调用判断复杂度
     *
     * <p>关键：判复杂度本身必须用**最便宜的模型**（simpleModel），
     * 而非默认（强力）模型——否则为了省 simpleModel 的差价反而先烧一次复杂模型的成本，
     * 路由净亏。当 simpleModel 未配置时才 fallback 到默认配置。
     */
    private String judgeComplexity(String task) {
        AgentConfig.LLMConfig llmConfig = buildClassifierLlmConfig();
        String apiKey = resolveApiKey();
        Model model = chatModelFactory.create(llmConfig, agentProperties.getLlm(), apiKey);

        ReActAgent judgeAgent = ReActAgent.builder()
                .name("ComplexityJudge")
                .sysPrompt("你是一个请求分类器。只输出 SIMPLE 或 COMPLEX。")
                .model(model)
                .toolkit(new Toolkit())
                .build();

        Msg userMsg = Msg.builder().role(MsgRole.USER)
                .textContent(String.format(COMPLEXITY_PROMPT, task))
                .build();
        Msg response = judgeAgent.call(List.of(userMsg)).block();

        return response != null && response.getTextContent() != null
                ? response.getTextContent().trim().toUpperCase() : "SIMPLE";
    }

    /**
     * 构建分类器 LLM 配置——优先用 simpleModel（最便宜），
     * 未配置时才退回默认配置。
     */
    private AgentConfig.LLMConfig buildClassifierLlmConfig() {
        LuminaAgentProperties.ModelRoutingConfig routing = agentProperties.getModelRouting();
        if (routing.getSimpleModel() != null && !routing.getSimpleModel().isBlank()) {
            AgentConfig.LLMConfig cfg = new AgentConfig.LLMConfig();
            cfg.setModelName(routing.getSimpleModel());
            return cfg;
        }
        // simpleModel 未配置才用默认（此时路由本身价值有限，仅做日志）
        return new AgentConfig.LLMConfig();
    }

    private String resolveApiKey() {
        String apiKey = agentProperties.getLlm().getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = System.getenv("LLM_API_KEY");
        }
        return apiKey;
    }
}
