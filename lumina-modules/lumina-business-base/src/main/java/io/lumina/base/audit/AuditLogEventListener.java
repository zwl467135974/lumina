package io.lumina.base.audit;

import io.lumina.base.infrastructure.entity.AuditLogDO;
import io.lumina.base.infrastructure.mapper.AuditLogMapper;
import io.lumina.framework.audit.event.AuditEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 审计日志事件监听器（降级路径）
 *
 * <p>当 RocketMQ 不可用时，AuditAspect 降级发布 Spring ApplicationEvent，
 * 本监听器通过 auditExecutor 线程池异步持久化到审计表。
 *
 * <p>MQ 可用时由 {@link AuditLogConsumer} 消费，本监听器不触发。
 *
 * @author Lumina Team
 * @since 1.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogEventListener {

    private final AuditLogMapper auditLogMapper;

    @Async("auditExecutor")
    @EventListener(AuditEvent.class)
    public void onAuditEvent(AuditEvent event) {
        AuditLogPersistenceHelper.persist(event, auditLogMapper);
    }
}
