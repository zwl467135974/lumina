package io.lumina.agent.model;

import lombok.Data;

import java.io.Serializable;
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
     * 额外参数
     */
    private Map<String, Object> extraParams;

    /**
     * LLM 模型配置
     */
    @Data
    public static class LLMConfig implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 模型类型（如：dashscope、openai、claude）
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
