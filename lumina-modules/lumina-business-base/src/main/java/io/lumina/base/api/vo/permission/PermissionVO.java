package io.lumina.base.api.vo.permission;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 权限 VO
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
public class PermissionVO {

    /**
     * 权限ID
     */
    private Long permissionId;

    /**
     * 父权限ID
     */
    private Long parentId;

    /**
     * 权限编码
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
     * 子权限列表（树形结构）
     */
    private List<PermissionVO> children;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
