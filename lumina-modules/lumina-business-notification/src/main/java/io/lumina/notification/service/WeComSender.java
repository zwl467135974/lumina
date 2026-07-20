package io.lumina.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.lumina.framework.cache.RedisCacheManager;
import io.lumina.notification.event.NotificationEvent;
import io.lumina.notification.infrastructure.entity.WebhookDO;
import io.lumina.notification.infrastructure.mapper.WebhookMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * 企业微信群机器人发送器
 *
 * <p>将通知事件转为企微 markdown 消息发送，支持 severity 着色、超长自动分片、
 * Redis 限频（20 条/分钟，企微机器人官方限制）。errcode=0 才算成功。
 *
 * <p>继承 {@link AbstractNotificationSender} 复用 recordResult / acquireRateQuota /
 * truncate / chunkByBytes 等公共逻辑。
 *
 * @author Lumina Team
 * @since 3.4.0
 */
@Slf4j
@Component
public class WeComSender extends AbstractNotificationSender {

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
     * 限频计数 key 前缀
     */
    private static final String RATE_KEY_PREFIX = "wecom:rate:";

    private final ObjectMapper objectMapper;

    /**
     * HTTP 客户端（package-private 便于单测注入 mock）
     */
    HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public WeComSender(WebhookMapper webhookMapper, ObjectMapper objectMapper,
                       RedisCacheManager redisCacheManager) {
        super(webhookMapper, redisCacheManager);
        this.objectMapper = objectMapper;
    }

    @Override
    protected int rateLimitPerWindow() {
        return RATE_LIMIT_PER_MIN;
    }

    @Override
    protected String rateKeyPrefix() {
        return RATE_KEY_PREFIX;
    }

    @Override
    protected String channelName() {
        return "企微";
    }

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

    // ==================== 渠道特有实现 ====================

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
}
