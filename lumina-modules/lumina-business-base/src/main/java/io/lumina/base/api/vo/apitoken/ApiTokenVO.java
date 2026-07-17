package io.lumina.base.api.vo.apitoken;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * API Token VO
 *
 * <p>{@link #token} 明文只在创建时返回一次，列表查询不返回。
 *
 * @author Lumina Team
 * @since 3.4.0
 */
@Data
public class ApiTokenVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Token ID
     */
    private Long id;

    /**
     * Token 名称
     */
    private String name;

    /**
     * Token 明文（仅创建时返回一次，请妥善保管）
     */
    private String token;

    /**
     * 权限范围（逗号分隔）
     */
    private String scopes;

    /**
     * 状态（1-启用，0-禁用）
     */
    private Integer status;

    /**
     * 最后使用时间
     */
    private LocalDateTime lastUsedAt;

    /**
     * 过期时间（null=永不过期）
     */
    private LocalDateTime expiresAt;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
