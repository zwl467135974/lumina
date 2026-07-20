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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DingTalkSender 单元测试
 *
 * <p>验证钉钉加签算法（固定 secret + timestamp 对比预期 sign）、
 * 签名 URL 构造、markdown 消息体格式与 errcode 成功判断。
 *
 * @author Lumina Team
 * @since 3.5.0
 */
@ExtendWith(MockitoExtension.class)
class DingTalkSenderTest {

    @Mock
    private WebhookMapper webhookMapper;

    @Mock
    private RedisCacheManager redisCacheManager;

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private DingTalkSender sender;

    @BeforeEach
    void setUp() {
        sender = new DingTalkSender(webhookMapper, new ObjectMapper(), redisCacheManager);
        sender.httpClient = httpClient;
    }

    @Test
    void signMatchesKnownVector() {
        // StringToSign = timestamp + "\n" + secret，HMAC-SHA256 → Base64 → URL encode
        String sign = sender.sign(1656931200000L, "testSecret");

        assertThat(sign).isEqualTo("I5qF8Hdc2FnVp6BXow1DH7DFwojHCCKSKLuLR%2B7x2NU%3D");
    }

    @Test
    void buildSignedUrlAppendsTimestampAndSign() {
        String rawUrl = "https://oapi.dingtalk.com/robot/send?access_token=abc&secret=testSecret";

        String url = sender.buildSignedUrl(rawUrl, 1656931200000L);

        // secret 参数被移除，追加 timestamp + sign
        assertThat(url).startsWith("https://oapi.dingtalk.com/robot/send?access_token=abc");
        assertThat(url).doesNotContain("secret=");
        assertThat(url).contains("&timestamp=1656931200000");
        assertThat(url).contains("&sign=I5qF8Hdc2FnVp6BXow1DH7DFwojHCCKSKLuLR%2B7x2NU%3D");
    }

    @Test
    void buildSignedUrlWithoutSecretReturnsRawUrl() {
        String rawUrl = "https://oapi.dingtalk.com/robot/send?access_token=abc";

        assertThat(sender.buildSignedUrl(rawUrl, 1656931200000L)).isEqualTo(rawUrl);
    }

    @Test
    void buildBodyIsMarkdownMessage() throws Exception {
        NotificationEvent event = new NotificationEvent(
                1L, "TASK", "任务完成", "Agent 任务已完成", "ERROR", "agent_task", "t-1", 1L);

        String body = sender.buildBody(event);

        var root = new ObjectMapper().readTree(body);
        assertThat(root.path("msgtype").asText()).isEqualTo("markdown");
        assertThat(root.path("markdown").path("title").asText()).isEqualTo("任务完成");
        // 钉钉不支持 font color，用 emoji 着色
        assertThat(root.path("markdown").path("text").asText())
                .contains("任务完成").contains("TASK").contains("ERROR").contains("🔴")
                .doesNotContain("<font");
    }

    @Test
    void sendSuccessWhenErrcodeZero() throws Exception {
        WebhookDO webhook = webhook("https://oapi.dingtalk.com/robot/send?access_token=abc");
        when(redisCacheManager.incrementAndGet(any())).thenReturn(1L);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"errcode\":0,\"errmsg\":\"ok\"}");
        when(httpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

        boolean result = sender.send(webhook, event());

        assertThat(result).isTrue();
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(captor.getValue().uri().toString())
                .isEqualTo("https://oapi.dingtalk.com/robot/send?access_token=abc");
        verify(webhookMapper).updateStatus(eq(1L), eq("SUCCESS"), isNull(), eq(0), isNull());
    }

    @Test
    void sendFailsWhenErrcodeNonZero() throws Exception {
        WebhookDO webhook = webhook("https://oapi.dingtalk.com/robot/send?access_token=abc");
        when(redisCacheManager.incrementAndGet(any())).thenReturn(1L);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"errcode\":310000,\"errmsg\":\"sign not match\"}");
        when(httpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

        assertThat(sender.send(webhook, event())).isFalse();
        verify(webhookMapper).updateStatus(eq(1L), eq("FAILED"), any(), eq(1), isNull());
    }

    @Test
    void sendFailsWhenUrlMissingAccessToken() {
        WebhookDO webhook = webhook("https://oapi.dingtalk.com/robot/send");

        assertThat(sender.send(webhook, event())).isFalse();
        verify(webhookMapper).updateStatus(eq(1L), eq("FAILED"), any(), eq(1), isNull());
    }

    @Test
    void sendDroppedWhenRateLimited() throws Exception {
        WebhookDO webhook = webhook("https://oapi.dingtalk.com/robot/send?access_token=abc");
        when(redisCacheManager.incrementAndGet(any())).thenReturn(21L);

        assertThat(sender.send(webhook, event())).isFalse();
        // 限频丢弃不发 HTTP、不记失败
        verify(httpClient, org.mockito.Mockito.never()).send(any(), any(HttpResponse.BodyHandler.class));
        verify(webhookMapper, org.mockito.Mockito.never()).updateStatus(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    private WebhookDO webhook(String url) {
        WebhookDO webhook = new WebhookDO();
        webhook.setId(1L);
        webhook.setUrl(url);
        webhook.setChannel("DINGTALK");
        webhook.setFailCount(0);
        return webhook;
    }

    private NotificationEvent event() {
        return new NotificationEvent(1L, "TASK", "标题", "内容", "INFO", null, null, 1L);
    }
}
