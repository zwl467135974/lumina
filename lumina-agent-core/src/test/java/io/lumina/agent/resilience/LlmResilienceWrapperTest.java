package io.lumina.agent.resilience;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LlmResilienceWrapper 单元测试
 *
 * <p>覆盖正常执行、可重试异常重试、不可重试异常直接抛出、熔断器状态。
 *
 * @author Lumina Team
 * @since 2.0.0
 */
class LlmResilienceWrapperTest {

    private LlmResilienceWrapper wrapper;

    @BeforeEach
    void setUp() {
        wrapper = new LlmResilienceWrapper();
    }

    @Test
    void executeReturnsResultOnSuccess() {
        String result = wrapper.execute("test-call", () -> "hello");
        assertThat(result).isEqualTo("hello");
    }

    @Test
    void executeRetriesOnConnectionRefused() {
        AtomicInteger attempts = new AtomicInteger(0);
        Supplier<String> op = () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new RuntimeException("connection refused");
            }
            return "success-on-3rd";
        };

        String result = wrapper.execute("retry-test", op);

        assertThat(result).isEqualTo("success-on-3rd");
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void executeRetriesOnTimeoutMessage() {
        AtomicInteger attempts = new AtomicInteger(0);
        Supplier<String> op = () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new RuntimeException("read timeout");
            }
            return "ok";
        };

        String result = wrapper.execute("timeout-retry", op);
        assertThat(result).isEqualTo("ok");
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void executeRetriesOn503Message() {
        AtomicInteger attempts = new AtomicInteger(0);
        Supplier<String> op = () -> {
            if (attempts.incrementAndGet() < 2) {
                throw new RuntimeException("server returned 503");
            }
            return "recovered";
        };

        String result = wrapper.execute("503-retry", op);
        assertThat(result).isEqualTo("recovered");
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    void executeRetriesOn429Message() {
        AtomicInteger attempts = new AtomicInteger(0);
        Supplier<String> op = () -> {
            if (attempts.incrementAndGet() < 2) {
                throw new RuntimeException("Too Many Requests");
            }
            return "ok";
        };

        String result = wrapper.execute("429-retry", op);
        assertThat(result).isEqualTo("ok");
    }

    @Test
    void executeDoesNotRetryOnBusinessException() {
        AtomicInteger attempts = new AtomicInteger(0);
        Supplier<String> op = () -> {
            attempts.incrementAndGet();
            throw new IllegalArgumentException("invalid param");
        };

        assertThatThrownBy(() -> wrapper.execute("biz-error", op))
                .isInstanceOf(RuntimeException.class);

        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    void executeDoesNotRetryOnGenericRuntimeException() {
        AtomicInteger attempts = new AtomicInteger(0);
        Supplier<String> op = () -> {
            attempts.incrementAndGet();
            throw new RuntimeException("something unexpected");
        };

        assertThatThrownBy(() -> wrapper.execute("generic-error", op))
                .isInstanceOf(RuntimeException.class);

        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    void executeFailsAfterMaxRetries() {
        AtomicInteger attempts = new AtomicInteger(0);
        Supplier<String> op = () -> {
            attempts.incrementAndGet();
            throw new RuntimeException("connection refused");
        };

        assertThatThrownBy(() -> wrapper.execute("exhaust-retries", op))
                .isInstanceOf(RuntimeException.class);

        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void circuitBreakerInitiallyClosed() {
        assertThat(wrapper.isCircuitBreakerOpen()).isFalse();
    }

    @Test
    void executeNullResultAllowed() {
        String result = wrapper.execute("null-test", () -> null);
        assertThat(result).isNull();
    }
}
