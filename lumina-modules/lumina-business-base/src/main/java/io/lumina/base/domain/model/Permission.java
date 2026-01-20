package io.lumina.base.domain.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 权限领域实体（树形结构）
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
public class Permission implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 权限 ID
     */
    private Long permissionId;

    /**
     * 父权限 ID（0表示根节点）
     */
    private Long parentId;

    /**
     * 权限编码（如：system:user:create）
     */
    private String permissionCode;

    /**
     * 权限名称
     */
    private String permissionName;

    /**
     * 权限类型（1-菜单，2-按钮，3-接口）
     */
    private Integer permissionType;

    /**
     * 路由路径（菜单类型专用）
     */
    private String path;

    /**
     * 组件路径（菜单类型专用）
     */
    private String component;

    /**
     * 图标
     */
    private String icon;

    /**
     * 排序序号
     */
    private Integer sortOrder;

    /**
     * 是否可见（0-隐藏，1-可见）
     */
    private Integer visible;

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
     * 子权限列表
     */
    private List<Permission> children = new ArrayList<>();

    /**
     * 激活权限
     */
    public void activate() {
        if (this.status == 1) {
            throw new IllegalStateException("权限已是启用状态");
        }
        this.status = 1;
    }

    /**
     * 禁用权限
     */
    public void deactivate() {
        if (this.status == 0) {
            throw new IllegalStateException("权限已是禁用状态");
        }
        this.status = 0;
    }

    /**
     * 判断是否是根节点
     */
    public boolean isRoot() {
        return this.parentId == null || this.parentId == 0;
    }

    /**
     * 判断是否启用
     */
    public boolean isActive() {
        return this.status == 1;
    }

    /**
     * 判断是否可见
     */
    public boolean isVisible() {
        return this.visible == 1;
    }

    /**
     * 判断是否是菜单类型
     */
    public boolean isMenu() {
        return this.permissionType != null && this.permissionType == 1;
    }

    /**
     * 判断是否是按钮类型
     */
    public boolean isButton() {
        return this.permissionType != null && this.permissionType == 2;
    }

    /**
     * 判断是否是接口类型
     */
    public boolean isApi() {
        return this.permissionType != null && this.permissionType == 3;
    }

    /**
     * 添加子权限
     */
    public void addChild(Permission child) {
        if (this.children == null) {
            this.children = new ArrayList<>();
        }
        if (!this.children.contains(child)) {
            this.children.add(child);
        }
    }

    /**
     * 移除子权限
     */
    public void removeChild(Long permissionId) {
        if (this.children != null) {
            this.children.removeIf(p -> p.getPermissionId().equals(permissionId));
        }
    }

    /**
     * 验证权限编码
     */
    public void validatePermissionCode() {
        if (permissionCode == null || permissionCode.trim().isEmpty()) {
            throw new IllegalArgumentException("权限编码不能为空");
        }
        String codeRegex = "^[a-z]+:[a-z]+$";
        if (!permissionCode.matches(codeRegex)) {
            throw new IllegalArgumentException("权限编码格式不正确，应为 module:action 格式（如：system:user:create）");
        }
    }
}
