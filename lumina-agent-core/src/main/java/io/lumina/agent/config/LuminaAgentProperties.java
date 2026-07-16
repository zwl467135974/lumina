package io.lumina.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

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
         * Gemini Vertex AI 模式：是否启用（true 时使用 project/location 而非 apiKey）
         *
         * <p>启用后需设置 GOOGLE_APPLICATION_CREDENTIALS 环境变量指向服务账号 JSON 文件。
         *
         * @since 3.3.1
         */
        private Boolean vertexAi = false;

        /**
         * Gemini Vertex AI：GCP 项目 ID
         *
         * @since 3.3.1
         */
        private String projectId;

        /**
         * Gemini Vertex AI：区域（如 us-central1）
         *
         * @since 3.3.1
         */
        private String location;

        /**
         * 自定义 OpenAI 兼容预设（v3.3.0 新增）
         *
         * <p>配置示例：
         * <pre>
         * lumina.agent.llm.presets.acme: https://api.acme.com/v1
         * lumina.agent.llm.presets.myllm: https://my-llm.local/v1
         * </pre>
         * 配置后 type=acme 即可自动使用对应 baseUrl，无需改代码。
         */
        private Map<String, String> presets;

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

        /**
         * 思考 Token 预算（Anthropic/Gemini 扩展思考模式，null 则不传）
         *
         * @since 3.3.1
         */
        private Integer thinkingBudget;

        /**
         * 推理强度（OpenAI o-series: low/medium/high，null 则不传）
         *
         * @since 3.3.1
         */
        private String reasoningEffort;
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

