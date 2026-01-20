package io.lumina.base.api.dto;

import javax.validation.constraints.NotBlank;

/**
 * 登录请求 DTO
 *
 * @author Lumina Team
 * @since 1.0.0
 */
public class LoginDTO {

    /**
     * 租户 ID（可选，默认为 0）
     */
    private Long tenantId;

    /**
     * 用户名
     */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    private String password;

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
