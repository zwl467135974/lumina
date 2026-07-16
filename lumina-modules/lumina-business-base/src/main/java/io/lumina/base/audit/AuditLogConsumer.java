package io.lumina.base.audit;

import io.lumina.base.infrastructure.mapper.AuditLogMapper;
import io.lumina.framework.audit.event.AuditEvent;
import io.lumina.framework.config.RocketMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 审计日志 MQ 消费者
 *
 * <p>消费 {@link RocketMQConfig#TOPIC_AUDIT_LOG} 主题的审计事件，持久化到数据库。
 * 相比 @Async 本地监听器，MQ 消费支持跨实例消费、削峰填谷、失败重试。
 *
 * <p>启用条件：rocketmq.consumer.audit-log.enabled=true（默认 true，MQ 可用时自动启用）。
 *
 * @author Lumina Team
 * @since 3.3.1
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "rocketmq.consumer.audit-log",
        name = "enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(
        consumerGroup = RocketMQConfig.GROUP_AUDIT_LOG,
        topic = RocketMQConfig.TOPIC_AUDIT_LOG)
public class AuditLogConsumer implements RocketMQListener<AuditEvent> {

    @Autowired
    private AuditLogMapper auditLogMapper;

    @Override
    public void onMessage(AuditEvent event) {
        AuditLogPersistenceHelper.persist(event, auditLogMapper);
    }
}
