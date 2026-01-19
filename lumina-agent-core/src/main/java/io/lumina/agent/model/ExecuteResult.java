package io.lumina.agent.model;

import lombok.Data;

import java.io.Serializable;

/**
 * Agent 执行结果
 *
 * <p>封装 Agent 执行后的结果信息。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
public class ExecuteResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 执行结果
     */
    private String result;

    /**
     * 错误信息
     */
    private String error;

    /**
     * 执行时长（毫秒）
     */
    private Long duration;

    /**
     * 时间戳（毫秒）
     */
    private Long timestamp;

    /**
     * Token 使用量
     */
    private TokenUsage tokenUsage;

    /**
     * 额外信息
     */
    private java.util.Map<String, Object> metadata;

    /**
     * Token 使用量
     */
    @Data
    public static class TokenUsage implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 输入 Token 数
         */
        private Integer promptTokens;

        /**
         * 输出 Token 数
         */
        private Integer completionTokens;

        /**
         * 总 Token 数
         */
        private Integer totalTokens;
    }

    /**
     * 创建成功结果
     */
    public static ExecuteResult success(String result) {
        ExecuteResult executeResult = new ExecuteResult();
        executeResult.setSuccess(true);
        executeResult.setResult(result);
        executeResult.setTimestamp(System.currentTimeMillis());
        return executeResult;
    }

    /**
     * 创建失败结果
     */
    public static ExecuteResult failure(String error) {
        ExecuteResult executeResult = new ExecuteResult();
        executeResult.setSuccess(false);
        executeResult.setError(error);
        executeResult.setTimestamp(System.currentTimeMillis());
        return executeResult;
    }
}
