package io.lumina.base.api.dto.user;

import io.lumina.common.core.BaseDTO;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 更新用户 DTO
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UpdateUserDTO extends BaseDTO {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 真实姓名
     */
    @Size(max = 100, message = "真实姓名长度不能超过100")
    private String realName;

    /**
     * 邮箱
     */
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过100")
    private String email;

    /**
     * 手机号
     */
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /**
     * 头像URL
     */
    @Size(max = 255, message = "头像URL长度不能超过255")
    private String avatar;

    /**
     * 状态（0-禁用，1-启用）
     */
    private Integer status;
}
