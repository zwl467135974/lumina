package io.lumina.agent.api.dto.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * OpenAI Usage（Token 用量）
 *
 * @author Lumina Team
 * @since 3.4.0
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Usage {

    /**
     * 输入 Token 数
     */
    @JsonProperty("prompt_tokens")
    private Integer promptTokens;

    /**
     * 输出 Token 数
     */
    @JsonProperty("completion_tokens")
    private Integer completionTokens;

    /**
     * 总 Token 数
     */
    @JsonProperty("total_tokens")
    private Integer totalTokens;

    public static Usage of(Integer promptTokens, Integer completionTokens, Integer totalTokens) {
        Usage usage = new Usage();
        usage.setPromptTokens(promptTokens != null ? promptTokens : 0);
        usage.setCompletionTokens(completionTokens != null ? completionTokens : 0);
        usage.setTotalTokens(totalTokens != null ? totalTokens : 0);
        return usage;
    }
}
