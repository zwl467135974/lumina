package io.lumina.standalone.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lumina.base.api.vo.apitoken.ApiTokenUserVO;
import io.lumina.base.service.ApiTokenService;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.core.LoginUser;
import io.lumina.common.core.R;
import io.lumina.common.util.JwtUtil;
import io.lumina.standalone.config.WhitelistConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 单体模式 JWT 认证过滤器
 *
 * <p>逻辑移植自 gateway 的 JwtAuthenticationGatewayFilterFactory（WebFlux，不能直接复用），
 * 在单体模式下替代 Gateway 完成认证：
 * <ol>
 *   <li>入口先剥离客户端携带的身份头（X-User-Id 等），防止伪造身份透传到下游拦截器</li>
 *   <li>白名单路径直接放行</li>
 *   <li>校验 Authorization: Bearer Token（签名 + 过期时间）</li>
 *   <li>Redis 黑名单检查（Token 是否已登出/撤销）</li>
 *   <li>解析 claims，将用户信息注回 X-User-Id 等请求头，
 *       交由 TenantIsolationInterceptor 初始化 BaseContext</li>
 * </ol>
 *
 * @author Lumina Team
 * @since 3.4.0
 */
@Slf4j
public class StandaloneJwtFilter extends OncePerRequestFilter {

    private static final String TOKEN_BLACKLIST_KEY_PREFIX = "token:blacklist:";
    private static final String PERM_SNAPSHOT_KEY_PREFIX = "user:perms:";

    private static final String[] TRUSTED_IDENTITY_HEADERS = {
            "X-User-Id", "X-Username", "X-Tenant-Id", "X-Roles", "X-Permissions"
    };

    private final JwtUtil jwtUtil;

    private final WhitelistConfig whitelistConfig;

    private final StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper;

    /** API Token 校验服务（可选依赖，未启用 ApiToken 时为 null） */
    private final ApiTokenService apiTokenService;

    public StandaloneJwtFilter(JwtUtil jwtUtil,
                               WhitelistConfig whitelistConfig,
                               StringRedisTemplate stringRedisTemplate,
                               ObjectMapper objectMapper) {
        this(jwtUtil, whitelistConfig, stringRedisTemplate, objectMapper, null);
    }

    public StandaloneJwtFilter(JwtUtil jwtUtil,
                               WhitelistConfig whitelistConfig,
                               StringRedisTemplate stringRedisTemplate,
                               ObjectMapper objectMapper,
                               ApiTokenService apiTokenService) {
        this.jwtUtil = jwtUtil;
        this.whitelistConfig = whitelistConfig;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.apiTokenService = apiTokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 无论是否白名单，先剥离客户端可能携带的身份头，防止伪造身份（mirror gateway 的安全修复）
        IdentityHeaderRequestWrapper wrapped = new IdentityHeaderRequestWrapper(request);

        String path = request.getRequestURI();

        // 检查白名单（支持配置文件配置）
        if (whitelistConfig.isWhitelisted(path)) {
            log.debug("路径在白名单中，跳过认证: path={}", path);
            filterChain.doFilter(wrapped, response);
            return;
        }

        // 获取 Authorization header
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("缺少或无效的 Authorization header: path={}", path);
            rejectUnauthorized(response, "缺少或无效的 Authorization header");
            return;
        }

        // 提取 token
        String token = authHeader.substring(7);

        // API Token 分支：OpenAI 兼容端点（/v1/**）用 sk- 前缀的 API Token 认证
        // 委托给 ApiTokenService 校验，校验通过后注入与 JWT 路径一致的身份头
        if (token.startsWith("sk-")) {
            if (apiTokenService == null) {
                log.warn("API Token 认证未启用（ApiTokenService 未配置）: path={}", path);
                rejectUnauthorized(response, "API Token 认证未启用");
                return;
            }
            try {
                ApiTokenUserVO tokenUser = apiTokenService.validateToken(token);
                if (tokenUser == null) {
                    log.warn("API Token 校验失败或已过期: path={}", path);
                    rejectUnauthorized(response, "无效或已过期的 API Token");
                    return;
                }
                wrapped.setIdentityHeader("X-User-Id", String.valueOf(tokenUser.getUserId()));
                wrapped.setIdentityHeader("X-Username",
                        tokenUser.getUsername() != null ? tokenUser.getUsername() : "");
                wrapped.setIdentityHeader("X-Tenant-Id",
                        tokenUser.getTenantId() != null ? String.valueOf(tokenUser.getTenantId()) : "0");
                wrapped.setIdentityHeader("X-Roles",
                        tokenUser.getRoles() != null ? tokenUser.getRoles() : "");
                wrapped.setIdentityHeader("X-Permissions",
                        tokenUser.getScopes() != null ? tokenUser.getScopes() : "");
                log.debug("API Token 认证成功: userId={}, path={}", tokenUser.getUserId(), path);
                filterChain.doFilter(wrapped, response);
                return;
            } catch (Exception e) {
                log.error("API Token 处理失败: path={}", path, e);
                rejectUnauthorized(response, "API Token 处理失败");
                return;
            }
        }

