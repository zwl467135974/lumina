package io.lumina.base.api.dto.role;

import io.lumina.common.core.BaseDTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 创建角色 DTO
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CreateRoleDTO extends BaseDTO {

    /**
     * 角色编码（租户内唯一）
     */
    @NotBlank(message = "角色编码不能为空")
    @Size(min = 2, max = 50, message = "角色编码长度必须在2-50之间")
    private String roleCode;

    /**
     * 角色名称
     */
    @NotBlank(message = "角色名称不能为空")
    @Size(max = 100, message = "角色名称长度不能超过100")
    private String roleName;

    /**
     * 角色描述
     */
    @Size(max = 500, message = "角色描述长度不能超过500")
    private String description;

    /**
     * 排序序号
     */
    private Integer sortOrder;

    /**
     * 权限ID列表（创建角色时分配权限）
     */
    private Long[] permissionIds;
}
