package io.lumina.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 网关限流过滤器（固定窗口算法）
 *
 * <p>按全局 QPS + 单 IP QPS 双维度限流，超限返回 429。
 * <p>窗口为 1 秒，每秒重置计数器。
 *
 * @author Lumina Team
 * @since 1.1.0
 */
@Slf4j
@Component
public class RateLimitGlobalFilter implements GlobalFilter, Ordered {

    /**
     * 全局每秒最大请求数
     */
    @Value("${lumina.gateway.rate-limit.global:100}")
    private int globalLimit;

    /**
     * 单 IP 每秒最大请求数
     */
    @Value("${lumina.gateway.rate-limit.per-ip:20}")
    private int perIpLimit;

    private final AtomicLong globalCount = new AtomicLong(0);
    private final Map<String, AtomicLong> ipCounts = new ConcurrentHashMap<>();
    private volatile long windowStart = System.currentTimeMillis();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 固定窗口重置（每秒）
        long now = System.currentTimeMillis();
        if (now - windowStart >= 1000) {
            resetWindow(now);
        }

        // 全局限流
        if (globalCount.incrementAndGet() > globalLimit) {
            log.warn("全局限流触发: count={}, limit={}", globalCount.get(), globalLimit);
            return reject(exchange, "服务繁忙，请稍后重试");
        }

        // IP 限流
        String ip = getClientIp(exchange.getRequest());
        long ipCount = ipCounts.computeIfAbsent(ip, k -> new AtomicLong()).incrementAndGet();
        if (ipCount > perIpLimit) {
            log.warn("IP 限流触发: ip={}, count={}, limit={}", ip, ipCount, perIpLimit);
            return reject(exchange, "您的请求过于频繁，请稍后重试");
        }

        return chain.filter(exchange);
    }

    /**
     * 重置限流窗口（synchronized 防并发重复重置）
     */
    private synchronized void resetWindow(long now) {
        if (now - windowStart >= 1000) {
            windowStart = now;
            globalCount.set(0);
            ipCounts.clear();
        }
    }

    /**
     * 获取客户端 IP
     */
    private String getClientIp(ServerHttpRequest request) {
        String ip = request.getHeaders().getFirst("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getHeaders().getFirst("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() && request.getRemoteAddress() != null) {
            ip = request.getRemoteAddress().getAddress().getHostAddress();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "unknown";
    }

    /**
     * 拒绝请求，返回 429 JSON
     */
    private Mono<Void> reject(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("code", 429);
            body.put("message", message);
            DataBuffer buffer = response.bufferFactory().wrap(OBJECT_MAPPER.writeValueAsBytes(body));
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            log.error("写入限流响应失败", e);
            return response.setComplete();
        }
    }

    @Override
    public int getOrder() {
        // 优先于 JWT 认证过滤器执行
        return -2;
    }
}
