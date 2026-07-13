package io.lumina.notification.infrastructure.mq;

import io.lumina.framework.config.RocketMQConfig;
import io.lumina.notification.event.NotificationEvent;
import io.lumina.notification.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 通知消息消费者
 *
 * <p>接收来自 agent-service 的 {@link NotificationEvent}，持久化并推送给用户。
 * 仅在 {@code rocketmq.consumer.notification.enabled=true} 时注册（base 服务默认开启）。
 *
 * @author Lumina Team
 * @since 3.2.0
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "rocketmq.consumer.notification", name = "enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(
        consumerGroup = RocketMQConfig.GROUP_NOTIFICATION,
        topic = RocketMQConfig.TOPIC_NOTIFICATION
)
public class NotificationConsumer implements RocketMQListener<NotificationEvent> {

    @Autowired
    private NotificationService notificationService;

    @Override
    public void onMessage(NotificationEvent event) {
        log.info("收到通知事件: userId={}, category={}, title={}", event.getUserId(), event.getCategory(), event.getTitle());
        try {
            notificationService.handleEvent(event);
        } catch (Exception e) {
            log.error("处理通知事件失败: {}", event, e);
            // 不抛异常，避免 MQ 无限重试；通知丢失可接受（非核心链路）
        }
    }
}
