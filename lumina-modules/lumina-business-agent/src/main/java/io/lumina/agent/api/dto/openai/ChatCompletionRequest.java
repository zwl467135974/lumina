package io.lumina.agent.api.dto.openai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * OpenAI Chat Completions 请求（标准 schema）
 *
 * <p>{@code model} 支持 {@code agent-{id}}（按 ID）或 Agent 名称（按名查表）。
 * 采样类参数（top_p/frequency_penalty 等）保留字段但由 Agent 自身 LLM 配置决定，不透传。
 *
 * @author Lumina Team
 * @since 3.4.0
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatCompletionRequest {

    /**
     * 模型标识（agent-{id} 或 Agent 名称）
     */
    @NotBlank(message = "model 不能为空")
    private String model;

    /**
     * 消息列表（system/user/assistant）
     */
    @NotEmpty(message = "messages 不能为空")
    @Valid
    private List<Message> messages;

    /**
     * 是否流式返回（SSE）
     */
    private Boolean stream = false;

    /**
     * 采样温度（保留字段，暂不透传）
     */
    private Double temperature;

    /**
     * 最大输出 Token（保留字段，暂不透传）
     */
    @JsonProperty("max_tokens")
    private Integer maxTokens;

    /**
     * 核采样（保留字段，暂不透传）
     */
    @JsonProperty("top_p")
    private Double topP;

    /**
     * 候选数（保留字段，暂不透传）
     */
    private Integer n;

    /**
     * 停止词（保留字段，暂不透传）
     */
    private Object stop;

    /**
     * 存在惩罚（保留字段，暂不透传）
     */
    @JsonProperty("presence_penalty")
    private Double presencePenalty;

    /**
     * 频率惩罚（保留字段，暂不透传）
     */
    @JsonProperty("frequency_penalty")
    private Double frequencyPenalty;

    /**
     * 终端用户标识（保留字段，暂不透传）
     */
    private String user;

    /**
     * 消息单元
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Message {

        /**
         * 角色（system/user/assistant）
         */
        @NotBlank(message = "message.role 不能为空")
        private String role;

        /**
         * 文本内容
         */
        private String content;
    }
}
