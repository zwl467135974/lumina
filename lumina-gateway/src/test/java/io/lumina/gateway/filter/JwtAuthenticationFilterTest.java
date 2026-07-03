package io.lumina.gateway.filter;

import io.lumina.common.core.LoginUser;
import io.lumina.common.util.JwtUtil;
import io.lumina.gateway.config.WhitelistConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * JwtAuthenticationFilter 单元测试
 *
 * <p>覆盖白名单跳过、无 Token、无效 Token、有效 Token 等场景。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private JwtAuthenticationFilter filterFactory;

    @Mock
    private JwtUtil jwtUtil;

    private WhitelistConfig whitelistConfig;

    @BeforeEach
    void setUp() {
        filterFactory = new JwtAuthenticationFilter();
        whitelistConfig = new WhitelistConfig();
        whitelistConfig.setPaths(Arrays.asList("/api/v1/auth/login", "/actuator/**"));
        ReflectionTestUtils.setField(filterFactory, "jwtUtil", jwtUtil);
        ReflectionTestUtils.setField(filterFactory, "whitelistConfig", whitelistConfig);
    }

    @Test
    void whitelistPathBypassAuth() {
        GatewayFilter filter = filterFactory.apply(new JwtAuthenticationFilter.Config());
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/v1/auth/login")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void actuatorWildcardBypassAuth() {
        GatewayFilter filter = filterFactory.apply(new JwtAuthenticationFilter.Config());
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/actuator/health")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    @Test
    void missingAuthHeaderReturnsUnauthorized() {
        GatewayFilter filter = filterFactory.apply(new JwtAuthenticationFilter.Config());
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/users")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void invalidAuthSchemeReturnsUnauthorized() {
        GatewayFilter filter = filterFactory.apply(new JwtAuthenticationFilter.Config());
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, "Basic abc123")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void invalidTokenReturnsUnauthorized() {
        GatewayFilter filter = filterFactory.apply(new JwtAuthenticationFilter.Config());
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(jwtUtil.validateToken("invalid-token")).thenReturn(false);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void validTokenInjectsUserHeadersAndPassesThrough() {
        GatewayFilter filter = filterFactory.apply(new JwtAuthenticationFilter.Config());

        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(100L);
        loginUser.setUsername("testuser");
        loginUser.setTenantId(1L);
        loginUser.setRoles(new String[]{"admin"});
        loginUser.setPermissions(new String[]{"user:read", "user:write"});

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        when(jwtUtil.validateToken("valid-token")).thenReturn(true);
        when(jwtUtil.parseTokenToLoginUser("valid-token")).thenReturn(loginUser);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
        assertThat(exchange.getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("100");
        assertThat(exchange.getRequest().getHeaders().getFirst("X-Username")).isEqualTo("testuser");
        assertThat(exchange.getRequest().getHeaders().getFirst("X-Tenant-Id")).isEqualTo("1");
        assertThat(exchange.getRequest().getHeaders().getFirst("X-Roles")).isEqualTo("admin");
        assertThat(exchange.getRequest().getHeaders().getFirst("X-Permissions")).isEqualTo("user:read,user:write");
    }

    @Test
    void tokenParseThrowsReturnsUnauthorized() {
        GatewayFilter filter = filterFactory.apply(new JwtAuthenticationFilter.Config());
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer corrupt-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(jwtUtil.validateToken("corrupt-token")).thenReturn(true);
        when(jwtUtil.parseTokenToLoginUser("corrupt-token")).thenThrow(new RuntimeException("parse error"));

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void nullTenantIdDefaultsToZero() {
        GatewayFilter filter = filterFactory.apply(new JwtAuthenticationFilter.Config());

        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(1L);
        loginUser.setUsername("user");
        loginUser.setTenantId(null);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/agents")
                .header(HttpHeaders.AUTHORIZATION, "Bearer tok")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());
        when(jwtUtil.validateToken("tok")).thenReturn(true);
        when(jwtUtil.parseTokenToLoginUser("tok")).thenReturn(loginUser);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getRequest().getHeaders().getFirst("X-Tenant-Id")).isEqualTo("0");
    }
}
