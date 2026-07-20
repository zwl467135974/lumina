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

/**
 * 飞书群机器人发送器
 *
 * <p>将通知事件转为飞书 text 消息发送（URL 为用户自填的
 * {@code https://open.feishu.cn/open-apis/bot/v2/hook/xxx} 完整地址），
 * Redis 秒级限频（5 条/秒，飞书官方限制）。响应 code=0 或 StatusCode=0 才算成功。
 *
 * <p>飞书 text 消息不支持 markdown 着色，severity 用 emoji 标识。
 *
 * @author Lumina Team
 * @since 3.5.0
 */
@Slf4j
@Component
public class FeishuSender extends AbstractNotificationSender {

    /**
     * 飞书群机器人限频：5 条/秒
     */
    private static final int RATE_LIMIT_PER_SEC = 5;

    /**
     * 正文截断长度
     */
    private static final int MAX_BODY_CHARS = 3000;

    /**
     * 限频计数 key 前缀
     */
    private static final String RATE_KEY_PREFIX = "feishu:rate:";

    private final ObjectMapper objectMapper;

    /**
     * HTTP 客户端（package-private 便于单测注入 mock）
     */
    HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public FeishuSender(WebhookMapper webhookMapper, ObjectMapper objectMapper,
                        RedisCacheManager redisCacheManager) {
        super(webhookMapper, redisCacheManager);
        this.objectMapper = objectMapper;
    }

    @Override
    protected int rateLimitPerWindow() {
        return RATE_LIMIT_PER_SEC;
    }

    @Override
    protected String rateKeyPrefix() {
        return RATE_KEY_PREFIX;
    }

    @Override
    protected long rateWindowSeconds() {
        return 1;  // 秒级限频
    }

    @Override
    protected String channelName() {
        return "飞书";
    }

    /**
     * 发送飞书 text 消息并更新发送状态
     *
     * @param webhook webhook 订阅（url 为飞书群机器人完整地址，v2/hook/ 结尾为 token）
     * @param event   通知事件
     * @return true 发送成功
     */
    public boolean send(WebhookDO webhook, NotificationEvent event) {
        // 限频检查（超限不发送、不计失败——非目标端故障）
        if (!acquireRateQuota(webhook.getUrl())) {
            log.warn("飞书机器人触发限频（{}/s），本条丢弃: whId={}", RATE_LIMIT_PER_SEC, webhook.getId());
            return false;
        }

        boolean success = postToFeishu(webhook.getUrl(), event);
        recordResult(webhook, success, success ? null : "飞书发送失败");
        return success;
    }

    // ==================== 内部实现 ====================

    /**
     * 构造飞书 text 消息体（severity 用 emoji 标识）
     */
    String buildBody(NotificationEvent event) {
        String severity = event.getSeverity() != null ? event.getSeverity() : "INFO";
        String emoji = switch (severity) {
            case "ERROR" -> "🔴";
            case "WARN" -> "🟡";
            default -> "🟢";
        };
        String content = event.getContent() != null ? event.getContent() : "";
        String text = String.format(
                "%s %s%n类别: %s | 严重度: %s%n%n%s%n%n— Lumina 通知中心",
                emoji,
                event.getTitle() != null ? event.getTitle() : "通知",
                event.getCategory() != null ? event.getCategory() : "SYSTEM",
                severity,
                truncate(content, MAX_BODY_CHARS));

        ObjectNode body = objectMapper.createObjectNode();
        body.put("msg_type", "text");
        ObjectNode contentNode = body.putObject("content");
        contentNode.put("text", text);
        return body.toString();
    }

    /**
     * POST text 消息到飞书机器人（HTTP 200 且 code=0 或 StatusCode=0 才算成功）
     */
    private boolean postToFeishu(String url, NotificationEvent event) {
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
                // 新版返回 code，旧版返回 StatusCode，任一为 0 即成功
                if (root.path("code").asInt(-1) == 0 || root.path("StatusCode").asInt(-1) == 0) {
                    return true;
                }
                log.warn("飞书返回错误: code={}, msg={}", root.path("code").asText(), root.path("msg").asText());
            } else {
                log.warn("飞书返回非 200: status={}", resp.statusCode());
            }
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("飞书发送被中断");
            return false;
        } catch (Exception e) {
            log.warn("飞书发送异常: {}", e.getMessage());
            return false;
        }
    }
}
