package io.lumina.gateway.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * API Token 关联用户信息（base 服务校验接口的返回体）
 *
 * <p>与 lumina-business-base 的 {@code ApiTokenUserVO} 字段对齐（Gateway 不依赖 base 模块，
 * 通过 HTTP 反序列化获取）。
 *
 * @author Lumina Team
 * @since 3.4.0
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiTokenUser {

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 租户 ID
     */
    private Long tenantId;

    /**
     * 权限范围（逗号分隔，注入 X-Permissions）
     */
    private String scopes;

    /**
     * 角色（逗号分隔，注入 X-Roles）
     */
    private String roles;
}
