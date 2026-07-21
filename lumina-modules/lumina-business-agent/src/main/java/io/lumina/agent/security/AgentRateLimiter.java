package io.lumina.agent.security;

import io.lumina.common.core.BaseContext;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.exception.BusinessException;
import io.lumina.framework.cache.RedisCacheManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Agent 执行频率限制器
 *
 * <p>基于 {@link RedisCacheManager} 原子计数器实现固定窗口限流，
 * 按 用户 + Agent 维度独立计量，防止单个用户对同一 Agent 过度调用。
 *
 * <p>可配置参数：
 * <ul>
 *   <li>{@code lumina.agent.rate-limit.max-requests} — 窗口内最大请求数（默认 30）</li>
 *   <li>{@code lumina.agent.rate-limit.window-seconds} — 时间窗口秒数（默认 60）</li>
 *   <li>{@code lumina.agent.rate-limit.fail-open} — Redis 不可用时是否放行（默认 false，即 fail-closed 拒绝请求）</li>
 * </ul>
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRateLimiter {

    private static final String KEY_PREFIX = "agent:rate:";

    private final RedisCacheManager redisCacheManager;

    @Value("${lumina.agent.rate-limit.max-requests:30}")
    private int maxRequests;

    @Value("${lumina.agent.rate-limit.window-seconds:60}")
    private int windowSeconds;

    /**
     * Redis 不可用时的策略：true=放行（fail-open），false=拒绝（fail-closed，默认）
     *
     * <p>安全场景默认 fail-closed（拒绝请求），避免限流被绕过；
     * 可用性优先场景可设为 true（放行请求），避免 Redis 抖动导致全局不可用。
     */
    @Value("${lumina.agent.rate-limit.fail-open:false}")
    private boolean failOpen;

    /**
     * 检查当前用户对指定 Agent 的调用频率（使用全局默认限额，向后兼容）
     *
     * @param agentId Agent ID
     * @throws BusinessException 超出频率限制时抛出 {@link ErrorCode#AGENT_RATE_LIMITED}
     */
    public void checkRateLimit(Long agentId) {
        checkRateLimit(agentId, null);
    }

    /**
     * 检查当前用户对指定 Agent 的调用频率
     *
     * <p>当 {@code perAgentLimit} 不为 null 且大于 0 时，使用 Agent 专属限额替代全局默认；
     * 否则回退到全局配置 {@code lumina.agent.rate-limit.max-requests}。
     *
     * @param agentId        Agent ID
     * @param perAgentLimit  Agent 专属每分钟最大请求数（null/<=0 表示用全局默认）
     * @throws BusinessException 超出频率限制时抛出 {@link ErrorCode#AGENT_RATE_LIMITED}
     */
    public void checkRateLimit(Long agentId, Integer perAgentLimit) {
        // 解析生效限额：优先 Agent 专属配置，否则用全局默认
        int effectiveMax = (perAgentLimit != null && perAgentLimit > 0) ? perAgentLimit : maxRequests;

        Long userId = BaseContext.getUserId();
        String identity = userId != null ? userId.toString() : "anonymous";

        String key = KEY_PREFIX + agentId + ":" + identity;

        try {
            long count = redisCacheManager.incrementAndGet(key);
            if (count == 1) {
                redisCacheManager.expire(key, Duration.ofSeconds(windowSeconds));
            }

            if (count > effectiveMax) {
                log.warn("Agent 频率限制触发: agentId={}, userId={}, max={}, window={}s",
                        agentId, userId, effectiveMax, windowSeconds);
                throw new BusinessException(ErrorCode.AGENT_RATE_LIMITED);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            if (failOpen) {
                log.warn("频率限制检查失败（Redis 可能不可用），fail-open 模式放行请求: agentId={}, error={}",
                        agentId, e.getMessage());
            } else {
                log.error("频率限制检查失败（Redis 可能不可用），fail-closed 模式拒绝请求: agentId={}, error={}",
                        agentId, e.getMessage());
                throw new BusinessException(ErrorCode.AGENT_RATE_LIMITED,
                        "限流服务暂时不可用，请稍后重试");
            }
        }
    }
}
