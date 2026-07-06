package io.lumina.agent.security;

import io.lumina.common.core.BaseContext;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.core.LoginContext;
import io.lumina.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * AgentRateLimiter 单元测试
 *
 * @author Lumina Team
 * @since 2.0.0
 */
class AgentRateLimiterTest {

    private RedissonClient redissonClient;
    private RRateLimiter rateLimiter;
    private AgentRateLimiter agentRateLimiter;

    @BeforeEach
    void setUp() throws Exception {
        redissonClient = mock(RedissonClient.class);
        rateLimiter = mock(RRateLimiter.class);

        when(redissonClient.getRateLimiter(anyString())).thenReturn(rateLimiter);
        when(rateLimiter.trySetRate(any(RateType.class), anyLong(), anyLong(), any(RateIntervalUnit.class)))
                .thenReturn(true);

        agentRateLimiter = new AgentRateLimiter(redissonClient);

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
        when(rateLimiter.tryAcquire()).thenReturn(true);

        assertThatCode(() -> agentRateLimiter.checkRateLimit(1L))
                .doesNotThrowAnyException();

        verify(rateLimiter).tryAcquire();
    }

    @Test
    void exceedingLimitThrowsBusinessException() {
        when(rateLimiter.tryAcquire()).thenReturn(false);

        assertThatThrownBy(() -> agentRateLimiter.checkRateLimit(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assert be.getErrorCode() == ErrorCode.AGENT_RATE_LIMITED;
                });

        verify(rateLimiter).tryAcquire();
    }

    @Test
    void rateLimiterKeyIncludesAgentIdAndUserId() {
        when(rateLimiter.tryAcquire()).thenReturn(true);

        agentRateLimiter.checkRateLimit(42L);

        verify(redissonClient).getRateLimiter(eq("agent:rate:42:100"));
    }

    @Test
    void anonymousUserUsesFallbackKey() {
        BaseContext.clear();

        when(rateLimiter.tryAcquire()).thenReturn(true);

        agentRateLimiter.checkRateLimit(1L);

        verify(redissonClient).getRateLimiter(eq("agent:rate:1:anonymous"));
    }

    @Test
    void trySetRateCalledWithConfiguredValues() {
        when(rateLimiter.tryAcquire()).thenReturn(true);

        agentRateLimiter.checkRateLimit(1L);

        verify(rateLimiter).trySetRate(eq(RateType.OVERALL), eq(5L), eq(60L), eq(RateIntervalUnit.SECONDS));
    }
}
