package io.lumina.base.domain.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户领域实体
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
     * 租户 ID（0表示系统管理员）
     */
    private Long tenantId;

    /**
     * 用户名（租户内唯一）
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
     * 头像 URL
     */
    private String avatar;

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
     * 关联的角色列表（不持久化）
     */
    private List<Role> roles = new ArrayList<>();

    /**
     * 关联的权限列表（不持久化，通过角色聚合）
     */
    private List<Permission> permissions = new ArrayList<>();

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
     * 判断是否是超级管理员
     */
    public boolean isSuperAdmin() {
        return roles.stream()
                .anyMatch(role -> "SUPER_ADMIN".equals(role.getRoleCode()));
    }

    /**
     * 判断是否是租户管理员
     */
    public boolean isTenantAdmin() {
        return roles.stream()
                .anyMatch(role -> "TENANT_ADMIN".equals(role.getRoleCode()));
    }

    /**
     * 判断是否是系统管理员
     */
    public boolean isSystemAdmin() {
        return this.tenantId == 0 && (isSuperAdmin() || roles.stream()
                .anyMatch(role -> "SYSTEM_ADMIN".equals(role.getRoleCode())));
    }

    /**
     * 获取所有权限编码（通过角色聚合）
     */
    public List<String> getPermissionCodes() {
        return permissions.stream()
                .map(Permission::getPermissionCode)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 获取所有角色编码
     */
    public List<String> getRoleCodes() {
        return roles.stream()
                .map(Role::getRoleCode)
                .collect(Collectors.toList());
    }

    /**
     * 判断是否有指定权限
     */
    public boolean hasPermission(String permissionCode) {
        return permissions.stream()
                .anyMatch(p -> permissionCode.equals(p.getPermissionCode()));
    }

    /**
     * 判断是否有指定角色
     */
    public boolean hasRole(String roleCode) {
        return roles.stream()
                .anyMatch(role -> roleCode.equals(role.getRoleCode()));
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

    /**
     * 验证邮箱
     */
    public void validateEmail() {
        if (email != null && !email.isEmpty()) {
            String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
            if (!email.matches(emailRegex)) {
                throw new IllegalArgumentException("邮箱格式不正确");
            }
        }
    }

    /**
     * 验证手机号
     */
    public void validatePhone() {
        if (phone != null && !phone.isEmpty()) {
            String phoneRegex = "^1[3-9]\\d{9}$";
            if (!phone.matches(phoneRegex)) {
                throw new IllegalArgumentException("手机号格式不正确");
            }
        }
    }
}
