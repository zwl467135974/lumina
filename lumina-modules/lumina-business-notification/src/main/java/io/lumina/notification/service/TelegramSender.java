package io.lumina.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
 * Telegram Bot 发送器
 *
 * <p>将通知事件通过 Telegram Bot API sendMessage 发送。
 * webhook URL 约定格式：{@code https://api.telegram.org/bot<TOKEN>?chat_id=<CHAT_ID>}，
 * Sender 解析 chat_id 后向 {@code /sendMessage} 发送。响应 ok=true 才算成功。
 *
 * @author Lumina Team
 * @since 3.5.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramSender {

    /**
     * 正文截断长度（Telegram 单条消息上限 4096 字符，留头部余量）
     */
    private static final int MAX_BODY_CHARS = 3000;

    /**
     * 连续失败自动禁用阈值（与 WebhookSender 一致）
     */
    private static final int MAX_FAIL_COUNT = 5;

    private final WebhookMapper webhookMapper;
    private final ObjectMapper objectMapper;

    /**
     * HTTP 客户端（package-private 便于单测注入 mock）
     */
    HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * 发送 Telegram 消息并更新发送状态
     *
     * @param webhook webhook 订阅（url 约定为 https://api.telegram.org/bot&lt;TOKEN&gt;?chat_id=&lt;CHAT_ID&gt;）
     * @param event   通知事件
     * @return true 发送成功
     */
    public boolean send(WebhookDO webhook, NotificationEvent event) {
        String chatId = extractChatId(webhook.getUrl());
        if (chatId == null) {
            log.warn("Telegram webhook URL 无法解析 chat_id: {}", webhook.getUrl());
            recordResult(webhook, false, "Telegram URL 缺少 chat_id 参数");
            return false;
        }

        boolean success = postToTelegram(buildSendMessageUrl(webhook.getUrl()), chatId, event);
        recordResult(webhook, success, success ? null : "Telegram 发送失败");
        return success;
    }

    // ==================== 内部实现 ====================

    /**
     * 由约定 URL 构造 sendMessage 端点（去掉 query，追加 /sendMessage）
     */
    String buildSendMessageUrl(String rawUrl) {
        int question = rawUrl.indexOf('?');
        String base = question >= 0 ? rawUrl.substring(0, question) : rawUrl;
        // 容错去掉尾部斜杠
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/sendMessage";
    }

    /**
     * 从约定 URL 解析 chat_id query 参数
     */
    String extractChatId(String url) {
        if (url == null) {
            return null;
        }
        int idx = url.indexOf("chat_id=");
        if (idx <= 0) {
            return null;
        }
        String chatId = url.substring(idx + 8);
        int amp = chatId.indexOf('&');
        if (amp >= 0) {
            chatId = chatId.substring(0, amp);
        }
        return chatId.isBlank() ? null : chatId;
    }

    /**
     * 构造 sendMessage 请求体（Markdown parse_mode，severity 用 emoji 标识）
     */
    String buildBody(String chatId, NotificationEvent event) {
        String severity = event.getSeverity() != null ? event.getSeverity() : "INFO";
        String emoji = switch (severity) {
            case "ERROR" -> "🔴";
            case "WARN" -> "🟡";
            default -> "🟢";
        };
        String content = event.getContent() != null ? event.getContent() : "";
        String text = String.format(
                "%s *%s*%n类别: %s | 严重度: %s%n%n%s%n%n— Lumina 通知中心",
                emoji,
                event.getTitle() != null ? event.getTitle() : "通知",
                event.getCategory() != null ? event.getCategory() : "SYSTEM",
                severity,
                truncate(content, MAX_BODY_CHARS));

        ObjectNode body = objectMapper.createObjectNode();
        body.put("chat_id", chatId);
        body.put("text", text);
        body.put("parse_mode", "Markdown");
        return body.toString();
    }

    /**
     * POST sendMessage 到 Telegram Bot API（HTTP 200 且 ok=true 才算成功）
     */
    private boolean postToTelegram(String url, String chatId, NotificationEvent event) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(buildBody(chatId, event), StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(resp.body());
                if (root.path("ok").asBoolean(false)) {
                    return true;
                }
                log.warn("Telegram 返回错误: error_code={}, description={}",
                        root.path("error_code").asInt(), root.path("description").asText());
            } else {
                log.warn("Telegram 返回非 200: status={}", resp.statusCode());
            }
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Telegram 发送被中断");
            return false;
        } catch (Exception e) {
            log.warn("Telegram 发送异常: {}", e.getMessage());
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
            log.warn("Telegram webhook [{}] 连续失败 {} 次已自动禁用", webhook.getId(), MAX_FAIL_COUNT);
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}
