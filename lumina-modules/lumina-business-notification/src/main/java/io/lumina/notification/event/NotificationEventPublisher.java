package io.lumina.notification.event;

import io.lumina.framework.config.RocketMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 通知事件发布器
 *
 * <p>发布策略（自动降级，与 AuditAspect 一致）：
 * <ol>
 *   <li>RocketMQ 可用时 → 发送到 {@link RocketMQConfig#TOPIC_NOTIFICATION}，
 *       由 NotificationConsumer 跨服务消费持久化</li>
 *   <li>RocketMQ 不可用时（standalone 单体模式 exclude 了 RocketMQ autoconfiguration）
 *       → 降级为 Spring ApplicationEvent，由 {@link NotificationEventListener} 本地消费</li>
 * </ol>
 *
 * <p>业务方发通知统一走本发布器，不要直接注入 RocketMQTemplate。
 *
 * @author Lumina Team
 * @since 3.4.0
 */
@Slf4j
@Component
public class NotificationEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    @Autowired(required = false)
    private RocketMQTemplate rocketMQTemplate;

    public NotificationEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * 发布通知事件（优先 MQ，降级 Spring 本地事件；二者只走其一，无需去重）
     *
     * @param event 通知事件
     */
    public void publish(NotificationEvent event) {
        if (event == null) {
            return;
        }
        if (rocketMQTemplate != null) {
            try {
                rocketMQTemplate.convertAndSend(RocketMQConfig.TOPIC_NOTIFICATION, event);
                return;
            } catch (Exception e) {
                log.warn("通知事件发送 MQ 失败，降级为本地事件: {}", e.getMessage());
            }
        }
        eventPublisher.publishEvent(event);
    }
}
