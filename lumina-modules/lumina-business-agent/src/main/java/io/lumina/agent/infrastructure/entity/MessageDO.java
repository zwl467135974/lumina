package io.lumina.agent.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 对话消息数据库实体（DO）
 *
 * @author Lumina Team
 * @since 1.1.0
 */
@Data
@TableName("lumina_message")
public class MessageDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "message_id", type = IdType.AUTO)
    private Long messageId;

    @TableField("conversation_id")
    private Long conversationId;

    @TableField("role")
    private String role;

    @TableField("content")
    private String content;

    @TableField("token_count")
    private Integer tokenCount;

    @TableField("duration_ms")
    private Long durationMs;

    /** 关联文件 UUID 列表（JSON 数组，仅多模态用户消息有值） */
    @TableField("file_ids")
    private String fileIds;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
