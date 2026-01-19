package io.lumina.agent.api.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

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
     * Token 类型（Bearer）
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
     * 真实姓名
     */
    private String realName;

    /**
     * 角色
     */
    private String role;

    /**
     * 头像（可选）
     */
    private String avatar;

    /**
     * Token 过期时间（时间戳）
     */
    private Long expiration;
}
