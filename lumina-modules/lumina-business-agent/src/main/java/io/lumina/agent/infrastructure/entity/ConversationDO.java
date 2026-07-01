package io.lumina.agent.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会话数据库实体（DO）
 *
 * @author Lumina Team
 * @since 1.1.0
 */
@Data
@TableName("lumina_conversation")
public class ConversationDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "conversation_id", type = IdType.AUTO)
    private Long conversationId;

    @TableField("conversation_uuid")
    private String conversationUuid;

    @TableField("agent_id")
    private Long agentId;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("user_id")
    private Long userId;

    @TableField("title")
    private String title;

    @TableField("message_count")
    private Integer messageCount;

    @TableField("status")
    private Integer status;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
