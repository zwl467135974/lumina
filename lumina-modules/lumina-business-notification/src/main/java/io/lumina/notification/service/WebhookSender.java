package io.lumina.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lumina.notification.event.NotificationEvent;
import io.lumina.notification.infrastructure.entity.WebhookDO;
import io.lumina.notification.infrastructure.mapper.WebhookMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 通用 HTTP Webhook 发送器
 *
 * <p>POST JSON + HMAC-SHA256 签名（{@code X-Lumina-Signature} 头）+ 3 次指数退避重试。
 * 连续失败达 {@link #MAX_FAIL_COUNT} 次后自动禁用该 webhook。
 *
 * @author Lumina Team
 * @since 3.4.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookSender {

    /**
     * 单次发送的最大重试次数
     */
    private static final int MAX_ATTEMPTS = 3;

    /**
     * 连续失败自动禁用阈值
     */
    private static final int MAX_FAIL_COUNT = 5;

    /**
     * 失败原因截断长度（对齐 last_error 列宽）
     */
    private static final int MAX_ERROR_LENGTH = 500;

    private final WebhookMapper webhookMapper;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * 发送 webhook 并更新发送状态（成功重置失败计数，连续失败达阈值自动禁用）
     *
     * @param webhook webhook 订阅
     * @param event   通知事件
     * @return true 发送成功（2xx）
     */
    public boolean send(WebhookDO webhook, NotificationEvent event) {
        try {
            boolean success = sendToUrl(webhook.getUrl(), webhook.getSecret(), event);
            if (success) {
                webhookMapper.updateStatus(webhook.getId(), "SUCCESS", null, 0, null);
                return true;
            }
            recordFailure(webhook, "发送失败（重试 " + MAX_ATTEMPTS + " 次后仍失败）");
        } catch (Exception e) {
            recordFailure(webhook, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
        return false;
    }

    /**
     * 直接向指定 URL 发送事件（不更新 DB 状态，供评估回归告警等直连场景复用）
     *
     * @param url    webhook 接收端 URL
     * @param secret HMAC 签名密钥（null 或空则跳过签名）
     * @param event  通知事件
     * @return true 发送成功（2xx）
     */
    public boolean sendToUrl(String url, String secret, NotificationEvent event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.warn("Webhook payload 序列化失败: {}", e.getMessage());
            return false;
        }

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .header("X-Lumina-Event", event.getCategory() != null ? event.getCategory() : "UNKNOWN")
                        .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                        .timeout(Duration.ofSeconds(10));
                if (secret != null && !secret.isBlank()) {
                    builder.header("X-Lumina-Signature", "sha256=" + hmacSha256(payload, secret));
                }
                HttpResponse<String> resp = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                    return true;
                }
                throw new IOException("HTTP " + resp.statusCode());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Webhook 发送被中断: url={}", url);
                return false;
            } catch (Exception e) {
                log.warn("Webhook 发送失败（第 {}/{} 次）: url={}, error={}", attempt, MAX_ATTEMPTS, url, e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    sleep((long) (1000 * Math.pow(2, attempt - 1)));
                }
            }
        }
        return false;
    }

    // ==================== 辅助方法 ====================

    /**
     * 记录一次失败：递增失败计数，达阈值自动禁用（禁用时计数重置为 0）
     */
    private void recordFailure(WebhookDO webhook, String error) {
        int newFailCount = (webhook.getFailCount() != null ? webhook.getFailCount() : 0) + 1;
        boolean autoDisable = newFailCount >= MAX_FAIL_COUNT;
        webhookMapper.updateStatus(webhook.getId(), "FAILED",
                truncate(error, MAX_ERROR_LENGTH),
                autoDisable ? 0 : newFailCount,
                autoDisable ? 0 : null);
        if (autoDisable) {
            log.warn("Webhook [{}] 连续失败 {} 次已自动禁用: {}", webhook.getId(), MAX_FAIL_COUNT, webhook.getUrl());
        }
    }

    /**
     * HMAC-SHA256 签名（十六进制小写）
     */
    private String hmacSha256(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 签名失败", e);
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
