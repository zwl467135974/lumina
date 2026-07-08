package io.lumina.agent.security;

import io.lumina.common.core.BaseContext;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Agent 执行频率限制器
 *
 * <p>基于 Redisson {@link RRateLimiter} 实现分布式令牌桶限流，
 * 按 用户 + Agent 维度独立计量，防止单个用户对同一 Agent 过度调用。
 *
 * <p>可配置参数：
 * <ul>
 *   <li>{@code lumina.agent.rate-limit.max-requests} — 窗口内最大请求数（默认 30）</li>
 *   <li>{@code lumina.agent.rate-limit.window-seconds} — 时间窗口秒数（默认 60）</li>
 * </ul>
 *
 * @author Lumina Team
 * @since 2.0.0
 */
@Slf4j
@Component
public class AgentRateLimiter {

    private static final String KEY_PREFIX = "agent:rate:";

    private final RedissonClient redissonClient;

    @Value("${lumina.agent.rate-limit.max-requests:30}")
    private int maxRequests;

    @Value("${lumina.agent.rate-limit.window-seconds:60}")
    private int windowSeconds;

    @Autowired
    public AgentRateLimiter(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 检查当前用户对指定 Agent 的调用频率
     *
     * @param agentId Agent ID
     * @throws BusinessException 超出频率限制时抛出 {@link ErrorCode#AGENT_RATE_LIMITED}
     */
    public void checkRateLimit(Long agentId) {
        Long userId = BaseContext.getUserId();
        String identity = userId != null ? userId.toString() : "anonymous";

        String key = KEY_PREFIX + agentId + ":" + identity;

        try {
            RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
            rateLimiter.trySetRate(RateType.OVERALL, maxRequests, windowSeconds, RateIntervalUnit.SECONDS);

            if (!rateLimiter.tryAcquire()) {
                log.warn("Agent 频率限制触发: agentId={}, userId={}, max={}, window={}s",
                        agentId, userId, maxRequests, windowSeconds);
                throw new BusinessException(ErrorCode.AGENT_RATE_LIMITED);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("频率限制检查失败（Redis 可能不可用），放行请求: agentId={}, error={}", agentId, e.getMessage());
        }
    }
}
