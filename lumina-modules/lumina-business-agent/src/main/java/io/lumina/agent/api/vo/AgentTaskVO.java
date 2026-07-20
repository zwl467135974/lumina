package io.lumina.agent.api.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 异步任务 VO
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Data
public class AgentTaskVO {

    private String taskUuid;
    private Long agentId;
    private String conversationUuid;
    private String inputText;
    private String fileIds;
    private String status;
    private String result;
    private String errorMessage;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Long durationMs;
    private String modelName;
    private String provider;
    /**
     * 触发来源（定时 trigger 的 id），null 表示用户手动提交的任务
     *
     * @since 3.5.0
     */
    private Long triggerId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
