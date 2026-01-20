package io.lumina.base.api.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 登录响应 VO
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
public class LoginVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * JWT Token
     */
    private String token;

    /**
     * Token 类型
     */
    private String tokenType;

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
     * 真实姓名
     */
    private String realName;

    /**
     * 角色编码列表
     */
    private String[] roles;

    /**
     * 权限编码列表
     */
    private String[] permissions;

    /**
     * 过期时间（时间戳）
     */
    private Long expiration;
}
