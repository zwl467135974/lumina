package io.lumina.agent.api.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * Agent 推理链追踪 VO
 *
 * @author Lumina Team
 * @since 3.7.0
 */
@Data
public class AgentTraceVO {

    private Long traceId;
    private String traceUuid;
    private String taskUuid;
    private String conversationUuid;
    private Long agentId;
    private String agentName;
    private String agentType;

    private String inputText;
    private String outputText;
    private String status;

    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;

    private Long durationMs;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    /** steps JSON 字符串（前端解析为树形渲染） */
    private Object steps;

    private LocalDateTime createTime;

    public static AgentTraceVO from(io.lumina.agent.infrastructure.entity.AgentTraceDO entity) {
        AgentTraceVO vo = new AgentTraceVO();
        vo.setTraceId(entity.getTraceId());
        vo.setTraceUuid(entity.getTraceUuid());
        vo.setTaskUuid(entity.getTaskUuid());
        vo.setConversationUuid(entity.getConversationUuid());
        vo.setAgentId(entity.getAgentId());
        vo.setAgentName(entity.getAgentName());
        vo.setAgentType(entity.getAgentType());
        vo.setInputText(entity.getInputText());
        vo.setOutputText(entity.getOutputText());
        vo.setStatus(entity.getStatus());
        vo.setPromptTokens(entity.getPromptTokens());
        vo.setCompletionTokens(entity.getCompletionTokens());
        vo.setTotalTokens(entity.getTotalTokens());
        vo.setDurationMs(entity.getDurationMs());
        vo.setStartedAt(entity.getStartedAt());
        vo.setFinishedAt(entity.getFinishedAt());
        vo.setCreateTime(entity.getCreateTime());
        // steps 保持为原始 JSON 字符串，前端自行解析
        vo.setSteps(entity.getSteps());
        return vo;
    }
}
