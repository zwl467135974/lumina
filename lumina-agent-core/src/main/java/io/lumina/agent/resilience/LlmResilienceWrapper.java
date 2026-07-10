package io.lumina.agent.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * LLM 调用容错包装器
 *
 * <p>提供重试（3 次指数退避）+ 熔断（错误率 > 50% 开启）的组合容错策略。
 *
 * <p>使用方式：
 * <pre>{@code
 * Msg response = llmResilience.execute("agent-chat", () -> agent.call(messages).block());
 * }</pre>
 *
 * @author Lumina Team
 * @since 1.3.0
 */
@Slf4j
@Component
public class LlmResilienceWrapper {

    @Value("${lumina.agent.llm.retry.max-attempts:3}")
    private int retryMaxAttempts;

    @Value("${lumina.agent.llm.retry.wait-ms:500}")
    private long retryWaitMs;

    private Retry retry;
    private CircuitBreaker circuitBreaker;

    public LlmResilienceWrapper() {
        this.retryMaxAttempts = 3;
        this.retryWaitMs = 500;
        build();
    }

    @PostConstruct
    private void rebuildFromConfig() {
        build();
    }

    private void build() {
        this.retry = Retry.of("llm-retry", RetryConfig.custom()
                .maxAttempts(retryMaxAttempts)
                .waitDuration(Duration.ofMillis(retryWaitMs))
                .retryOnException(this::isRetryable)
                .retryOnResult(result -> false)
                .build());

        this.circuitBreaker = CircuitBreaker.of("llm-circuit-breaker", CircuitBreakerConfig.custom()
                .failureRateThreshold(50.0f)
                .slowCallRateThreshold(80.0f)
                .slowCallDurationThreshold(Duration.ofSeconds(30))
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .permittedNumberOfCallsInHalfOpenState(3)
                .build());

        this.retry.getEventPublisher().onRetry(e ->
                log.warn("LLM 调用重试: attempt={}/{}, wait={}ms",
                        e.getNumberOfRetryAttempts(), retryMaxAttempts, e.getWaitInterval().toMillis()));

        this.circuitBreaker.getEventPublisher()
                .onCallNotPermitted(e ->
                        log.error("LLM 熔断器开启，请求被拒绝。请稍后重试或检查 LLM 服务状态"))
                .onError(e ->
                        log.error("LLM 熔断器记录错误: {}", e.getThrowable().getMessage()))
                .onStateTransition(e ->
                        log.warn("LLM 熔断器状态转换: {} → {}", e.getStateTransition().getFromState(),
                                e.getStateTransition().getToState()));

        log.info("LLM 容错包装器初始化: retry(maxAttempts={}, wait={}ms), circuitBreaker(failureRate=50%, waitOpen=10s)",
                retryMaxAttempts, retryWaitMs);
    }

    /**
     * 执行 LLM 调用（重试 + 熔断保护）
     *
     * @param operation 调用操作
     * @param <T>       返回类型
     * @return 调用结果
     * @throws RuntimeException 熔断器开启或重试耗尽后抛出
     */
    public <T> T execute(String callName, Supplier<T> operation) {
        Supplier<T> retryable = Retry.decorateSupplier(retry, operation);
        Supplier<T> resilient = CircuitBreaker.decorateSupplier(circuitBreaker, retryable);
        return resilient.get();
    }

    /**
     * 判断异常是否可重试（网络超时/5xx 重试，业务异常不重试）
     */
    private boolean isRetryable(Throwable throwable) {
        if (throwable instanceof java.io.IOException
                || throwable instanceof java.util.concurrent.TimeoutException) {
            return true;
        }
        String msg = throwable.getMessage();
        if (msg != null && (msg.contains("timeout")
                || msg.contains("connection refused")
                || msg.contains("502")
                || msg.contains("503")
                || msg.contains("504")
                || msg.contains("Too Many Requests")
                || msg.contains("429"))) {
            return true;
        }
        return false;
    }

    /**
     * 熔断器是否当前开启
     */
    public boolean isCircuitBreakerOpen() {
        return circuitBreaker.getState() == CircuitBreaker.State.OPEN;
    }
}
