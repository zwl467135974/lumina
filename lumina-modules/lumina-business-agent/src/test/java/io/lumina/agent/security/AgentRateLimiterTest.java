package io.lumina.agent.security;

import io.lumina.common.core.BaseContext;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.core.LoginContext;
import io.lumina.common.exception.BusinessException;
import io.lumina.framework.cache.RedisCacheManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AgentRateLimiter 单元测试
 *
 * @author Lumina Team
 * @since 2.0.0
 */
class AgentRateLimiterTest {

    private RedisCacheManager redisCacheManager;
    private AgentRateLimiter agentRateLimiter;

    @BeforeEach
    void setUp() throws Exception {
        redisCacheManager = mock(RedisCacheManager.class);
        agentRateLimiter = new AgentRateLimiter(redisCacheManager);

        Field maxRequestsField = AgentRateLimiter.class.getDeclaredField("maxRequests");
        maxRequestsField.setAccessible(true);
        maxRequestsField.set(agentRateLimiter, 5);

        Field windowSecondsField = AgentRateLimiter.class.getDeclaredField("windowSeconds");
        windowSecondsField.setAccessible(true);
        windowSecondsField.set(agentRateLimiter, 60);

        BaseContext.setCurrent(new LoginContext(0L, 100L, "testuser", null, null));
    }

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    @Test
    void withinLimitPasses() {
        when(redisCacheManager.incrementAndGetWithExpire(anyString(), Mockito.any(Duration.class))).thenReturn(1L);

        assertThatCode(() -> agentRateLimiter.checkRateLimit(1L))
                .doesNotThrowAnyException();

        verify(redisCacheManager).incrementAndGetWithExpire(eq("agent:rate:1:100"), eq(Duration.ofSeconds(60)));
    }

    @Test
    void exceedingLimitThrowsBusinessException() {
        when(redisCacheManager.incrementAndGetWithExpire(anyString(), Mockito.any(Duration.class))).thenReturn(6L);

        assertThatThrownBy(() -> agentRateLimiter.checkRateLimit(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assert be.getErrorCode() == ErrorCode.AGENT_RATE_LIMITED;
                });
    }

    @Test
    void rateLimiterKeyIncludesAgentIdAndUserId() {
        when(redisCacheManager.incrementAndGetWithExpire(anyString(), Mockito.any(Duration.class))).thenReturn(1L);

        agentRateLimiter.checkRateLimit(42L);

        verify(redisCacheManager).incrementAndGetWithExpire(eq("agent:rate:42:100"), eq(Duration.ofSeconds(60)));
    }

    @Test
    void firstRequestSetsExpiry() {
        when(redisCacheManager.incrementAndGetWithExpire(anyString(), Mockito.any(Duration.class))).thenReturn(1L);

        agentRateLimiter.checkRateLimit(1L);

        verify(redisCacheManager).incrementAndGetWithExpire(eq("agent:rate:1:100"), eq(Duration.ofSeconds(60)));
    }

    @Test
    void subsequentRequestDoesNotResetExpiry() {
        // 返回 2L 时不应触发 expire（由 RedisCacheManager 内部判断值=1 才 expire，
        // 这里验证 limiter 本身不会再单独调用 expire）
        when(redisCacheManager.incrementAndGetWithExpire(anyString(), Mockito.any(Duration.class))).thenReturn(2L);

        agentRateLimiter.checkRateLimit(1L);

        verify(redisCacheManager).incrementAndGetWithExpire(eq("agent:rate:1:100"), eq(Duration.ofSeconds(60)));
        verify(redisCacheManager, never()).expire(anyString(), Mockito.any(Duration.class));
    }

    @Test
    void redisFailureFailsClosedByDefault() {
        // 默认 fail-closed：Redis 宕机时拒绝请求（安全优先）
        when(redisCacheManager.incrementAndGetWithExpire(anyString(), Mockito.any(Duration.class)))
                .thenThrow(new RuntimeException("Redis down"));

        assertThatThrownBy(() -> agentRateLimiter.checkRateLimit(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assert be.getErrorCode() == ErrorCode.AGENT_RATE_LIMITED;
                });
    }

    @Test
    void redisFailureFailsOpenWhenConfigured() throws Exception {
        // fail-open=true 时 Redis 宕机放行请求（可用性优先）
        when(redisCacheManager.incrementAndGetWithExpire(anyString(), Mockito.any(Duration.class)))
                .thenThrow(new RuntimeException("Redis down"));

        Field failOpenField = AgentRateLimiter.class.getDeclaredField("failOpen");
        failOpenField.setAccessible(true);
        failOpenField.set(agentRateLimiter, true);

        assertThatCode(() -> agentRateLimiter.checkRateLimit(1L))
                .doesNotThrowAnyException();
    }
}
