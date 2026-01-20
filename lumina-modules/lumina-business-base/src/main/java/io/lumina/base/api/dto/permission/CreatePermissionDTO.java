package io.lumina.base.api.dto.permission;

import io.lumina.common.core.BaseDTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 创建权限 DTO
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CreatePermissionDTO extends BaseDTO {

    /**
     * 父权限ID（0表示根节点）
     */
    @NotNull(message = "父权限ID不能为空")
    private Long parentId;

    /**
     * 权限编码（唯一标识）
     */
    @NotBlank(message = "权限编码不能为空")
    @Size(max = 100, message = "权限编码长度不能超过100")
    private String permissionCode;

    /**
     * 权限名称
     */
    @NotBlank(message = "权限名称不能为空")
    @Size(max = 100, message = "权限名称长度不能超过100")
    private String permissionName;

    /**
     * 权限类型（1-菜单，2-按钮，3-接口）
     */
    @NotNull(message = "权限类型不能为空")
    private Integer permissionType;

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
}
