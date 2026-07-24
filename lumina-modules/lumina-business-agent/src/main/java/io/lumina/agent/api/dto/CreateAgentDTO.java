package io.lumina.agent.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 创建 Agent DTO
 *
 * <p>llmConfig 和 tools 使用结构化类型接收前端请求，
 * Controller 层负责转换为 Agent domain model 的 String 格式存库。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
public class CreateAgentDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Agent 名称
     */
    @NotBlank(message = "Agent 名称不能为空")
    @Size(max = 100, message = "Agent 名称不能超过 100 个字符")
    private String agentName;

    /**
     * Agent 类型
     */
    @NotBlank(message = "Agent 类型不能为空")
    @Size(max = 50, message = "Agent 类型不能超过 50 个字符")
    private String agentType;

    /**
     * 描述
     */
    @Size(max = 500, message = "描述不能超过 500 个字符")
    private String description;

    /**
     * LLM 配置（结构化对象，前端直接传 JSON 对象）
     */
    private LlmConfigDTO llmConfig;

    /**
     * 工具列表（前端传数组，如 ["base.getUser", "base.createUser"]）
     */
    private List<String> tools;

    /**
     * 挂载的知识库 ID 列表（关联 lumina_agent_knowledge_base 中间表）
     */
    private List<Long> knowledgeBaseIds;

    /**
     * 每分钟最大请求数（per-agent 限流，0=用全局默认）
     */
    private Integer rateLimit;

    /**
     * 最大并发执行数（per-agent 并发限制，0=不限制）
     */
    private Integer maxConcurrent;

    /**
     * 子 Agent 配置 JSON 字符串（MultiAgent 模式，JSON 数组）
     *
     * @since 3.8.0
     */
    private String subAgents;

    /**
     * LLM 配置 DTO（字段与前端 LlmConfig 对齐，同时兼容 AgentConfig.LLMConfig）
     */
    @Data
    public static class LlmConfigDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 模型提供商（前端字段名 provider，后端 AgentConfig 用 modelType）
         */
        @JsonAlias({"provider", "modelType", "type"})
        private String modelType;

        private String modelName;
        private String apiKey;
        private String baseUrl;
        private Double temperature;
        private Integer maxTokens;
        private Double topP;
        private Double frequencyPenalty;
        private Double presencePenalty;
    }
}
