package io.lumina.agent.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 推理链追踪 DO
 *
 * @author Lumina Team
 * @since 3.7.0
 */
@Data
@TableName("lumina_agent_trace")
public class AgentTraceDO {

    @TableId(value = "trace_id", type = IdType.AUTO)
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

    /** steps JSON 字符串（由 Service 层序列化） */
    private String steps;

    @TableField("tenant_id")
    private Long tenantId;
    private Long createBy;
    private LocalDateTime createTime;
}
