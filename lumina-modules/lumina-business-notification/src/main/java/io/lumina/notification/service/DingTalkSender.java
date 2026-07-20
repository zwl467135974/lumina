package io.lumina.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.lumina.framework.cache.RedisCacheManager;
import io.lumina.notification.event.NotificationEvent;
import io.lumina.notification.infrastructure.entity.WebhookDO;
import io.lumina.notification.infrastructure.mapper.WebhookMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * 钉钉群机器人发送器
 *
 * <p>将通知事件转为钉钉 markdown 消息发送，支持"加签"安全设置
 * （webhook URL 含 {@code &secret=SECxxx} 时自动追加 timestamp + sign 参数），
 * Redis 限频（20 条/分钟，钉钉机器人官方限制）。errcode=0 才算成功。
 *
 * <p>钉钉/飞书不支持企微的 {@code <font color>} 语法，severity 用 emoji 着色。
 *
 * @author Lumina Team
 * @since 3.5.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DingTalkSender {

    /**
     * 钉钉群机器人限频：20 条/分钟
     */
    private static final int RATE_LIMIT_PER_MIN = 20;

    /**
     * 正文截断长度（钉钉 markdown 上限 20000 字节，留足余量）
     */
    private static final int MAX_BODY_CHARS = 3000;

    /**
     * 连续失败自动禁用阈值（与 WebhookSender 一致）
     */
    private static final int MAX_FAIL_COUNT = 5;

    /**
     * 限频计数 key 前缀
     */
    private static final String RATE_KEY_PREFIX = "dingtalk:rate:";

    private final WebhookMapper webhookMapper;
    private final ObjectMapper objectMapper;
    private final RedisCacheManager redisCacheManager;

    /**
     * HTTP 客户端（package-private 便于单测注入 mock）
     */
    HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * 发送钉钉 markdown 消息并更新发送状态
     *
     * @param webhook webhook 订阅（url 为钉钉群机器人完整地址，含 access_token=xxx；
     *                开启"加签"时约定追加 &amp;secret=SECxxx，发送时自动签名）
     * @param event   通知事件
     * @return true 发送成功
     */
    public boolean send(WebhookDO webhook, NotificationEvent event) {
        String accessToken = extractQueryParam(webhook.getUrl(), "access_token");
        if (accessToken == null) {
            log.warn("钉钉 webhook URL 无法解析 access_token: {}", webhook.getUrl());
            recordResult(webhook, false, "钉钉 URL 缺少 access_token 参数");
            return false;
        }

        // 限频检查（超限不发送、不计失败——非目标端故障）
        if (!acquireRateQuota(accessToken)) {
            log.warn("钉钉机器人触发限频（{}/min），本条丢弃: whId={}", RATE_LIMIT_PER_MIN, webhook.getId());
            return false;
        }

        String url = buildSignedUrl(webhook.getUrl(), System.currentTimeMillis());
        boolean success = postToDingTalk(url, event);
        recordResult(webhook, success, success ? null : "钉钉发送失败");
        return success;
    }

    // ==================== 内部实现 ====================

    /**
     * 构造实际发送 URL：URL 含 secret 参数时执行加签（移除 secret，追加 timestamp + sign）
     *
     * <p>签名算法（钉钉官方）：{@code StringToSign = timestamp + "\n" + secret}，
     * HMAC-SHA256（key 为 secret），Base64 编码后 URL encode。
     *
     * @param rawUrl    用户配置的 webhook URL（可能含 &amp;secret=SECxxx）
     * @param timestamp 毫秒时间戳
     * @return 实际发送 URL（未配置 secret 时原样返回）
     */
    String buildSignedUrl(String rawUrl, long timestamp) {
        String secret = extractQueryParam(rawUrl, "secret");
        if (secret == null) {
            return rawUrl;
        }
        String baseUrl = removeQueryParam(rawUrl, "secret");
        String sign = sign(timestamp, secret);
        return baseUrl + "&timestamp=" + timestamp + "&sign=" + sign;
    }

    /**
     * 钉钉加签：HMAC-SHA256(timestamp + "\n" + secret, secret) → Base64 → URL encode
     */
    String sign(long timestamp, String secret) {
        try {
            String stringToSign = timestamp + "\n" + secret;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            return URLEncoder.encode(Base64.getEncoder().encodeToString(digest), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("钉钉加签失败", e);
        }
    }

    /**
     * 构造钉钉 markdown 消息体（severity 用 emoji 着色，钉钉不支持 font color）
     */
    String buildBody(NotificationEvent event) {
        String severity = event.getSeverity() != null ? event.getSeverity() : "INFO";
        String emoji = severityEmoji(severity);
        String title = event.getTitle() != null ? event.getTitle() : "通知";
        String content = event.getContent() != null ? event.getContent() : "";
        String text = String.format(
                "## %s %s%n> **类别**: %s%n> **严重度**: %s %s%n%n%s%n%n---%n_Lumina 通知中心_",
                emoji, title,
                event.getCategory() != null ? event.getCategory() : "SYSTEM",
                emoji, severity,
                truncate(content, MAX_BODY_CHARS));

        ObjectNode body = objectMapper.createObjectNode();
        body.put("msgtype", "markdown");
        ObjectNode md = body.putObject("markdown");
        md.put("title", title);
        md.put("text", text);
        return body.toString();
    }

    /**
     * POST markdown 消息到钉钉机器人（HTTP 200 且 errcode=0 才算成功）
     */
    private boolean postToDingTalk(String url, NotificationEvent event) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(buildBody(event), StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(resp.body());
                int errcode = root.path("errcode").asInt(-1);
                if (errcode == 0) {
                    return true;
                }
                log.warn("钉钉返回错误: errcode={}, errmsg={}", errcode, root.path("errmsg").asText());
            } else {
                log.warn("钉钉返回非 200: status={}", resp.statusCode());
            }
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("钉钉发送被中断");
            return false;
        } catch (Exception e) {
            log.warn("钉钉发送异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 复用 webhook 状态字段记录结果（连续失败达阈值自动禁用）
     */
    private void recordResult(WebhookDO webhook, boolean success, String error) {
        if (success) {
            webhookMapper.updateStatus(webhook.getId(), "SUCCESS", null, 0, null);
            return;
        }
        int newFailCount = (webhook.getFailCount() != null ? webhook.getFailCount() : 0) + 1;
        boolean autoDisable = newFailCount >= MAX_FAIL_COUNT;
        webhookMapper.updateStatus(webhook.getId(), "FAILED", error,
                autoDisable ? 0 : newFailCount, autoDisable ? 0 : null);
        if (autoDisable) {
            log.warn("钉钉 webhook [{}] 连续失败 {} 次已自动禁用", webhook.getId(), MAX_FAIL_COUNT);
        }
    }

    /**
     * 限频：Redis 原子计数 + 60s 过期，超过 20/min 拒绝
     */
    private boolean acquireRateQuota(String accessToken) {
        try {
            String rateKey = RATE_KEY_PREFIX + accessToken;
            long count = redisCacheManager.incrementAndGet(rateKey);
            if (count == 1) {
                redisCacheManager.expire(rateKey, Duration.ofSeconds(60));
            }
            return count <= RATE_LIMIT_PER_MIN;
        } catch (Exception e) {
            // Redis 异常时降级放行（软限频），避免 Redis 故障阻断通知
            log.warn("钉钉限频计数失败，降级放行: {}", e.getMessage());
            return true;
        }
    }

    /**
     * severity → emoji（ERROR=🔴, WARN=🟡, 其他=🟢）
     */
    private String severityEmoji(String severity) {
        return switch (severity) {
            case "ERROR" -> "🔴";
            case "WARN" -> "🟡";
            default -> "🟢";
        };
    }

    /**
     * 提取 URL query 参数值（简单字符串解析，与 WeComSender.extractKey 同风格）
     */
    private String extractQueryParam(String url, String param) {
        if (url == null) {
            return null;
        }
        int idx = url.indexOf(param + "=");
        if (idx <= 0) {
            return null;
        }
        char prev = url.charAt(idx - 1);
        if (prev != '?' && prev != '&') {
            return null;
        }
        String value = url.substring(idx + param.length() + 1);
        int amp = value.indexOf('&');
        if (amp >= 0) {
            value = value.substring(0, amp);
        }
        return value.isBlank() ? null : value;
    }

    /**
     * 从 URL 中移除指定 query 参数（secret 不应发给钉钉服务端）
     */
    private String removeQueryParam(String url, String param) {
        int idx = url.indexOf(param + "=");
        if (idx <= 0) {
            return url;
        }
        String rest = url.substring(idx + param.length() + 1);
        int amp = rest.indexOf('&');
        if (amp >= 0) {
            // 后面还有参数：移除 "param=value&"，保留原分隔符（? 或 &）
            return url.substring(0, idx) + rest.substring(amp + 1);
        }
        // param 是最后一个参数：连同前面的分隔符一起移除
        return url.substring(0, idx - 1);
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}
