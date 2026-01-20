package io.lumina.base.api.dto.role;

import io.lumina.common.core.BaseDTO;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 更新角色 DTO
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UpdateRoleDTO extends BaseDTO {

    /**
     * 角色ID
     */
    private Long roleId;

    /**
     * 角色名称
     */
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
     * 状态（0-禁用，1-启用）
     */
    private Integer status;
}
