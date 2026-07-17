package io.lumina.agent.api.dto.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * OpenAI Chat Completions 流式 SSE Chunk
 *
 * <p>推理片段映射到 {@code delta.reasoning_content}（DeepSeek R1 扩展字段，
 * OpenAI 官方协议无此字段但主流客户端可识别）。
 *
 * @author Lumina Team
 * @since 3.4.0
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatCompletionChunk {

    /**
     * 响应 ID（同一次请求的所有 chunk 一致）
     */
    private String id;

    /**
     * 对象类型（固定 chat.completion.chunk）
     */
    private String object = "chat.completion.chunk";

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
    private List<ChunkChoice> choices;

    /**
     * 流式候选单元
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ChunkChoice {

        /**
         * 候选序号
         */
        private Integer index;

        /**
         * 增量内容
         */
        private Delta delta;

        /**
         * 结束原因（最后一个 chunk 为 stop，其余为 null）
         */
        @JsonProperty("finish_reason")
        private String finishReason;
    }

    /**
     * 增量内容
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Delta {

        /**
         * 角色（首个 chunk 为 assistant）
         */
        private String role;

        /**
         * 正文增量
         */
        private String content;

        /**
         * 推理过程增量（DeepSeek R1 扩展）
         */
        @JsonProperty("reasoning_content")
        private String reasoningContent;
    }
}
