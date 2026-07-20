package io.lumina.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lumina.notification.event.NotificationEvent;
import io.lumina.notification.infrastructure.entity.WebhookDO;
import io.lumina.notification.infrastructure.mapper.WebhookMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TelegramSender 单元测试
 *
 * <p>验证约定 URL 解析（bot TOKEN + chat_id）、sendMessage 端点构造、
 * 请求体格式与 ok=true 成功判断。
 *
 * @author Lumina Team
 * @since 3.5.0
 */
@ExtendWith(MockitoExtension.class)
class TelegramSenderTest {

    private static final String BOT_URL = "https://api.telegram.org/bot123456:ABC-DEF?chat_id=-1009876";

    @Mock
    private WebhookMapper webhookMapper;

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private TelegramSender sender;

    @BeforeEach
    void setUp() {
        sender = new TelegramSender(webhookMapper, new ObjectMapper());
        sender.httpClient = httpClient;
    }

    @Test
    void buildSendMessageUrlStripsQueryAndAppendsMethod() {
        assertThat(sender.buildSendMessageUrl(BOT_URL))
                .isEqualTo("https://api.telegram.org/bot123456:ABC-DEF/sendMessage");
    }

    @Test
    void extractChatIdFromQuery() {
        assertThat(sender.extractChatId(BOT_URL)).isEqualTo("-1009876");
        assertThat(sender.extractChatId("https://api.telegram.org/bot123456:ABC-DEF")).isNull();
    }

    @Test
    void buildBodyContainsChatIdAndParseMode() throws Exception {
        NotificationEvent event = new NotificationEvent(
                1L, "SYSTEM", "系统告警", "CPU 过高", "ERROR", null, null, 1L);

        String body = sender.buildBody("-1009876", event);

        var root = new ObjectMapper().readTree(body);
        assertThat(root.path("chat_id").asText()).isEqualTo("-1009876");
        assertThat(root.path("parse_mode").asText()).isEqualTo("Markdown");
        assertThat(root.path("text").asText())
                .contains("系统告警").contains("SYSTEM").contains("ERROR").contains("🔴");
    }

    @Test
    void sendSuccessWhenOkTrue() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"ok\":true,\"result\":{}}");
        when(httpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

        boolean result = sender.send(webhook(BOT_URL), event());

        assertThat(result).isTrue();
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(captor.getValue().uri().toString())
                .isEqualTo("https://api.telegram.org/bot123456:ABC-DEF/sendMessage");
        verify(webhookMapper).updateStatus(eq(1L), eq("SUCCESS"), isNull(), eq(0), isNull());
    }

    @Test
    void sendFailsWhenOkFalse() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"ok\":false,\"error_code\":400,\"description\":\"chat not found\"}");
        when(httpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

        assertThat(sender.send(webhook(BOT_URL), event())).isFalse();
        verify(webhookMapper).updateStatus(eq(1L), eq("FAILED"), any(), eq(1), isNull());
    }

    @Test
    void sendFailsWhenUrlMissingChatId() {
        assertThat(sender.send(webhook("https://api.telegram.org/bot123456:ABC-DEF"), event())).isFalse();
        verify(webhookMapper).updateStatus(eq(1L), eq("FAILED"), any(), eq(1), isNull());
    }

    private WebhookDO webhook(String url) {
        WebhookDO webhook = new WebhookDO();
        webhook.setId(1L);
        webhook.setUrl(url);
        webhook.setChannel("TELEGRAM");
        webhook.setFailCount(0);
        return webhook;
    }

    private NotificationEvent event() {
        return new NotificationEvent(1L, "TASK", "标题", "内容", "INFO", null, null, 1L);
    }
}
