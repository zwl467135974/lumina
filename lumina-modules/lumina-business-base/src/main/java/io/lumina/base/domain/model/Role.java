package io.lumina.base.domain.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 角色领域实体
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
public class Role implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 角色 ID
     */
    private Long roleId;

    /**
     * 租户 ID（0表示系统角色）
     */
    private Long tenantId;

    /**
     * 角色编码
     */
    private String roleCode;

    /**
     * 角色名称
     */
    private String roleName;

    /**
     * 角色描述
     */
    private String description;

    /**
     * 排序序号
     */
    private Integer sortOrder;

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
     * 关联的权限列表
     */
    private List<Permission> permissions = new ArrayList<>();

    /**
     * 激活角色
     */
    public void activate() {
        if (this.status == 1) {
            throw new IllegalStateException("角色已是启用状态");
        }
        this.status = 1;
    }

    /**
     * 禁用角色
     */
    public void deactivate() {
        if (this.status == 0) {
            throw new IllegalStateException("角色已是禁用状态");
        }
        this.status = 0;
    }

    /**
     * 判断是否是系统角色
     */
    public boolean isSystemRole() {
        return this.tenantId == 0;
    }

    /**
     * 判断是否启用
     */
    public boolean isActive() {
        return this.status == 1;
    }

    /**
     * 判断是否是超级管理员角色
     */
    public boolean isSuperAdmin() {
        return "SUPER_ADMIN".equals(this.roleCode);
    }

    /**
     * 判断是否是租户管理员角色
     */
    public boolean isTenantAdmin() {
        return "TENANT_ADMIN".equals(this.roleCode);
    }

    /**
     * 添加权限
     */
    public void addPermission(Permission permission) {
        if (this.permissions == null) {
            this.permissions = new ArrayList<>();
        }
        if (!this.permissions.contains(permission)) {
            this.permissions.add(permission);
        }
    }

    /**
     * 移除权限
     */
    public void removePermission(Long permissionId) {
        if (this.permissions != null) {
            this.permissions.removeIf(p -> p.getPermissionId().equals(permissionId));
        }
    }

    /**
     * 获取所有权限编码
     */
    public List<String> getPermissionCodes() {
        return permissions.stream()
                .map(Permission::getPermissionCode)
                .toList();
    }

    /**
     * 验证角色编码
     */
    public void validateRoleCode() {
        if (roleCode == null || roleCode.trim().isEmpty()) {
            throw new IllegalArgumentException("角色编码不能为空");
        }
        String codeRegex = "^[A-Z_][A-Z0-9_]*$";
        if (!roleCode.matches(codeRegex)) {
            throw new IllegalArgumentException("角色编码格式不正确，只能包含大写字母、数字和下划线，且必须以字母或下划线开头");
        }
    }
}
