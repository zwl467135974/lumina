package io.lumina.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lumina.gateway.client.ApiTokenUser;
import io.lumina.gateway.client.ApiTokenValidationClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * API Token 认证过滤器（OpenAI 兼容端点 /v1/**）
 *
 * <p>拦截 {@code /v1/**} 开放 API 路径，校验 {@code Authorization: Bearer sk-xxx}，
 * 通过后注入身份头（X-User-Id 等）传递给下游服务，401 时返回 OpenAI 风格错误体。
 *
 * <p>{@code /v1/**} 已加入 JWT 白名单，JwtAuthentication 过滤器（order=1，已统一剥离
 * 客户端伪造身份头）先执行放行，本过滤器（order=10）随后注入已校验的身份头。
 *
 * @author Lumina Team
 * @since 3.4.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiTokenAuthGlobalFilter implements GlobalFilter, Ordered {

    /**
     * OpenAI 兼容端点路径前缀
     */
    private static final String OPENAI_PATH_PREFIX = "/v1/";

    /**
     * API Token 明文前缀（Bearer 后）
     */
    private static final String TOKEN_PREFIX = "sk-";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ApiTokenValidationClient apiTokenValidationClient;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 只拦截 /v1/** 开放 API 路径
        if (!path.startsWith(OPENAI_PATH_PREFIX)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer " + TOKEN_PREFIX)) {
            log.warn("缺少或无效的 API Token: path={}", path);
            return unauthorized(exchange, "Missing or invalid API key. Expected 'Authorization: Bearer sk-...'.");
        }

        String cleartext = authHeader.substring(7);

        return apiTokenValidationClient.validate(cleartext)
                .flatMap(user -> {
                    // 注入身份头（客户端伪造的身份头已由 JwtAuthentication 过滤器统一剥离）
                    ServerHttpRequest authenticatedRequest = exchange.getRequest().mutate()
                            .header("X-User-Id", String.valueOf(user.getUserId()))
                            .header("X-Username", user.getUsername() != null ? user.getUsername() : "")
                            .header("X-Tenant-Id", user.getTenantId() != null ? String.valueOf(user.getTenantId()) : "0")
                            .header("X-Roles", user.getRoles() != null ? user.getRoles() : "")
                            .header("X-Permissions", user.getScopes() != null ? user.getScopes() : "")
                            .build();
                    log.debug("API Token 认证成功: userId={}, path={}", user.getUserId(), path);
                    return chain.filter(exchange.mutate().request(authenticatedRequest).build());
                })
                .switchIfEmpty(unauthorized(exchange, "Invalid or expired API key."));
    }

    /**
     * 返回 401 + OpenAI 风格错误体（标准 SDK 可解析）
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            Map<String, Object> body = Map.of("error", Map.of(
                    "message", message,
                    "type", "invalid_request_error",
                    "code", "invalid_api_key"
            ));
            DataBuffer buffer = response.bufferFactory().wrap(OBJECT_MAPPER.writeValueAsBytes(body));
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            log.error("写入 401 响应失败", e);
            DataBuffer buffer = response.bufferFactory()
                    .wrap("{\"error\":{\"message\":\"unauthorized\"}}".getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        }
    }

    @Override
    public int getOrder() {
        // 在 JwtAuthentication 默认过滤器（order=1，负责统一剥离身份头）之后执行，
        // 否则本过滤器注入的身份头会被其剥离
        return 10;
    }
}
