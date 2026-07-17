package io.lumina.agent.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 异步任务 DO
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Data
@TableName("lumina_agent_task")
public class AgentTaskDO {

    @TableId(type = IdType.AUTO)
    private Long id;

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
    private String modelName;
    private String provider;
    private Long durationMs;

    /**
     * 触发来源（定时 trigger 的 id，手动提交为 null）
     */
    private Long triggerId;

    private Long tenantId;
    private Long createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableField("is_deleted")
    private Integer isDeleted;
}
