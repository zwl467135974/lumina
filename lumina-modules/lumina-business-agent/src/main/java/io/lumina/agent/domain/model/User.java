package io.lumina.agent.domain.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户 领域实体
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
public class User implements Serializable {

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
     * 密码（加密后）
     */
    private String password;

    /**
     * 真实姓名
     */
    private String realName;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 角色（admin, user）
     */
    private String role;

    /**
     * 状态（0-禁用，1-启用）
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 激活用户
     */
    public void activate() {
        if (this.status == 1) {
            throw new IllegalStateException("用户已是启用状态");
        }
        this.status = 1;
    }

    /**
     * 禁用用户
     */
    public void deactivate() {
        if (this.status == 0) {
            throw new IllegalStateException("用户已是禁用状态");
        }
        this.status = 0;
    }

    /**
     * 判断是否启用
     */
    public boolean isActive() {
        return this.status == 1;
    }

    /**
     * 判断是否是管理员
     */
    public boolean isAdmin() {
        return "admin".equals(this.role);
    }

    /**
     * 验证用户名
     */
    public void validateUsername() {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (username.length() < 3 || username.length() > 50) {
            throw new IllegalArgumentException("用户名长度必须在3-50个字符之间");
        }
    }

    /**
     * 验证密码
     */
    public void validatePassword() {
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        if (password.length() < 6) {
            throw new IllegalArgumentException("密码长度不能少于6个字符");
        }
    }
}
