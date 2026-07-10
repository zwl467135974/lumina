package io.lumina.base.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录请求 DTO
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
public class LoginDTO {

    /**
     * 租户 ID（可选，默认为 0）
     */
    private Long tenantId;

    /**
     * 用户名
     */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度须在 3-50 个字符之间")
    private String username;

    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    @Size(max = 128, message = "密码长度不能超过 128 个字符")
    private String password;
}
