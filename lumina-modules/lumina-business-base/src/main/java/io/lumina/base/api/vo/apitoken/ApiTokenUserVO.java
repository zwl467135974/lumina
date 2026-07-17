package io.lumina.base.api.vo.apitoken;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * API Token 关联用户 VO
 *
 * <p>Token 校验通过后返回的身份信息，Gateway 据此注入下游身份头（X-User-Id 等）。
 * 会被缓存到 Redis（TTL 5 分钟），需保持可序列化。
 *
 * @author Lumina Team
 * @since 3.4.0
 */
@Data
public class ApiTokenUserVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 租户 ID
     */
    private Long tenantId;

    /**
     * 权限范围（逗号分隔，注入 X-Permissions）
     */
    private String scopes;

    /**
     * 角色（逗号分隔，Token 用户使用固定角色，注入 X-Roles）
     */
    private String roles;

    /**
     * Token 过期时间（缓存命中时二次校验用，null=永不过期）
     */
    private LocalDateTime expiresAt;
}
