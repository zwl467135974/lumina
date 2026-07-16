package io.lumina.base.api.dto.user;

import io.lumina.common.core.BaseDTO;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 自助修改密码 DTO
 *
 * <p>用户自行修改密码时提交，需验证旧密码。userId 从 BaseContext 获取（防 IDOR）。
 *
 * @author Lumina Team
 * @since 3.3.1
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ChangePasswordDTO extends BaseDTO {

    /**
     * 当前密码（旧密码）
     */
    @NotBlank(message = "当前密码不能为空")
    private String oldPassword;

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
