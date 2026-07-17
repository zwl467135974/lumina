package io.lumina.base.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * API Token 数据库实体（DO）
 *
 * <p>外部调用（OpenAI 兼容端点）认证用 Token，DB 仅存 SHA-256 哈希，明文只在创建时返回一次。
 *
 * @author Lumina Team
 * @since 3.4.0
 */
@Data
@TableName("lumina_api_token")
public class ApiTokenDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Token ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * SHA-256(明文)，查询用
     */
    @TableField("token_hash")
    private String tokenHash;

    /**
     * 所属用户 ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 租户 ID
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 用户自填名称
     */
    @TableField("name")
    private String name;

    /**
     * 权限范围（逗号分隔）
     */
    @TableField("scopes")
    private String scopes;

    /**
     * 状态（1-启用，0-禁用）
     */
    @TableField("status")
    private Integer status;

    /**
     * 最后使用时间
     */
    @TableField("last_used_at")
    private LocalDateTime lastUsedAt;

    /**
     * 过期时间（NULL=永不过期）
     */
    @TableField("expires_at")
    private LocalDateTime expiresAt;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除（0-未删除，1-已删除）
     */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
