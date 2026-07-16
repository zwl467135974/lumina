package io.lumina.notification.infrastructure.mq;

import io.lumina.notification.event.NotificationEvent;
import io.lumina.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

/**
 * NotificationConsumer 单元测试
 *
 * <p>验证通知消费者正确委托给 NotificationService，异常不传播。
 *
 * @author Lumina Team
 * @since 3.3.1
 */
@ExtendWith(MockitoExtension.class)
class NotificationConsumerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationConsumer consumer;

    @Test
    void onMessageDelegatesToService() {
        NotificationEvent event = new NotificationEvent(
                100L, "TASK", "任务完成", "Agent 任务已完成", "INFO",
                "agent_task", "task-uuid-1", 1L);

        consumer.onMessage(event);

        verify(notificationService).handleEvent(event);
    }

    @Test
    void serviceExceptionDoesNotPropagate() {
        NotificationEvent event = new NotificationEvent(
                200L, "SYSTEM", "系统告警", "CPU 过高", "WARN",
                null, null, 1L);

        doThrow(new RuntimeException("DB error"))
                .when(notificationService).handleEvent(event);

        // 异常不应传播（Consumer 吞掉避免 MQ 无限重试）
        assertThatCode(() -> consumer.onMessage(event)).doesNotThrowAnyException();

        verify(notificationService).handleEvent(event);
    }
}
