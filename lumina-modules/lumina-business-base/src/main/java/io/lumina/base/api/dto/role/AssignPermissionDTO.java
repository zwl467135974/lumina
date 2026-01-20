package io.lumina.base.api.dto.role;

import io.lumina.common.core.BaseDTO;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 分配权限 DTO
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AssignPermissionDTO extends BaseDTO {

    /**
     * 角色ID
     */
    private Long roleId;

    /**
     * 权限ID列表
     */
    @NotEmpty(message = "权限ID列表不能为空")
    private List<Long> permissionIds;
}
