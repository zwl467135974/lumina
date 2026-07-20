package io.lumina.notification.service;

import io.lumina.common.core.BaseContext;
import io.lumina.notification.domain.enums.NotificationChannel;
import io.lumina.notification.event.NotificationEvent;
import io.lumina.notification.infrastructure.entity.WebhookDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Webhook 分发器
 *
 * <p>通知事件持久化 + SSE 推送后，异步分发到该用户订阅的外部 webhook，
 * 按 channel 路由到对应 Sender（WEBHOOK/WE_COM/DINGTALK/FEISHU/TELEGRAM），不阻塞通知主链。
 *
 * <p>可通过 {@code lumina.notification.webhook.enabled=false} 关闭。
 *
 * @author Lumina Team
 * @since 3.4.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "lumina.notification.webhook", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WebhookDispatcher {

    private final WebhookService webhookService;
    private final WebhookSender webhookSender;
    private final WeComSender weComSender;

    /**
     * IM 渠道 Sender 可选注入（未装配时对应渠道跳过，不阻断启动）
     */
    @Autowired(required = false)
    private DingTalkSender dingTalkSender;

    @Autowired(required = false)
    private FeishuSender feishuSender;

    @Autowired(required = false)
    private TelegramSender telegramSender;

    /**
     * 异步分发事件到用户订阅的 webhook（广播事件 userId=null 跳过）
     *
     * <p>运行在 webhookExecutor 线程，需手动传播租户上下文
     * （TenantLineHandler 依赖 BaseContext 追加租户条件）。
     *
     * @param event 通知事件
     */
    @Async("webhookExecutor")
    public void dispatch(NotificationEvent event) {
        if (event == null || event.getUserId() == null) {
            return;
        }
        // 线程池拒绝降级（CallerRunsPolicy）时会在调用线程执行，保存并恢复原上下文
        Long prevUserId = BaseContext.getUserId();
        Long prevTenantId = BaseContext.getTenantId();
        try {
            BaseContext.setUserId(event.getUserId());
            BaseContext.setTenantId(event.getTenantId() != null ? event.getTenantId() : 0L);

            List<WebhookDO> webhooks = webhookService.findEnabledForEvent(event.getUserId(), event.getCategory());
            for (WebhookDO webhook : webhooks) {
                try {
                    dispatchOne(webhook, event);
                } catch (Throwable t) {
                    log.warn("Webhook 分发异常 whId={}: {}", webhook.getId(), t.getMessage());
                }
            }
        } catch (Throwable t) {
            log.warn("Webhook 分发失败 userId={}, category={}: {}",
                    event.getUserId(), event.getCategory(), t.getMessage());
        } finally {
            BaseContext.clear();
            if (prevUserId != null) {
                BaseContext.setUserId(prevUserId);
            }
            if (prevTenantId != null) {
                BaseContext.setTenantId(prevTenantId);
            }
        }
    }

    /**
     * 单个 webhook 按 channel 路由到对应 Sender（IM Sender 未装配时跳过）
     *
     * @param webhook webhook 订阅
     * @param event   通知事件
     */
    void dispatchOne(WebhookDO webhook, NotificationEvent event) {
        switch (NotificationChannel.fromName(webhook.getChannel())) {
            case WE_COM -> weComSender.send(webhook, event);
            case DINGTALK -> {
                if (dingTalkSender != null) {
                    dingTalkSender.send(webhook, event);
                } else {
                    log.warn("DingTalkSender 未装配，跳过 whId={}", webhook.getId());
                }
            }
            case FEISHU -> {
                if (feishuSender != null) {
                    feishuSender.send(webhook, event);
                } else {
                    log.warn("FeishuSender 未装配，跳过 whId={}", webhook.getId());
                }
            }
            case TELEGRAM -> {
                if (telegramSender != null) {
                    telegramSender.send(webhook, event);
                } else {
                    log.warn("TelegramSender 未装配，跳过 whId={}", webhook.getId());
                }
            }
            case WEBHOOK -> webhookSender.send(webhook, event);
        }
    }
}
