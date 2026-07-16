package io.lumina.agent.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * A/B 测试变体 DO
 *
 * @author Lumina Team
 * @since 3.3.1
 */
@Data
@TableName("lumina_ab_variant")
public class AbVariantDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("experiment_id")
    private Long experimentId;

    @TableField("name")
    private String name;

    @TableField("weight")
    private Integer weight;

    @TableField("llm_config")
    private String llmConfig;

    @TableField("prompt_name")
    private String promptName;

    @TableField("description")
    private String description;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
