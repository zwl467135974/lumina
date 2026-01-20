package io.lumina.base.api.dto.permission;

import io.lumina.common.core.BaseDTO;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 更新权限 DTO
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UpdatePermissionDTO extends BaseDTO {

    /**
     * 权限ID
     */
    private Long permissionId;

    /**
     * 权限名称
     */
    @Size(max = 100, message = "权限名称长度不能超过100")
    private String permissionName;

    /**
     * 路由路径（菜单类型专用）
     */
    @Size(max = 255, message = "路由路径长度不能超过255")
    private String path;

    /**
     * 组件路径（菜单类型专用）
     */
    @Size(max = 255, message = "组件路径长度不能超过255")
    private String component;

    /**
     * 图标
     */
    @Size(max = 100, message = "图标长度不能超过100")
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
}
