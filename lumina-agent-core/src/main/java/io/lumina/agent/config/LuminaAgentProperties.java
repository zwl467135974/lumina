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
         * 模型类型（dashscope/openai/claude，默认：dashscope）
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
    }
}

