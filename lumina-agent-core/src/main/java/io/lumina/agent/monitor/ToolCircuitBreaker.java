package io.lumina.agent.monitor;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 工具调用熔断器（基于 Resilience4j）
 *
 * <p>使用 Resilience4j {@link CircuitBreakerRegistry} 管理每个工具的熔断器，
 * 状态转换由 Resilience4j 保证正确性，无需手写 synchronized。
 *
 * <p>外部 API 保持不变：
 * <ul>
 *   <li>{@link #allowExecution(String)} — 基于 {@code tryAcquirePermission}，原子且非阻塞</li>
 *   <li>{@link #recordSuccess(String)} / {@link #recordFailure(String)} — 反馈执行结果</li>
 *   <li>{@link #getBreakerStates()} — 返回监控状态快照</li>
 * </ul>
 *
 * @author Lumina Team
 * @since 1.1.0
 */
@Slf4j
@Component
public class ToolCircuitBreaker {

    private final CircuitBreakerRegistry registry;

    private final Set<String> listenersRegistered = ConcurrentHashMap.newKeySet();

    private final Map<String, Long> openedAtMap = new ConcurrentHashMap<>();

    public ToolCircuitBreaker(
            @Value("${lumina.agent.tool.failure-threshold:5}") int failureThreshold,
            @Value("${lumina.agent.tool.reset-timeout-ms:60000}") long resetTimeoutMs) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(100f)
                .slidingWindowSize(failureThreshold)
                .minimumNumberOfCalls(failureThreshold)
                .waitDurationInOpenState(Duration.ofMillis(resetTimeoutMs))
                .permittedNumberOfCallsInHalfOpenState(1)
                .build();
        this.registry = CircuitBreakerRegistry.of(config);
    }

    public boolean allowExecution(String toolName) {
        return getOrCreate(toolName).tryAcquirePermission();
    }

    public void recordSuccess(String toolName) {
        CircuitBreaker cb = registry.find(toolName).orElse(null);
        if (cb != null) {
            cb.onSuccess(0, TimeUnit.NANOSECONDS);
        }
    }

    public void recordFailure(String toolName) {
        CircuitBreaker cb = registry.find(toolName).orElse(null);
        if (cb != null) {
            cb.onError(0, TimeUnit.NANOSECONDS, new RuntimeException("tool execution failed"));
        }
    }

    public Map<String, BreakerState> getBreakerStates() {
        Map<String, BreakerState> result = new LinkedHashMap<>();
        for (CircuitBreaker cb : registry.getAllCircuitBreakers()) {
            String name = cb.getName();
            BreakerState state = new BreakerState();
            CircuitBreaker.State cs = cb.getState();
            state.setOpen(cs == CircuitBreaker.State.OPEN || cs == CircuitBreaker.State.HALF_OPEN);
            state.getConsecutiveFailures().set(cb.getMetrics().getNumberOfFailedCalls());
            state.setOpenedAt(openedAtMap.getOrDefault(name, 0L));
            result.put(name, state);
        }
        return result;
    }

    private CircuitBreaker getOrCreate(String toolName) {
        CircuitBreaker cb = registry.circuitBreaker(toolName);
        if (listenersRegistered.add(toolName)) {
            cb.getEventPublisher().onStateTransition(event -> onStateTransition(toolName, event));
        }
        return cb;
    }

    private void onStateTransition(String toolName, CircuitBreakerOnStateTransitionEvent event) {
        CircuitBreaker.State target = event.getStateTransition().getToState();
        if (target == CircuitBreaker.State.OPEN) {
            openedAtMap.put(toolName, System.currentTimeMillis());
            log.error("工具熔断器打开: tool={}, 转换={}", toolName, event.getStateTransition());
        } else if (target == CircuitBreaker.State.CLOSED) {
            openedAtMap.remove(toolName);
            log.info("工具熔断器关闭（恢复正常）: {}", toolName);
        }
    }

    /**
     * 熔断器状态快照（用于监控）
     */
    @lombok.Data
    public static class BreakerState {
        /** 当前窗口内失败次数 */
        private final AtomicLong consecutiveFailures = new AtomicLong();
        /** 是否熔断打开（OPEN 或 HALF_OPEN） */
        private volatile boolean open = false;
        /** 熔断打开时间戳 */
        private volatile long openedAt;
    }
}
