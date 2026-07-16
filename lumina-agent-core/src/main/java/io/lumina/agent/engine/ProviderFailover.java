package io.lumina.agent.engine;

import io.agentscope.core.message.Msg;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Supplier;

/**
 * Provider Failover 执行器
 *
 * <p>按优先级依次尝试 Provider 链，可重试异常（超时/5xx/429）触发 failover，
 * 不可重试异常（鉴权/参数错误）直接抛出。
 *
 * @author Lumina Team
 * @since 3.3.1
 */
@Slf4j
public final class ProviderFailover {

    private ProviderFailover() {}

    /**
     * 按 Provider 链执行，失败时自动切换到下一个
     *
     * @param providers Provider 执行链（按优先级排序）
     * @param names     Provider 名称（用于日志）
     * @return 第一个成功的执行结果
     * @throws RuntimeException 全部失败时抛出最后一个异常
     */
    public static Msg executeWithFailover(List<Supplier<Msg>> providers, List<String> names) {
        if (providers == null || providers.isEmpty()) {
            throw new IllegalArgumentException("Provider 链不能为空");
        }

        Exception lastException = null;
        for (int i = 0; i < providers.size(); i++) {
            String name = (names != null && i < names.size()) ? names.get(i) : "provider-" + i;
            try {
                Msg result = providers.get(i).get();
                if (result != null) {
                    if (i > 0) {
                        log.info("Failover 成功: 已切换到 {} (第 {}/{} 个 Provider)", name, i + 1, providers.size());
                    }
                    return result;
                }
                log.warn("Provider {} 返回空响应，尝试下一个", name);
                lastException = new IllegalStateException("Provider " + name + " 返回空响应");
            } catch (Exception e) {
                lastException = e;
                if (isFailoverEligible(e)) {
                    log.warn("Provider {} 失败（{}），切换到下一个 Provider", name, e.getMessage());
                } else {
                    // 不可重试异常（鉴权/参数错误），直接抛出
                    log.error("Provider {} 失败（不可重试），终止 failover: {}", name, e.getMessage());
                    throw e;
                }
            }
        }

        // 全部失败
        throw new RuntimeException("所有 Provider 均失败，最后错误: "
                + (lastException != null ? lastException.getMessage() : "unknown"), lastException);
    }

    /**
     * 判断异常是否可触发 failover（网络/限流类异常）
     */
    static boolean isFailoverEligible(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof java.io.IOException
                    || current instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
            String msg = current.getMessage();
            if (msg != null && (msg.contains("timeout")
                    || msg.contains("connection refused")
                    || msg.contains("Connection refused")
                    || msg.contains("502")
                    || msg.contains("503")
                    || msg.contains("504")
                    || msg.contains("Too Many Requests")
                    || msg.contains("429")
                    || msg.contains("Service Unavailable"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
