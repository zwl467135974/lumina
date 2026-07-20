package io.lumina.notification.service;

import io.lumina.notification.event.NotificationEvent;
import io.lumina.notification.infrastructure.entity.WebhookDO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * WebhookDispatcher 路由单元测试
 *
 * <p>验证按 channel 路由到对应 Sender（含 3.5.0 新增的 DINGTALK/FEISHU/TELEGRAM）。
 *
 * @author Lumina Team
 * @since 3.5.0
 */
@ExtendWith(MockitoExtension.class)
class WebhookDispatcherTest {

    @Mock
    private WebhookService webhookService;

    @Mock
    private WebhookSender webhookSender;

    @Mock
    private WeComSender weComSender;

    @Mock
    private DingTalkSender dingTalkSender;

    @Mock
    private FeishuSender feishuSender;

    @Mock
    private TelegramSender telegramSender;

    private WebhookDispatcher dispatcher;

    private final NotificationEvent event = new NotificationEvent(
            1L, "TASK", "标题", "内容", "INFO", null, null, 1L);

    @BeforeEach
    void setUp() {
        dispatcher = new WebhookDispatcher(webhookService, webhookSender, weComSender);
        // IM Sender 为 @Autowired(required = false) 字段注入
        ReflectionTestUtils.setField(dispatcher, "dingTalkSender", dingTalkSender);
        ReflectionTestUtils.setField(dispatcher, "feishuSender", feishuSender);
        ReflectionTestUtils.setField(dispatcher, "telegramSender", telegramSender);
    }

    @Test
    void routesDingTalkChannelToDingTalkSender() {
        WebhookDO webhook = webhook("DINGTALK");

        dispatcher.dispatchOne(webhook, event);

        verify(dingTalkSender).send(webhook, event);
        verifyNoInteractions(webhookSender, weComSender, feishuSender, telegramSender);
    }

    @Test
    void routesFeishuChannelToFeishuSender() {
        WebhookDO webhook = webhook("FEISHU");

        dispatcher.dispatchOne(webhook, event);

        verify(feishuSender).send(webhook, event);
        verifyNoInteractions(webhookSender, weComSender, dingTalkSender, telegramSender);
    }

    @Test
    void routesTelegramChannelToTelegramSender() {
        WebhookDO webhook = webhook("TELEGRAM");

        dispatcher.dispatchOne(webhook, event);

        verify(telegramSender).send(webhook, event);
        verifyNoInteractions(webhookSender, weComSender, dingTalkSender, feishuSender);
    }

    @Test
    void routesWeComChannelToWeComSender() {
        WebhookDO webhook = webhook("WE_COM");

        dispatcher.dispatchOne(webhook, event);

        verify(weComSender).send(webhook, event);
        verifyNoInteractions(webhookSender, dingTalkSender, feishuSender, telegramSender);
    }

    @Test
    void unknownChannelFallsBackToGenericWebhookSender() {
        WebhookDO webhook = webhook("UNKNOWN");

        dispatcher.dispatchOne(webhook, event);

        // NotificationChannel.fromName 未知名称回落 WEBHOOK
        verify(webhookSender).send(webhook, event);
        verifyNoInteractions(weComSender, dingTalkSender, feishuSender, telegramSender);
    }

    @Test
    void missingDingTalkSenderIsSkippedWithoutError() {
        ReflectionTestUtils.setField(dispatcher, "dingTalkSender", null);
        WebhookDO webhook = webhook("DINGTALK");

        dispatcher.dispatchOne(webhook, event);

        verifyNoInteractions(webhookSender, weComSender, feishuSender, telegramSender);
    }

    private WebhookDO webhook(String channel) {
        WebhookDO webhook = new WebhookDO();
        webhook.setId(1L);
        webhook.setChannel(channel);
        webhook.setUrl("https://example.com/hook");
        return webhook;
    }
}
