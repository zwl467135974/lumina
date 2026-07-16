package io.lumina.agent.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * A/B 测试曝光记录 DO
 *
 * @author Lumina Team
 * @since 3.3.1
 */
@Data
@TableName("lumina_ab_exposure")
public class AbExposureDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("experiment_id")
    private Long experimentId;

    @TableField("variant_id")
    private Long variantId;

    @TableField("variant_name")
    private String variantName;

    @TableField("conversation_id")
    private String conversationId;

    @TableField("user_id")
    private Long userId;

    @TableField("success")
    private Integer success;

    @TableField("latency_ms")
    private Long latencyMs;

    @TableField("tokens")
    private Integer tokens;

    @TableField("error_msg")
    private String errorMsg;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
