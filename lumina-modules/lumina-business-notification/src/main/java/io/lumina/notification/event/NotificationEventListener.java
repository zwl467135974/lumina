package io.lumina.notification.event;

import io.lumina.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 通知事件监听器（降级路径）
 *
 * <p>当 RocketMQ 不可用时（standalone 单体模式），{@link NotificationEventPublisher}
 * 降级发布 Spring ApplicationEvent，本监听器异步持久化并推送 SSE。
 *
 * <p>MQ 可用时事件走 MQ、不发本地事件，本监听器不触发，与 NotificationConsumer 不重复消费。
 * 线程池复用 auditExecutor（框架降级链路共用，通知量小且非核心链路）。
 *
 * @author Lumina Team
 * @since 3.4.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    @Async("auditExecutor")
    @EventListener(NotificationEvent.class)
    public void onNotificationEvent(NotificationEvent event) {
        log.info("收到本地通知事件: userId={}, category={}, title={}",
                event.getUserId(), event.getCategory(), event.getTitle());
        try {
            notificationService.handleEvent(event);
        } catch (Exception e) {
            log.error("处理本地通知事件失败: {}", event, e);
            // 不向上抛，通知丢失可接受（非核心链路），与 NotificationConsumer 行为一致
        }
    }
}
