package io.lumina.base.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OAuth2 第三方登录配置（lumina.oauth2.*）
 *
 * <p>Provider 在 yml/env 配置：client-id 非空即视为启用。GitHub 预置
 * 端点已在 standalone yml 中给出，通用 OIDC 自行覆盖全部 URL。
 *
 * @author Lumina Team
 * @since 3.12.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "lumina.oauth2")
public class OAuth2Properties {

    /** 登录成功后携带 token 重定向回前端的地址 */
    private String frontendRedirectUri = "http://localhost:5173/oauth2/callback";

    /** 对外可达的服务基地址（构造 redirect_uri 用；空则取请求 Host） */
    private String publicBaseUrl = "";

    /** 首次登录自动建号的目标租户 */
    private Long defaultTenantId = 0L;

    /** 首次登录自动建号绑定的默认角色 ID（默认 4=TENANT_USER） */
    private Long defaultRoleId = 4L;

    /** 用户名前缀，避免与本地账号冲突（如 github_octocat） */
    private String usernamePrefix = "";

    private Map<String, ProviderConfig> providers = new LinkedHashMap<>();

    /**
     * 单个 Provider 配置
     */
    @Data
    public static class ProviderConfig {

        private String clientId = "";

        private String clientSecret = "";

        private String authorizeUrl = "";

        private String tokenUrl = "";

        private String userinfoUrl = "";

        /** 空格分隔的授权范围（github: read:user；OIDC: openid profile email） */
        private String scopes = "";

        /** userinfo JSON 中取用户唯一标识的字段（github: id；OIDC: sub） */
        private String openidField = "id";

        /** userinfo JSON 中取用户名的字段（github: login；OIDC: preferred_username） */
        private String usernameField = "login";

        /** userinfo JSON 中取头像/邮箱的字段（可空） */
        private String avatarField = "avatar_url";

        private String emailField = "email";

        /** 展示名（登录页按钮），空则用配置键 */
        private String displayName = "";

        public boolean enabled() {
            return clientId != null && !clientId.isBlank()
                    && authorizeUrl != null && !authorizeUrl.isBlank()
                    && tokenUrl != null && !tokenUrl.isBlank()
                    && userinfoUrl != null && !userinfoUrl.isBlank();
        }

        public Map<String, Object> toView(String name) {
            Map<String, Object> view = new HashMap<>();
            view.put("name", name);
            view.put("displayName", displayName == null || displayName.isBlank() ? name : displayName);
            return view;
        }
    }
}
