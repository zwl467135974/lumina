package io.lumina.agent.security;

import io.lumina.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AgentConcurrencyLimiter 单元测试
 *
 * @author Lumina Team
 * @since 3.4.0
 */
class AgentConcurrencyLimiterTest {

    @Test
    void acquireRejectsWhenAgentReachesConcurrentLimit() {
        AgentConcurrencyLimiter limiter = new AgentConcurrencyLimiter();

        assertThat(limiter.acquire(1L, 1)).isTrue();
        assertThatThrownBy(() -> limiter.acquire(1L, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("并发数已达上限");

        limiter.release(1L);

        assertThat(limiter.acquire(1L, 1)).isTrue();
        limiter.release(1L);
    }

    @Test
    void acquireDoesNotRequireReleaseWhenConcurrencyIsUnlimited() {
        AgentConcurrencyLimiter limiter = new AgentConcurrencyLimiter();

        assertThat(limiter.acquire(1L, 0)).isFalse();
        assertThat(limiter.acquire(1L, null)).isFalse();
    }
}
