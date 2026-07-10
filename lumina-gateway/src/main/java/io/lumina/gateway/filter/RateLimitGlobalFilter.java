package io.lumina.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lumina.common.core.R;
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

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

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

    private static final int MAX_IP_ENTRIES = 10000;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$");

    private static final Pattern IPV6_CHARS_PATTERN = Pattern.compile("^[0-9a-fA-F:]+$");

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
        if (ipCounts.size() >= MAX_IP_ENTRIES && !ipCounts.containsKey(ip)) {
            log.warn("IP 计数 Map 已达上限，跳过单 IP 限流: ip={}", ip);
            return chain.filter(exchange);
        }
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
        String ip = extractValidIpFromHeader(request, "X-Forwarded-For");
        if (ip == null) {
            ip = extractValidIpFromHeader(request, "X-Real-IP");
        }
        if (ip == null) {
            InetSocketAddress remoteAddress = request.getRemoteAddress();
            if (remoteAddress != null) {
                String remoteIp = remoteAddress.getAddress().getHostAddress();
                if (isValidIp(remoteIp)) {
                    ip = remoteIp;
                }
            }
        }
        return ip != null ? ip : "unknown";
    }

    private String extractValidIpFromHeader(ServerHttpRequest request, String headerName) {
        String value = request.getHeaders().getFirst(headerName);
        if (value == null || value.isEmpty() || "unknown".equalsIgnoreCase(value)) {
            return null;
        }
        for (String part : value.split(",")) {
            String candidate = part.trim();
            if (isValidIp(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean isValidIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        if (IPV4_PATTERN.matcher(ip).matches()) {
            return true;
        }
        return ip.contains(":") && IPV6_CHARS_PATTERN.matcher(ip).matches();
    }

    /**
     * 拒绝请求，返回 429 JSON
     */
    private Mono<Void> reject(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            R<Void> body = R.fail(429, message);
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
