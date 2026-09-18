package io.lumina.base.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lumina.base.api.vo.LoginVO;
import io.lumina.base.config.OAuth2Properties;
import io.lumina.base.config.OAuth2Properties.ProviderConfig;
import io.lumina.base.infrastructure.entity.OAuth2IdentityDO;
import io.lumina.base.infrastructure.entity.UserDO;
import io.lumina.base.infrastructure.entity.UserRoleDO;
import io.lumina.base.infrastructure.mapper.OAuth2IdentityMapper;
import io.lumina.base.infrastructure.mapper.UserMapper;
import io.lumina.base.infrastructure.mapper.UserRoleMapper;
import io.lumina.base.service.AuthService;
import io.lumina.base.service.OAuth2LoginService;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.exception.BusinessException;
import io.lumina.common.util.PasswordUtil;
import io.lumina.framework.cache.RedisCacheManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * OAuth2 登录服务实现
 *
 * <p>token 交换走 OpenAI 无关的标准 form POST，Accept: application/json
 * （GitHub 兼容）；userinfo 解析字段可按 Provider 配置（GitHub 用 id/login，
 * OIDC 用 sub/preferred_username）。
 *
 * @author Lumina Team
 * @since 3.12.0
 */
@Slf4j
@Service
public class OAuth2LoginServiceImpl implements OAuth2LoginService {

    /** state 暂存键前缀（Redis，5 分钟单次有效） */
    static final String STATE_KEY_PREFIX = "oauth2:state:";

    /** PKCE code_verifier 暂存键前缀（与 state 同 TTL 单次有效） */
    static final String PKCE_KEY_PREFIX = "oauth2:pkce:";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final Duration STATE_TTL = Duration.ofMinutes(5);

    /** userinfo 原文入库截断（排查用，避免敏感信息全量落库） */
    private static final int RAW_USERINFO_MAX = 2000;