        try {
            // 验证 token
            if (!jwtUtil.validateToken(token)) {
                log.warn("Token 验证失败: path={}", path);
                rejectUnauthorized(response, "Token 验证失败");
                return;
            }

            // 检查 Token 是否在黑名单中（已登出 / 已撤销）
            if (isTokenBlacklisted(token)) {
                log.warn("Token 已在黑名单中: path={}", path);
                rejectUnauthorized(response, "Token 已失效");
                return;
            }

            // 解析 token 获取用户信息
            LoginUser loginUser = jwtUtil.parseTokenToLoginUser(token);

            // 权限实时缓存：优先 Redis 快照，未命中回退 JWT claims
            String permissionsStr = resolvePermissions(loginUser);

            // 将用户信息注回请求 header，交由 TenantIsolationInterceptor 初始化 BaseContext
            wrapped.setIdentityHeader("X-User-Id", String.valueOf(loginUser.getUserId()));
            wrapped.setIdentityHeader("X-Username", loginUser.getUsername());
            wrapped.setIdentityHeader("X-Tenant-Id",
                    loginUser.getTenantId() != null ? String.valueOf(loginUser.getTenantId()) : "0");
            wrapped.setIdentityHeader("X-Roles",
                    loginUser.getRoles() != null ? String.join(",", loginUser.getRoles()) : "");
            wrapped.setIdentityHeader("X-Permissions", permissionsStr);

            log.debug("JWT 认证成功: username={}, path={}", loginUser.getUsername(), path);

        } catch (Exception e) {
            log.error("Token 处理失败: path={}", path, e);
            rejectUnauthorized(response, "Token 处理失败");
            return;
        }

        filterChain.doFilter(wrapped, response);
    }

    private boolean isTokenBlacklisted(String token) {
        try {
            Boolean exists = stringRedisTemplate.hasKey(TOKEN_BLACKLIST_KEY_PREFIX + token);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.warn("Token 黑名单检查失败，降级放行: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 解析用户权限：优先读 Redis 快照（实时），未命中回退 JWT claims（不写 Redis）
     */
    private String resolvePermissions(LoginUser loginUser) {
        String jwtPerms = loginUser.getPermissions() != null
                ? String.join(",", loginUser.getPermissions()) : "";

        if (loginUser.getUserId() == null) {
            return jwtPerms;
        }

        String key = PERM_SNAPSHOT_KEY_PREFIX + loginUser.getUserId();
        try {
            String cached = stringRedisTemplate.opsForValue().get(key);
            if (cached != null) {
                return cached;
            }
        } catch (Exception e) {
            log.warn("权限快照读取失败，降级 JWT claims: userId={}, error={}",
                    loginUser.getUserId(), e.getMessage());
        }

        return jwtPerms;
    }

    /**
     * 返回 401 统一响应体
     */
    private void rejectUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        R<Void> body = new R<>(HttpStatus.UNAUTHORIZED.value(), message, null);
        body.setErrCode(ErrorCode.UNAUTHORIZED.getCode());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    /**
     * 身份头重写包装器：屏蔽客户端携带的身份头，仅暴露认证通过后注入的可信身份头
     */
    private static class IdentityHeaderRequestWrapper extends HttpServletRequestWrapper {

        /**
         * 认证通过后注入的可信身份头（key 为小写头名）
         */
        private final Map<String, String> identityHeaders = new LinkedHashMap<>();

        IdentityHeaderRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        void setIdentityHeader(String name, String value) {
            identityHeaders.put(name.toLowerCase(), value);
        }

        private boolean isIdentityHeader(String name) {
            for (String trusted : TRUSTED_IDENTITY_HEADERS) {
                if (trusted.equalsIgnoreCase(name)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String getHeader(String name) {
            if (isIdentityHeader(name)) {
                return identityHeaders.get(name.toLowerCase());
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (isIdentityHeader(name)) {
                String value = identityHeaders.get(name.toLowerCase());
                return value != null
                        ? Collections.enumeration(Collections.singletonList(value))
                        : Collections.emptyEnumeration();
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> names = new LinkedHashSet<>();
            Enumeration<String> original = super.getHeaderNames();
            while (original != null && original.hasMoreElements()) {
                String name = original.nextElement();
                if (!isIdentityHeader(name)) {
                    names.add(name);
                }
            }
            names.addAll(identityHeaders.keySet());
            return Collections.enumeration(names);
        }
    }
}
