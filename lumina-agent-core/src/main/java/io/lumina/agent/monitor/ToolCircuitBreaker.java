package io.lumina.agent.monitor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 工具调用熔断器
 *
 * <p>基于连续失败计数的简单熔断器：
 * <ul>
 *   <li>CLOSED：正常执行</li>
 *   <li>OPEN：连续失败达阈值，拒绝执行</li>
 *   <li>HALF_OPEN：冷却期过后允许一次试探，成功则关闭，失败则重新打开</li>
 * </ul>
 *
 * @author Lumina Team
 * @since 1.1.0
 */
@Slf4j
@Component
public class ToolCircuitBreaker {

    /**
     * 连续失败阈值（达到后熔断）
     */
    @Value("${lumina.agent.tool.failure-threshold:5}")
    private int failureThreshold;

    /**
     * 熔断后冷却时间（毫秒），过后进入半开
     */
    @Value("${lumina.agent.tool.reset-timeout-ms:60000}")
    private long resetTimeoutMs;

    private final Map<String, BreakerState> breakers = new ConcurrentHashMap<>();

    /**
     * 是否允许执行该工具
     *
     * @return true 允许（CLOSED 或 HALF_OPEN 试探），false 拒绝（OPEN）
     */
    public boolean allowExecution(String toolName) {
        BreakerState s = breakers.computeIfAbsent(toolName, k -> new BreakerState());
        if (!s.open) {
            return true;
        }
        // 检查是否已过冷却期（进入 HALF_OPEN，允许一次试探）
        if (System.currentTimeMillis() - s.openedAt > resetTimeoutMs) {
            log.info("工具熔断器半开，允许试探执行: {}", toolName);
            return true;
        }
        log.warn("工具熔断中，拒绝执行: {}", toolName);
        return false;
    }

    /**
     * 记录成功（重置计数，关闭熔断）
     */
    public void recordSuccess(String toolName) {
        BreakerState s = breakers.get(toolName);
        if (s != null) {
            s.consecutiveFailures.set(0);
            if (s.open) {
                s.open = false;
                log.info("工具熔断器关闭（恢复正常）: {}", toolName);
            }
        }
    }

    /**
     * 记录失败（累加计数，达阈值则熔断）
     */
    public void recordFailure(String toolName) {
        BreakerState s = breakers.computeIfAbsent(toolName, k -> new BreakerState());
        long failures = s.consecutiveFailures.incrementAndGet();
        if (failures >= failureThreshold && !s.open) {
            s.open = true;
            s.openedAt = System.currentTimeMillis();
            log.error("工具熔断器打开: tool={}, 连续失败={} 次, 冷却={}ms", toolName, failures, resetTimeoutMs);
        }
    }

    /**
     * 获取熔断状态（用于查询）
     */
    public Map<String, BreakerState> getBreakerStates() {
        return new ConcurrentHashMap<>(breakers);
    }

    /**
     * 熔断器状态
     */
    @lombok.Data
    public static class BreakerState {
        /** 连续失败次数 */
        private final AtomicLong consecutiveFailures = new AtomicLong();
        /** 是否熔断打开 */
        private volatile boolean open = false;
        /** 熔断打开时间戳 */
        private volatile long openedAt;
    }
}
