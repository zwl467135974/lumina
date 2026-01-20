package io.lumina.base.api.dto.user;

import io.lumina.common.core.BaseDTO;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 分配角色 DTO
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AssignRoleDTO extends BaseDTO {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 角色ID列表
     */
    @NotEmpty(message = "角色ID列表不能为空")
    private List<Long> roleIds;
}
