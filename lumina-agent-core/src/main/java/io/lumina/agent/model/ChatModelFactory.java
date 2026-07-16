package io.lumina.agent.model;

import io.agentscope.core.formatter.anthropic.AnthropicChatFormatter;
import io.agentscope.core.formatter.dashscope.DashScopeChatFormatter;
import io.agentscope.core.formatter.gemini.GeminiChatFormatter;
import io.agentscope.core.formatter.ollama.OllamaChatFormatter;
import io.agentscope.core.formatter.openai.OpenAIChatFormatter;
import io.agentscope.core.model.AnthropicChatModel;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.GeminiChatModel;
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
 * 支持 DashScope / OpenAI / Anthropic / Gemini / Ollama 五类模型，
 * 以及 GLM/Kimi/豆包/DeepSeek 等 OpenAI 兼容预设。
 *
 * <p>新增模型只需在 {@link #create} 的 switch 中追加分支。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class ChatModelFactory {

    private final LuminaAgentProperties agentProperties;

    public ChatModelFactory(LuminaAgentProperties agentProperties) {
        this.agentProperties = agentProperties;
    }

    /**
     * 启动时注册自定义 OpenAI 兼容预设（从 lumina.agent.llm.presets 配置读取）
     */
    @jakarta.annotation.PostConstruct
    public void registerCustomPresets() {
        if (agentProperties.getLlm() != null && agentProperties.getLlm().getPresets() != null) {
            ProviderPresets.registerAll(agentProperties.getLlm().getPresets());
            log.info("已注册自定义 LLM 预设: {}", agentProperties.getLlm().getPresets().keySet());
        }
    }

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
            case "gemini":
            case "google":
                model = createGemini(config, defaults, apiKey, modelName);
                break;
            default:
                if (ProviderPresets.isOpenAICompatible(type)) {
                    model = createOpenAICompatiblePreset(type, config, defaults, apiKey, modelName);
                    break;
                }
                throw new SystemException(ErrorCode.AGENT_CONFIG_ERROR, "不支持的模型类型: " + type
                    + "。支持: dashscope/openai/anthropic(claude)/gemini(google)/ollama"
                    + "/glm/kimi/doubao/minimax/deepseek/yi/qwen");
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
                .stream(resolveStream(config, defaults))
                .enableThinking(resolveEnableThinking(config, defaults))
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
                .stream(resolveStream(config, defaults))
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
                .stream(resolveStream(config, defaults))
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
     * Google Gemini（通过 Google Gen AI SDK，支持 Vertex AI 模式）
     *
     * <p>默认走 AI Studio API（仅需 apiKey），Vertex AI 需额外配置 project/location。
     *
     * @since 3.3.0
     */
    private Model createGemini(LLMConfig config, LuminaAgentProperties.LLMConfig defaults,
                                String apiKey, String modelName) {
        GeminiChatModel.Builder builder = GeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .streamEnabled(resolveStream(config, defaults))
                .formatter(new GeminiChatFormatter());

        if (config.getTemperature() != null) {
            builder.defaultOptions(buildGenerateOptions(config));
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
                .stream(resolveStream(config, defaults))
                .formatter(new OpenAIChatFormatter());

        builder.baseUrl(effectiveBaseUrl);

        if (config.getTemperature() != null) {
            builder.generateOptions(buildGenerateOptions(config));
        }
        return builder.build();
    }

    /**
     * 解析流式开关：Agent 级配置优先，未设置时回退全局默认
     */
    private boolean resolveStream(LLMConfig config, LuminaAgentProperties.LLMConfig defaults) {
        return Boolean.TRUE.equals(config.getStream() != null ? config.getStream() : defaults.getStream());
    }

    /**
     * 解析思考模式开关：Agent 级配置优先，未设置时回退全局默认
     */
    private boolean resolveEnableThinking(LLMConfig config, LuminaAgentProperties.LLMConfig defaults) {
        return Boolean.TRUE.equals(config.getEnableThinking() != null ? config.getEnableThinking() : defaults.getEnableThinking());
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
        if (config.getThinkingBudget() != null) {
            ob.thinkingBudget(config.getThinkingBudget());
        }
        if (config.getReasoningEffort() != null && !config.getReasoningEffort().isBlank()) {
            ob.reasoningEffort(config.getReasoningEffort());
        }
        return ob.build();
    }
}