    private final OAuth2Properties properties;
    private final OAuth2IdentityMapper identityMapper;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final AuthService authService;
    private final RedisCacheManager redisCacheManager;
    private final OAuth2HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OAuth2LoginServiceImpl(OAuth2Properties properties,
                                   OAuth2IdentityMapper identityMapper,
                                   UserMapper userMapper,
                                   UserRoleMapper userRoleMapper,
                                   AuthService authService,
                                   RedisCacheManager redisCacheManager,
                                   OAuth2HttpClient httpClient,
                                   ObjectMapper objectMapper) {
        this.properties = properties;
        this.identityMapper = identityMapper;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.authService = authService;
        this.redisCacheManager = redisCacheManager;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Map<String, Object>> listEnabledProviders() {
        List<Map<String, Object>> providers = new ArrayList<>();
        properties.getProviders().forEach((name, config) -> {
            if (config.enabled()) {
                providers.add(config.toView(name));
            }
        });
        return providers;
    }

    @Override
    public String buildAuthorizeUrl(String provider) {
        ProviderConfig config = requireProvider(provider);
        String state = UUID.randomUUID().toString().replace("-", "");
        redisCacheManager.set(STATE_KEY_PREFIX + state, provider, STATE_TTL);

        // PKCE（S256）：verifier 只存本方 Redis，挑战上送授权页；
        // token 交换时回传 verifier，授权码被截获也换不到 token
        String pkceParams = "";
        if (config.isPkce()) {
            String verifier = generateCodeVerifier();
            redisCacheManager.set(PKCE_KEY_PREFIX + state, verifier, STATE_TTL);
            pkceParams = "&code_challenge=" + url(s256Challenge(verifier))
                    + "&code_challenge_method=S256";
        }

        return config.getAuthorizeUrl()
                + (config.getAuthorizeUrl().contains("?") ? "&" : "?")
                + "response_type=code"
                + "&client_id=" + url(config.getClientId())
                + "&redirect_uri=" + url(callbackUrl(provider))
                + "&scope=" + url(config.getScopes() == null ? "" : config.getScopes())
                + "&state=" + url(state)
                + pkceParams;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO handleCallback(String provider, String code, String state) {
        requireProvider(provider);
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "OAuth2 回调缺少 code");
        }
        // state 单次有效：取出即删（防 CSRF 与重放）
        String stateKey = STATE_KEY_PREFIX + (state == null ? "" : state);
        String stateProvider = redisCacheManager.get(stateKey);
        redisCacheManager.delete(stateKey);
        if (stateProvider == null || !stateProvider.equals(provider)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "OAuth2 state 无效或已过期");
        }
        // PKCE verifier 同为单次有效；启用方校验时 verifier 缺失即为异常流
        String verifier = redisCacheManager.get(PKCE_KEY_PREFIX + (state == null ? "" : state));
        redisCacheManager.delete(PKCE_KEY_PREFIX + (state == null ? "" : state));
        if (requireProvider(provider).isPkce() && (verifier == null || verifier.isBlank())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "OAuth2 code_verifier 无效或已过期");
        }

        OAuth2IdentityDO identity = fetchIdentity(provider, code, verifier);
        UserDO user = findOrCreateUser(identity);
        LoginVO login = authService.loginByUserId(user.getUserId());
        log.info("OAuth2 登录成功: provider={}, openId={}, userId={}",
                provider, identity.getOpenId(), user.getUserId());
        return login;
    }

    // ==================== 私有方法 ====================

    private ProviderConfig requireProvider(String provider) {
        ProviderConfig config = properties.getProviders().get(provider);
        if (config == null || !config.enabled()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "OAuth2 Provider 未配置或未启用: " + provider);
        }
        return config;
    }

    /** code → access_token → userinfo → 身份（不含建号） */
    private OAuth2IdentityDO fetchIdentity(String provider, String code, String codeVerifier) {
        ProviderConfig config = requireProvider(provider);
        try {
            Map<String, String> form = new HashMap<>();
            form.put("grant_type", "authorization_code");
            form.put("client_id", config.getClientId());
            form.put("client_secret", config.getClientSecret());
            form.put("code", code);
            form.put("redirect_uri", callbackUrl(provider));
            if (codeVerifier != null && !codeVerifier.isBlank()) {
                form.put("code_verifier", codeVerifier);
            }
            String tokenResponse = httpClient.postForm(config.getTokenUrl(), form);
            JsonNode tokenJson = objectMapper.readTree(tokenResponse);
            if (tokenJson.hasNonNull("error")) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED,
                        "OAuth2 换取 token 失败: " + tokenJson.path("error_description").asText("unknown"));
            }
            String accessToken = tokenJson.path("access_token").asText("");
            if (accessToken.isBlank()) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "OAuth2 响应缺少 access_token");
            }

            String userinfo = httpClient.getBearer(config.getUserinfoUrl(), accessToken);
            JsonNode userJson = objectMapper.readTree(userinfo);
            String openId = textOrNull(userJson, config.getOpenidField());
            if (openId == null || openId.isBlank()) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED,
                        "userinfo 缺少唯一标识字段: " + config.getOpenidField());
            }

            OAuth2IdentityDO identity = new OAuth2IdentityDO();
            identity.setProvider(provider);
            identity.setOpenId(openId);
            identity.setUsername(firstNonBlank(
                    textOrNull(userJson, config.getUsernameField()), provider + "_" + openId));
            identity.setAvatar(textOrNull(userJson, config.getAvatarField()));
            identity.setEmail(textOrNull(userJson, config.getEmailField()));
            String raw = userinfo.length() > RAW_USERINFO_MAX
                    ? userinfo.substring(0, RAW_USERINFO_MAX) : userinfo;
            identity.setRawUserinfo(raw);
            return identity;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("OAuth2 回调处理失败: provider={}, error={}", provider, e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "OAuth2 回调处理失败: " + e.getMessage());
        }
    }

    /** 查绑定用户；首次登录自动建号（随机密码 + 默认角色）并绑定 */
    private UserDO findOrCreateUser(OAuth2IdentityDO identity) {
        OAuth2IdentityDO existing = identityMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OAuth2IdentityDO>()
                        .eq(OAuth2IdentityDO::getProvider, identity.getProvider())
                        .eq(OAuth2IdentityDO::getOpenId, identity.getOpenId())
                        .eq(OAuth2IdentityDO::getIsDeleted, 0)
                        .last("LIMIT 1"));
        if (existing != null) {
            // 更新展示信息（头像/用户名可能变化）
            existing.setUsername(identity.getUsername());
            existing.setAvatar(identity.getAvatar());
            existing.setEmail(identity.getEmail());
            identityMapper.updateById(existing);
            UserDO bound = userMapper.selectById(existing.getUserId());
            if (bound == null || bound.getDeleted() == 1) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED,
                        "三方账号绑定的用户已不存在（provider=" + identity.getProvider()
                                + ", userId=" + existing.getUserId() + "）");
            }
            return bound;
        }

        Long tenantId = properties.getDefaultTenantId() != null ? properties.getDefaultTenantId() : 0L;
        String username = uniqueUsername(identity.getProvider(), identity.getUsername());

        UserDO user = new UserDO();
        user.setTenantId(tenantId);
        user.setUsername(username);
        user.setPassword(PasswordUtil.hash(UUID.randomUUID().toString()));
        user.setNickname(identity.getUsername());
        user.setAvatar(identity.getAvatar());
        user.setEmail(identity.getEmail());
        user.setStatus(1);
        user.setDeleted(0);
        userMapper.insert(user);

        UserRoleDO role = new UserRoleDO();
        role.setUserId(user.getUserId());
        role.setRoleId(properties.getDefaultRoleId());
        userRoleMapper.insert(role);

        identity.setUserId(user.getUserId());
        identity.setTenantId(tenantId);
        identity.setIsDeleted(0);
        identityMapper.insert(identity);
        log.info("OAuth2 首次登录自动建号: provider={}, username={}, userId={}, roleId={}",
                identity.getProvider(), username, user.getUserId(), properties.getDefaultRoleId());
        return user;
    }

    /** 用户名去冲突：前缀 + 提供方用户名，已存在则追加 openId 片段 */
    private String uniqueUsername(String provider, String providerUsername) {
        String prefix = properties.getUsernamePrefix() == null ? "" : properties.getUsernamePrefix();
        String base = prefix + provider + "_" + sanitize(providerUsername);
        if (base.length() > 60) {
            base = base.substring(0, 60);
        }
        String candidate = base;
        int suffix = 0;
        while (userMapper.selectByTenantIdAndUsername(
                properties.getDefaultTenantId() != null ? properties.getDefaultTenantId() : 0L, candidate) != null) {
            suffix++;
            candidate = base + "_" + suffix;
        }
        return candidate;
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.replaceAll("[^a-zA-Z0-9_\\-.]", "");
    }

    private String callbackUrl(String provider) {
        String base = properties.getPublicBaseUrl() == null ? "" : properties.getPublicBaseUrl().trim();
        if (base.isEmpty()) {
            base = "http://localhost:8080";
        }
        return base.replaceAll("/+$", "") + "/api/v1/base/auth/oauth2/" + provider + "/callback";
    }

    private static String textOrNull(JsonNode node, String field) {
        if (field == null || field.isBlank()) {
            return null;
        }
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    /** PKCE code_verifier：64 随机字节的 Base64URL（86 字符，43~128 合法区间） */
    static String generateCodeVerifier() {
        byte[] bytes = new byte[64];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** PKCE code_challenge：BASE64URL(SHA-256(verifier))，method=S256 */
    static String s256Challenge(String verifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(digest.digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static String url(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
