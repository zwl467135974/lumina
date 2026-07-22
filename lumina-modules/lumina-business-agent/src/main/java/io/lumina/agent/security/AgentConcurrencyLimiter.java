package io.lumina.agent.security;

import io.lumina.common.core.ErrorCode;
import io.lumina.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * Agent 并发执行限制器
 *
 * <p>基于 per-agent {@link Semaphore} 实现并发控制，限制同一 Agent 同时执行的任务数。
 * maxConcurrent=0 或 null 时不限制（放行）。
 *
 * <p>信号量按 agentId 缓存，首次访问时按 Agent 配置的 maxConcurrent 创建。
 * 如需动态调整，重启后生效（信号量不热更新，避免并发安全问题）。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class AgentConcurrencyLimiter {

    private final ConcurrentHashMap<Long, ConcurrencySlot> slots = new ConcurrentHashMap<>();

    private record ConcurrencySlot(int maxConcurrent, Semaphore semaphore) {
    }

    /**
     * 获取执行许可（非阻塞）
     *
     * @param agentId       Agent ID
     * @param maxConcurrent 最大并发数（0/null 表示不限制）
     * @return 是否实际获取到许可，调用方仅在返回 true 时释放
     * @throws BusinessException 达到并发上限时抛出 {@link ErrorCode#AGENT_CONCURRENT_LIMITED}
     */
    public boolean acquire(Long agentId, Integer maxConcurrent) {
        if (maxConcurrent == null || maxConcurrent <= 0) {
            return false;
        }

        ConcurrencySlot slot = slots.compute(agentId, (id, existing) -> {
            if (existing == null) {
                return new ConcurrencySlot(maxConcurrent, new Semaphore(maxConcurrent, true));
            }
            if (existing.maxConcurrent() != maxConcurrent
                    && existing.semaphore().availablePermits() == existing.maxConcurrent()) {
                return new ConcurrencySlot(maxConcurrent, new Semaphore(maxConcurrent, true));
            }
            return existing;
        });
        Semaphore semaphore = slot.semaphore();

        if (!semaphore.tryAcquire()) {
            log.warn("Agent 并发限制触发: agentId={}, maxConcurrent={}, available={}",
                    agentId, maxConcurrent, semaphore.availablePermits());
            throw new BusinessException(ErrorCode.AGENT_CONCURRENT_LIMITED);
        }

        log.debug("Agent 获取并发许可: agentId={}, maxConcurrent={}, available={}",
                agentId, maxConcurrent, semaphore.availablePermits());
        return true;
    }

    /**
     * 释放已获取的执行许可
     *
     * @param agentId Agent ID
     */
    public void release(Long agentId) {
        ConcurrencySlot slot = slots.get(agentId);
        if (slot != null) {
            Semaphore semaphore = slot.semaphore();
            semaphore.release();
            log.debug("Agent 释放并发许可: agentId={}, available={}", agentId, semaphore.availablePermits());
        }
    }
}
