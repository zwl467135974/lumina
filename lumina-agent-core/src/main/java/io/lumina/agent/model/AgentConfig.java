package io.lumina.agent.model;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Agent 配置
 *
 * <p>封装 Agent 执行所需的配置信息。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
public class AgentConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Agent ID（用于引擎层缓存失效，由 Service 层填充）
     */
    private Long agentId;

    /**
     * Agent 名称
     */
    private String agentName;

    /**
     * Agent 类型（如：ReAct、PlanAndExecute、MultiAgent）
     */
    private String agentType;

    /**
     * LLM 模型配置
     */
    private LLMConfig llmConfig;

    /**
     * 工具配置
     */
    private ToolConfig toolConfig;

    /**
     * 记忆配置
     */
    private MemoryConfig memoryConfig;

    /**
     * 提示词模板
     */
    private String promptTemplate;

    /**
     * Agent 挂载的知识库 ID 列表（执行时按 kbId 过滤 RAG 检索）
     */
    private List<Long> knowledgeBaseIds;

    /**
     * 额外参数
     */
    private Map<String, Object> extraParams;

    /**
     * ReAct 循环最大迭代次数（null 时用全局默认 lumina.agent.max-iterations）
     *
     * <p>每次迭代 = 一次 LLM 推理 + 一次工具调用。超过次数后 Agent 强制停止。
     * 防止 Agent 陷入死循环无限烧 Token。
     *
     * @since 3.8.0
     */
    private Integer maxIterations;

    /**
     * 结构化输出模式（JSON_OBJECT / TEXT，null = 不启用）
     *
     * <p>启用后 Agent 通过 GenerateOptions.responseFormat 约束 LLM 输出格式。
     * <ul>
     *   <li>JSON_OBJECT：强制模型输出合法 JSON</li>
     *   <li>TEXT：普通文本输出（默认）</li>
     * </ul>
     *
     * @since 3.8.0
     */
    private String structuredOutputMode;

    /**
     * 结构化输出的目标 Java 类全限定名（如 io.lumina.agent.dto.WeatherResult）
     *
     * <p>AgentScope 会根据此类的字段定义生成 JSON Schema，约束 LLM 输出。
     * 仅当 {@link #structuredOutputMode} 不为 null 时生效。
     *
     * @since 3.8.0
     */
    private String structuredOutputClass;

    /**
     * LLM 模型配置
     */
    @Data
    public static class LLMConfig implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 模型类型（如：dashscope、openai、anthropic[或claude]、ollama）
         */
        private String modelType;

        /**
         * 模型名称（如：qwen-max、gpt-4、claude-3-opus）
         */
        private String modelName;

        /**
         * API Key
         */
        private String apiKey;

        /**
         * 温度（0-1）
         */
        private Double temperature;

        /**
         * 最大 Token 数
         */
        private Integer maxTokens;

        /**
         * API Base URL（OpenAI 兼容 API、Ollama 本地模型等场景使用）
         */
        private String baseUrl;

        /**
         * Gemini Vertex AI 模式：是否启用（true 时使用 project/location 而非 apiKey）
         *
         * @since 3.3.1
         */
        private Boolean vertexAi;

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
         * 是否启用流式输出（null 时使用全局默认）
         */
        private Boolean stream;

        /**
         * 是否启用思考模式（null 时使用全局默认）
         */
        private Boolean enableThinking;

        /**
         * Top-P 核采样
         */
        private Double topP;

        /**
         * 频率惩罚
         */
        private Double frequencyPenalty;

        /**
         * 存在惩罚
         */
        private Double presencePenalty;

        /**
         * 随机种子
         */
        private Long seed;

        /**
         * Top-K 采样
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

        /**
         * Failover 备选 Provider 列表（主 Provider 失败后按顺序尝试）
         *
         * @since 3.3.1
         */
        private java.util.List<FallbackProvider> fallbackProviders;
    }

    /**
     * Failover 备选 Provider
     *
     * @since 3.3.1
     */
    @Data
    public static class FallbackProvider implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 模型类型（如：dashscope、openai、glm）
         */
        private String modelType;

        /**
         * 模型名称（如：qwen-max、gpt-4）
         */
        private String modelName;

        /**
         * API Key
         */
        private String apiKey;

        /**
         * API Base URL
         */
        private String baseUrl;
    }

    /**
     * 工具配置
     */
    @Data
    public static class ToolConfig implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 工具列表
         */
        private java.util.List<String> tools;

        /**
         * 是否启用所有工具
         */
        private Boolean enableAll;
    }

    /**
     * 记忆配置
     */
    @Data
    public static class MemoryConfig implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 记忆类型（如：memory、hash_memory）
         */
        private String memoryType;

        /**
         * 最大记忆条数
         */
        private Integer maxMemorySize;
    }
}
