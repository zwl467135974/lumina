package io.lumina.agent.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * LLM 供应商配置 DO
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
@TableName("lumina_llm_provider")
public class LlmProviderDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("name")
    private String name;

    @TableField("provider")
    private String provider;

    @TableField("base_url")
    private String baseUrl;

    @TableField("api_key_enc")
    private String apiKeyEnc;

    @TableField("default_model")
    private String defaultModel;

    @TableField("default_params")
    private String defaultParams;

    @TableField("status")
    private Integer status;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
