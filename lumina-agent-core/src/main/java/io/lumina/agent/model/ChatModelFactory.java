package io.lumina.agent.model;

import io.agentscope.core.formatter.anthropic.AnthropicChatFormatter;
import io.agentscope.core.formatter.dashscope.DashScopeChatFormatter;
import io.agentscope.core.formatter.ollama.OllamaChatFormatter;
import io.agentscope.core.formatter.openai.OpenAIChatFormatter;
import io.agentscope.core.model.AnthropicChatModel;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.OllamaChatModel;
import io.agentscope.core.model.OpenAIChatModel;
import io.lumina.agent.config.LuminaAgentProperties;
import io.lumina.agent.model.AgentConfig.LLMConfig;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.exception.SystemException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 聊天模型工厂
 *
 * <p>按 {@link LLMConfig#getModelType()} 创建对应的 AgentScope {@link Model}，
 * 支持 DashScope / OpenAI / Anthropic / Ollama 四类模型。
 *
 * <p>新增模型只需在 {@link #create} 的 switch 中追加分支。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class ChatModelFactory {

    /**
     * 创建聊天模型
     *
     * @param config        LLM 配置（来自 Agent 或默认）
     * @param defaults      全局默认 LLM 配置
     * @param defaultApiKey 默认 API Key（config 未提供时使用）
     * @return AgentScope Model 实例
     */
    public Model create(LLMConfig config, LuminaAgentProperties.LLMConfig defaults, String defaultApiKey) {
        String type = (config.getModelType() != null ? config.getModelType() : defaults.getType()).toLowerCase();
        String apiKey = config.getApiKey() != null ? config.getApiKey() : defaultApiKey;
        String modelName = config.getModelName() != null ? config.getModelName() : defaults.getModel();

        Model model;
        switch (type) {
            case "dashscope":
                model = createDashScope(config, defaults, apiKey, modelName);
                break;
            case "openai":
                model = createOpenAI(config, defaults, apiKey, modelName);
                break;
            case "anthropic":
            case "claude":
                model = createAnthropic(config, defaults, apiKey, modelName);
                break;
            case "ollama":
                model = createOllama(config, modelName);
                break;
            default:
                if (ProviderPresets.isOpenAICompatible(type)) {
                    model = createOpenAICompatiblePreset(type, config, defaults, apiKey, modelName);
                    break;
                }
                throw new SystemException(ErrorCode.AGENT_CONFIG_ERROR, "不支持的模型类型: " + type
                    + "。支持: dashscope/openai/anthropic/ollama/glm/kimi/doubao/minimax/deepseek/yi/qwen");
        }

        log.info("创建聊天模型: type={}, model={}", type, modelName);
        return model;
    }

    /**
     * DashScope（通义千问）
     */
    private Model createDashScope(LLMConfig config, LuminaAgentProperties.LLMConfig defaults,
                                  String apiKey, String modelName) {
        DashScopeChatModel.Builder builder = DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .stream(defaults.getStream())
                .enableThinking(defaults.getEnableThinking())
                .formatter(new DashScopeChatFormatter());

        if (config.getTemperature() != null) {
            builder.defaultOptions(buildGenerateOptions(config));
        }
        return builder.build();
    }

    /**
     * OpenAI 及其兼容 API（DeepSeek、GLM、Moonshot 等，通过 baseUrl 适配）
     */
    private Model createOpenAI(LLMConfig config, LuminaAgentProperties.LLMConfig defaults,
                               String apiKey, String modelName) {
        OpenAIChatModel.Builder builder = OpenAIChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .stream(defaults.getStream())
                .formatter(new OpenAIChatFormatter());

        if (config.getBaseUrl() != null) {
            builder.baseUrl(config.getBaseUrl());
        }
        if (config.getTemperature() != null) {
            builder.generateOptions(buildGenerateOptions(config));
        }
        return builder.build();
    }

    /**
     * Anthropic（Claude）
     */
    private Model createAnthropic(LLMConfig config, LuminaAgentProperties.LLMConfig defaults,
                                  String apiKey, String modelName) {
        AnthropicChatModel.Builder builder = AnthropicChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .stream(defaults.getStream())
                .formatter(new AnthropicChatFormatter());

        if (config.getBaseUrl() != null) {
            builder.baseUrl(config.getBaseUrl());
        }
        if (config.getTemperature() != null) {
            builder.defaultOptions(buildGenerateOptions(config));
        }
        return builder.build();
    }

    /**
     * Ollama（本地模型，无需 API Key）
     */
    private Model createOllama(LLMConfig config, String modelName) {
        OllamaChatModel.Builder builder = OllamaChatModel.builder()
                .modelName(modelName)
                .formatter(new OllamaChatFormatter());

        if (config.getBaseUrl() != null) {
            builder.baseUrl(config.getBaseUrl());
        }
        return builder.build();
    }

    /**
     * OpenAI 兼容预设 Provider（智谱 GLM / Kimi / 豆包 / Minimax / DeepSeek / Yi / 通义）
     *
     * <p>自动填充对应平台的 base-url，用户显式配置 baseUrl 时优先用户值。
     */
    private Model createOpenAICompatiblePreset(String presetType, LLMConfig config,
                                                LuminaAgentProperties.LLMConfig defaults,
                                                String apiKey, String modelName) {
        String presetBaseUrl = ProviderPresets.getPresetBaseUrl(presetType);
        String effectiveBaseUrl = config.getBaseUrl() != null ? config.getBaseUrl() : presetBaseUrl;

        log.info("使用预设 Provider: {} → baseUrl={}", presetType, effectiveBaseUrl);

        OpenAIChatModel.Builder builder = OpenAIChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .stream(defaults.getStream())
                .formatter(new OpenAIChatFormatter());

        builder.baseUrl(effectiveBaseUrl);

        if (config.getTemperature() != null) {
            builder.generateOptions(buildGenerateOptions(config));
        }
        return builder.build();
    }

    /**
     * 构建 GenerateOptions（temperature + maxTokens + topP + penalties + seed + topK）
     */
    private GenerateOptions buildGenerateOptions(LLMConfig config) {
        GenerateOptions.Builder ob = GenerateOptions.builder();
        if (config.getTemperature() != null) {
            ob.temperature(config.getTemperature().doubleValue());
        }
        if (config.getMaxTokens() != null) {
            ob.maxTokens(config.getMaxTokens());
        }
        if (config.getTopP() != null) {
            ob.topP(config.getTopP());
        }
        if (config.getFrequencyPenalty() != null) {
            ob.frequencyPenalty(config.getFrequencyPenalty());
        }
        if (config.getPresencePenalty() != null) {
            ob.presencePenalty(config.getPresencePenalty());
        }
        if (config.getSeed() != null) {
            ob.seed(config.getSeed());
        }
        if (config.getTopK() != null) {
            ob.topK(config.getTopK());
        }
        return ob.build();
    }
}
