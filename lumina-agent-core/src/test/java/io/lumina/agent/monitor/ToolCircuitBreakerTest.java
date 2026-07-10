package io.lumina.agent.monitor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ToolCircuitBreaker 单元测试（Resilience4j 实现）
 *
 * <p>验证熔断器的 CLOSED → OPEN → HALF_OPEN → CLOSED 状态转换，
 * 确保新的 Resilience4j 实现与旧手写版本行为一致。
 *
 * <p>构造参数：failureThreshold=5, resetTimeoutMs=1000
 * <ul>
 *   <li>{@code slidingWindowSize=5} — 滑动窗口大小 5</li>
 *   <li>{@code minimumNumberOfCalls=5} — 至少 5 次调用后才评估失败率</li>
 *   <li>{@code failureRateThreshold=100%} — 100% 失败率才打开熔断器</li>
 *   <li>{@code permittedNumberOfCallsInHalfOpenState=1} — 半开状态只允许 1 次试探</li>
 * </ul>
 *
 * @author Lumina Team
 * @since 1.1.0
 */
class ToolCircuitBreakerTest {

    private ToolCircuitBreaker breaker;

    @BeforeEach
    void setUp() {
        breaker = new ToolCircuitBreaker(5, 1000);
    }

    // ==================== 基本状态测试 ====================

    @Test
    void allowExecution_initialState_returnsTrue() {
        assertThat(breaker.allowExecution("testTool")).isTrue();
    }

    @Test
    void recordFailure_belowThreshold_stillAllows() {
        for (int i = 0; i < 4; i++) {
            breaker.allowExecution("testTool");
            breaker.recordFailure("testTool");
        }
        assertThat(breaker.allowExecution("testTool")).isTrue();
    }

    @Test
    void recordFailure_atThreshold_blocksExecution() {
        for (int i = 0; i < 5; i++) {
            breaker.allowExecution("testTool");
            breaker.recordFailure("testTool");
        }
        assertThat(breaker.allowExecution("testTool")).isFalse();
    }

    @Test
    void recordSuccess_resetsFailures() {
        for (int i = 0; i < 4; i++) {
            breaker.allowExecution("testTool");
            breaker.recordFailure("testTool");
        }
        breaker.recordSuccess("testTool");
        assertThat(breaker.allowExecution("testTool")).isTrue();
    }

    @Test
    void multipleTools_haveIndependentBreakers() {
        for (int i = 0; i < 5; i++) {
            breaker.allowExecution("toolA");
            breaker.recordFailure("toolA");
        }
        assertThat(breaker.allowExecution("toolA")).isFalse();
        assertThat(breaker.allowExecution("toolB")).isTrue();
    }

    // ==================== 半开状态测试 ====================

    @Test
    void halfOpen_singleProbeOnly() throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            breaker.allowExecution("testTool");
            breaker.recordFailure("testTool");
        }
        assertThat(breaker.allowExecution("testTool")).isFalse();

        Thread.sleep(1100);

        assertThat(breaker.allowExecution("testTool")).isTrue();
        assertThat(breaker.allowExecution("testTool")).isFalse();
    }

    @Test
    void halfOpen_probeSuccess_closesCircuit() throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            breaker.allowExecution("testTool");
            breaker.recordFailure("testTool");
        }
        assertThat(breaker.allowExecution("testTool")).isFalse();

        Thread.sleep(1100);

        assertThat(breaker.allowExecution("testTool")).isTrue();
        breaker.recordSuccess("testTool");
        assertThat(breaker.allowExecution("testTool")).isTrue();
        assertThat(breaker.allowExecution("testTool")).isTrue();
    }

    @Test
    void halfOpen_probeFailure_reopensCircuit() throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            breaker.allowExecution("testTool");
            breaker.recordFailure("testTool");
        }
        assertThat(breaker.allowExecution("testTool")).isFalse();

        Thread.sleep(1100);

        assertThat(breaker.allowExecution("testTool")).isTrue();
        breaker.recordFailure("testTool");
        assertThat(breaker.allowExecution("testTool")).isFalse();
    }

    // ==================== 监控状态测试 ====================

    @Test
    void getBreakerStates_closedWhenHealthy() {
        breaker.allowExecution("testTool");
        breaker.recordSuccess("testTool");

        var states = breaker.getBreakerStates();
        assertThat(states).containsKey("testTool");
        assertThat(states.get("testTool").isOpen()).isFalse();
    }

    @Test
    void getBreakerStates_openWhenTripped() {
        for (int i = 0; i < 5; i++) {
            breaker.allowExecution("testTool");
            breaker.recordFailure("testTool");
        }

        var states = breaker.getBreakerStates();
        assertThat(states.get("testTool").isOpen()).isTrue();
        assertThat(states.get("testTool").getOpenedAt()).isGreaterThan(0L);
    }
}
