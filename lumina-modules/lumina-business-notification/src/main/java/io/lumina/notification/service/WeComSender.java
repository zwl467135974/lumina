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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 企业微信群机器人发送器
 *
 * <p>将通知事件转为企微 markdown 消息发送，支持 severity 着色、超长自动分片、
 * Redis 限频（20 条/分钟，企微机器人官方限制）。errcode=0 才算成功。
 *
 * @author Lumina Team
 * @since 3.4.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeComSender {

    /**
     * 企业微信群机器人限频：20 条/分钟
     */
    private static final int RATE_LIMIT_PER_MIN = 20;

    /**
     * 单条 markdown 消息最大字节数（企业微信限制）
     */
    private static final int MAX_CONTENT_BYTES = 4096;

    /**
     * 正文截断长度（为 markdown 模板头部留余量）
     */
    private static final int MAX_BODY_CHARS = 3000;

    /**
     * 连续失败自动禁用阈值（与 WebhookSender 一致）
     */
    private static final int MAX_FAIL_COUNT = 5;

    /**
     * 限频计数 key 前缀
     */
    private static final String RATE_KEY_PREFIX = "wecom:rate:";

    private final WebhookMapper webhookMapper;
    private final ObjectMapper objectMapper;
    private final RedisCacheManager redisCacheManager;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * 发送企微 markdown 消息并更新发送状态
     *
     * @param webhook webhook 订阅（url 为企微群机器人完整地址，含 key=xxx）
     * @param event   通知事件
     * @return true 全部分片发送成功
     */
    public boolean send(WebhookDO webhook, NotificationEvent event) {
        String key = extractKey(webhook.getUrl());
        if (key == null) {
            log.warn("企微 webhook URL 无法解析 key: {}", webhook.getUrl());
            recordResult(webhook, false, "企微 URL 缺少 key 参数");
            return false;
        }

        // 限频检查（超限不发送、不计失败——非目标端故障）
        if (!acquireRateQuota(key)) {
            log.warn("企微机器人触发限频（{}/min），本条丢弃: whId={}", RATE_LIMIT_PER_MIN, webhook.getId());
            return false;
        }

        // 构造 markdown 消息（severity 着色），超长按字节分片
        String markdown = buildMarkdown(event);
        List<String> chunks = chunkByBytes(markdown, MAX_CONTENT_BYTES);
        boolean allOk = true;
        for (String chunk : chunks) {
            if (!postToWeCom(webhook.getUrl(), chunk)) {
                allOk = false;
                // 不 break：尽量多发几条让用户看到部分内容
            }
        }

        recordResult(webhook, allOk, allOk ? null : "企业微信发送失败");
        return allOk;
    }

    // ==================== 内部实现 ====================

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
            log.warn("企微 webhook [{}] 连续失败 {} 次已自动禁用", webhook.getId(), MAX_FAIL_COUNT);
        }
    }

    /**
     * 从企微机器人 URL 提取 key（url 形如 https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx）
     */
    private String extractKey(String url) {
        if (url == null) {
            return null;
        }
        int idx = url.indexOf("key=");
        if (idx <= 0) {
            return null;
        }
        String key = url.substring(idx + 4);
        int amp = key.indexOf('&');
        if (amp >= 0) {
            key = key.substring(0, amp);
        }
        return key.isBlank() ? null : key;
    }

    /**
     * 构造企微 markdown 消息（仅使用企微支持的 font 着色语法）
     */
    private String buildMarkdown(NotificationEvent event) {
        // severity 映射企微 font 颜色：ERROR=warning(红), WARN=comment(灰), 其他=info(绿)
        String severity = event.getSeverity() != null ? event.getSeverity() : "INFO";
        String color = switch (severity) {
            case "ERROR" -> "warning";
            case "WARN" -> "comment";
            default -> "info";
        };
        String content = event.getContent() != null ? event.getContent() : "";
        return String.format(
                "## %s%n> **类别**: %s%n> **严重度**: <font color=\"%s\">%s</font>%n%n%s%n%n---%n_Lumina 通知中心_",
                event.getTitle() != null ? event.getTitle() : "通知",
                event.getCategory() != null ? event.getCategory() : "SYSTEM",
                color, severity,
                truncate(content, MAX_BODY_CHARS));
    }

    /**
     * 按 UTF-8 字节数分片（保证不截断多字节字符）
     */
    private List<String> chunkByBytes(String text, int maxBytes) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int currentBytes = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            String ch = new String(Character.toChars(codePoint));
            int chBytes = ch.getBytes(StandardCharsets.UTF_8).length;
            if (currentBytes + chBytes > maxBytes && current.length() > 0) {
                chunks.add(current.toString());
                current = new StringBuilder();
                currentBytes = 0;
            }
            current.append(ch);
            currentBytes += chBytes;
            i += Character.charCount(codePoint);
        }
        if (current.length() > 0) {
            chunks.add(current.toString());
        }
        return chunks;
    }

    /**
     * POST markdown 消息到企微机器人（HTTP 200 且 errcode=0 才算成功）
     */
    private boolean postToWeCom(String url, String markdownContent) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("msgtype", "markdown");
            ObjectNode md = body.putObject("markdown");
            md.put("content", markdownContent);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(resp.body());
                int errcode = root.path("errcode").asInt(-1);
                if (errcode == 0) {
                    return true;
                }
                log.warn("企微返回错误: errcode={}, errmsg={}", errcode, root.path("errmsg").asText());
            } else {
                log.warn("企微返回非 200: status={}", resp.statusCode());
            }
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("企微发送被中断");
            return false;
        } catch (Exception e) {
            log.warn("企微发送异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 限频：Redis 原子计数 + 60s 过期，超过 20/min 拒绝
     */
    private boolean acquireRateQuota(String key) {
        try {
            String rateKey = RATE_KEY_PREFIX + key;
            long count = redisCacheManager.incrementAndGet(rateKey);
            if (count == 1) {
                redisCacheManager.expire(rateKey, Duration.ofSeconds(60));
            }
            return count <= RATE_LIMIT_PER_MIN;
        } catch (Exception e) {
            // Redis 异常时降级放行（软限频），避免 Redis 故障阻断通知
            log.warn("企微限频计数失败，降级放行: {}", e.getMessage());
            return true;
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}
