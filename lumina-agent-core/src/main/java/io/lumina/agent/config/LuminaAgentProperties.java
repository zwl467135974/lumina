package io.lumina.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Lumina Agent 配置属性
 *
 * <p>从配置文件读取 Agent 相关配置。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "lumina.agent")
public class LuminaAgentProperties {

    /**
     * LLM 配置
     */
    private LLMConfig llm = new LLMConfig();

    /**
     * LLM 配置
     */
    @Data
    public static class LLMConfig {
        /**
         * API Key（优先从环境变量读取）
         */
        private String apiKey;

        /**
         * 模型名称（默认：qwen-plus）
         */
        private String model = "qwen-plus";

        /**
         * 模型类型（dashscope/openai/anthropic[或claude]/ollama，
         * 或预设: glm/kimi/doubao/minimax/deepseek/yi/qwen，默认：dashscope）
         */
        private String type = "dashscope";

        /**
         * Base URL（仅用于 OpenAI 等需要自定义 URL 的模型）
         */
        private String baseUrl;

        /**
         * 温度（0-1，默认：0.7）
         */
        private Double temperature = 0.7;

        /**
         * 最大 Token 数（默认：2000）
         */
        private Integer maxTokens = 2000;

        /**
         * 是否启用流式输出（默认：false）
         */
        private Boolean stream = false;

        /**
         * 是否启用思考模式（默认：false）
         */
        private Boolean enableThinking = false;

        /**
         * Top-P 核采样（0-1，null 则不传）
         */
        private Double topP;

        /**
         * 频率惩罚（-2.0 到 2.0，null 则不传）
         */
        private Double frequencyPenalty;

        /**
         * 存在惩罚（-2.0 到 2.0，null 则不传）
         */
        private Double presencePenalty;

        /**
         * 随机种子（可复现输出，null 则不传）
         */
        private Long seed;

        /**
         * Top-K 采样（null 则不传）
         */
        private Integer topK;
    }

    /**
     * 工具配置
     */
    private ToolConfig tool = new ToolConfig();

    /**
     * 工具配置
     */
    @Data
    public static class ToolConfig {

        /**
         * 单次工具执行超时（毫秒），默认 60 秒
         */
        private Integer executionTimeoutMs = 60000;

        /**
         * 熔断失败阈值（连续失败次数）
         */
        private Integer failureThreshold = 5;

        /**
         * 熔断恢复超时（毫秒）
         */
        private Long resetTimeoutMs = 60000L;
    }
}

