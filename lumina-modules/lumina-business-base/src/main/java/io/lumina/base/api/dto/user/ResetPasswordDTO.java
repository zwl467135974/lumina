package io.lumina.base.api.dto.user;

import io.lumina.common.core.BaseDTO;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 重置密码 DTO
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ResetPasswordDTO extends BaseDTO {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 新密码
     */
    @NotBlank(message = "新密码不能为空")
    private String newPassword;

    /**
     * 确认密码
     */
    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;
}
