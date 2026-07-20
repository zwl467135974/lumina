package io.lumina.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lumina.framework.cache.RedisCacheManager;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FeishuSender 单元测试
 *
 * <p>验证飞书 text 消息体格式、URL 直发、code/StatusCode 成功判断与秒级限频。
 *
 * @author Lumina Team
 * @since 3.5.0
 */
@ExtendWith(MockitoExtension.class)
class FeishuSenderTest {

    private static final String HOOK_URL = "https://open.feishu.cn/open-apis/bot/v2/hook/token-xyz";

    @Mock
    private WebhookMapper webhookMapper;

    @Mock
    private RedisCacheManager redisCacheManager;

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private FeishuSender sender;

    @BeforeEach
    void setUp() {
        sender = new FeishuSender(webhookMapper, new ObjectMapper(), redisCacheManager);
        sender.httpClient = httpClient;
    }

    @Test
    void buildBodyIsTextMessage() throws Exception {
        NotificationEvent event = new NotificationEvent(
                1L, "BUDGET", "预算告警", "本月预算已用 90%", "WARN", null, null, 1L);

        String body = sender.buildBody(event);

        var root = new ObjectMapper().readTree(body);
        assertThat(root.path("msg_type").asText()).isEqualTo("text");
        assertThat(root.path("content").path("text").asText())
                .contains("预算告警").contains("BUDGET").contains("WARN").contains("🟡")
                .doesNotContain("<font");
    }

    @Test
    void sendPostsToWebhookUrlAndSucceedsOnCodeZero() throws Exception {
        when(redisCacheManager.incrementAndGet(any())).thenReturn(1L);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"code\":0,\"msg\":\"success\"}");
        when(httpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

        boolean result = sender.send(webhook(), event());

        assertThat(result).isTrue();
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        // URL 即用户自填的完整 webhook 地址
        assertThat(captor.getValue().uri().toString()).isEqualTo(HOOK_URL);
        verify(webhookMapper).updateStatus(eq(1L), eq("SUCCESS"), isNull(), eq(0), isNull());
    }

    @Test
    void sendSucceedsOnLegacyStatusCodeZero() throws Exception {
        when(redisCacheManager.incrementAndGet(any())).thenReturn(1L);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"StatusCode\":0,\"StatusMessage\":\"success\"}");
        when(httpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

        assertThat(sender.send(webhook(), event())).isTrue();
    }

    @Test
    void sendFailsOnNonZeroCode() throws Exception {
        when(redisCacheManager.incrementAndGet(any())).thenReturn(1L);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"code\":19001,\"msg\":\"param invalid\"}");
        when(httpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

        assertThat(sender.send(webhook(), event())).isFalse();
        verify(webhookMapper).updateStatus(eq(1L), eq("FAILED"), any(), eq(1), isNull());
    }

    @Test
    void sendDroppedWhenRateLimited() throws Exception {
        when(redisCacheManager.incrementAndGet(any())).thenReturn(6L);

        assertThat(sender.send(webhook(), event())).isFalse();
        // 限频丢弃不发 HTTP、不记失败
        verify(httpClient, never()).send(any(), any(HttpResponse.BodyHandler.class));
        verify(webhookMapper, never()).updateStatus(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    private WebhookDO webhook() {
        WebhookDO webhook = new WebhookDO();
        webhook.setId(1L);
        webhook.setUrl(HOOK_URL);
        webhook.setChannel("FEISHU");
        webhook.setFailCount(0);
        return webhook;
    }

    private NotificationEvent event() {
        return new NotificationEvent(1L, "TASK", "标题", "内容", "INFO", null, null, 1L);
    }
}
