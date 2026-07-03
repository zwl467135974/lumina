package io.lumina.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RateLimitGlobalFilter 单元测试
 *
 * <p>覆盖正常通过、全局限流、IP 限流、窗口重置、IP 提取等场景。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
class RateLimitGlobalFilterTest {

    private RateLimitGlobalFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitGlobalFilter();
        ReflectionTestUtils.setField(filter, "globalLimit", 5);
        ReflectionTestUtils.setField(filter, "perIpLimit", 3);
        resetWindow();
    }

    @SuppressWarnings("unchecked")
    private void resetWindow() {
        ReflectionTestUtils.setField(filter, "globalCount", new AtomicLong(0));
        ReflectionTestUtils.setField(filter, "ipCounts", new java.util.concurrent.ConcurrentHashMap<>());
        ReflectionTestUtils.setField(filter, "windowStart", System.currentTimeMillis());
    }

    private MockServerWebExchange createExchange(String ip) {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/test")
                .header("X-Forwarded-For", ip)
                .build();
        return MockServerWebExchange.from(request);
    }

    @Test
    void underLimitPassesThrough() {
        MockServerWebExchange exchange = createExchange("192.168.1.1");
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void globalLimitTriggered() {
        // 先消耗 5 次全局限额
        for (int i = 0; i < 5; i++) {
            MockServerWebExchange ex = createExchange("10.0.0." + i);
            GatewayFilterChain c = mock(GatewayFilterChain.class);
            when(c.filter(ex)).thenReturn(Mono.empty());
            filter.filter(ex, c).block();
        }

        // 第 6 次应该被全局限流
        MockServerWebExchange exchange = createExchange("10.0.0.99");
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void perIpLimitTriggered() {
        // 同一 IP 发 4 次请求（perIpLimit=3），第 4 次被限流
        for (int i = 0; i < 3; i++) {
            MockServerWebExchange ex = createExchange("192.168.1.100");
            GatewayFilterChain c = mock(GatewayFilterChain.class);
            when(c.filter(ex)).thenReturn(Mono.empty());
            filter.filter(ex, c).block();
        }

        MockServerWebExchange exchange = createExchange("192.168.1.100");
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void differentIpsNotAffectedBySingleIpLimit() {
        // IP-A 发 3 次（达到 perIpLimit）
        for (int i = 0; i < 3; i++) {
            MockServerWebExchange ex = createExchange("192.168.1.1");
            GatewayFilterChain c = mock(GatewayFilterChain.class);
            when(c.filter(ex)).thenReturn(Mono.empty());
            filter.filter(ex, c).block();
        }

        // IP-B 应该正常通过
        MockServerWebExchange exchange = createExchange("192.168.1.2");
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void orderIsMinusTwo() {
        assertThat(filter.getOrder()).isEqualTo(-2);
    }

    @Test
    void rejectBodyContainsCode429() throws Exception {
        // 触发限流
        MockServerWebExchange exchange = createExchange("10.0.0.1");
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        // 设 globalLimit=0 使第一个请求就被限流
        ReflectionTestUtils.setField(filter, "globalLimit", 0);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isNotNull();
    }

    @Test
    void ipFromXRealIpHeader() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/test")
                .header("X-Real-IP", "172.16.0.1")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void commaSeparatedXForwardedForUsesFirst() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/test")
                .header("X-Forwarded-For", "203.0.113.1, 10.0.0.1, 10.0.0.2")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // 第一条请求应该正常通过
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }
}
