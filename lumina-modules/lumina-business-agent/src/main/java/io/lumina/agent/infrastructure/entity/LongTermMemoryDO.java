package io.lumina.agent.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 长期记忆 DO
 *
 * @author Lumina Team
 * @since 3.3.1
 */
@Data
@TableName("lumina_long_term_memory")
public class LongTermMemoryDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("agent_id")
    private Long agentId;

    @TableField("conversation_id")
    private String conversationId;

    @TableField("memory_type")
    private String memoryType;

    @TableField("content")
    private String content;

    @TableField("importance")
    private BigDecimal importance;

    @TableField("access_count")
    private Integer accessCount;

    @TableField("last_accessed")
    private LocalDateTime lastAccessed;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
