package io.lumina.agent.api.dto.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * OpenAI Chat Completions 非流式响应
 *
 * @author Lumina Team
 * @since 3.4.0
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatCompletionResponse {

    /**
     * 响应 ID（chatcmpl- 前缀）
     */
    private String id;

    /**
     * 对象类型（固定 chat.completion）
     */
    private String object = "chat.completion";

    /**
     * 创建时间（Unix 秒）
     */
    private Long created;

    /**
     * 模型标识（回显请求 model）
     */
    private String model;

    /**
     * 候选列表
     */
    private List<Choice> choices;

    /**
     * Token 用量
     */
    private Usage usage;

    /**
     * 候选单元
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Choice {

        /**
         * 候选序号
         */
        private Integer index;

        /**
         * 助手消息
         */
        private ChatMessage message;

        /**
         * 结束原因（stop/length/...）
         */
        @JsonProperty("finish_reason")
        private String finishReason;
    }

    /**
     * 助手消息
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ChatMessage {

        /**
         * 角色（assistant）
         */
        private String role;

        /**
         * 文本内容
         */
        private String content;
    }
}
