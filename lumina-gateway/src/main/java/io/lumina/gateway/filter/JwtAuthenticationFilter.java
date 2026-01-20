package io.lumina.gateway.filter;

import io.lumina.common.core.LoginUser;
import io.lumina.common.util.JwtUtil;
import io.lumina.gateway.config.WhitelistConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * JWT 认证过滤器
 *
 * 在 Gateway 中验证 JWT Token
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config> {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private WhitelistConfig whitelistConfig;

    public JwtAuthenticationFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String path = exchange.getRequest().getURI().getPath();

            // 检查白名单（支持配置文件配置）
            if (whitelistConfig.isWhitelisted(path)) {
                log.debug("路径在白名单中，跳过认证: path={}", path);
                return chain.filter(exchange);
            }

            // 获取 Authorization header
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("缺少或无效的 Authorization header: path={}", path);
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            // 提取 token
            String token = authHeader.substring(7);

            try {
                // 验证 token
                if (!jwtUtil.validateToken(token)) {
                    log.warn("Token 验证失败: path={}", path);
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                }

                // 解析 token 获取用户信息
                LoginUser loginUser = jwtUtil.parseTokenToLoginUser(token);

                // 将用户信息添加到请求 header，传递给下游服务
                exchange.getRequest().mutate()
                        .header("X-User-Id", String.valueOf(loginUser.getUserId()))
                        .header("X-Username", loginUser.getUsername())
                        .header("X-Tenant-Id", loginUser.getTenantId() != null ? String.valueOf(loginUser.getTenantId()) : "0")
                        .header("X-Roles", loginUser.getRoles() != null ? String.join(",", loginUser.getRoles()) : "")
                        .header("X-Permissions", loginUser.getPermissions() != null ? String.join(",", loginUser.getPermissions()) : "")
                        .build();

                log.debug("JWT 认证成功: username={}, path={}", loginUser.getUsername(), path);

            } catch (Exception e) {
                log.error("Token 处理失败: path={}", path, e);
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            return chain.filter(exchange);
        };
    }

    public static class Config {
        // 配置属性（如果需要）
    }
}
