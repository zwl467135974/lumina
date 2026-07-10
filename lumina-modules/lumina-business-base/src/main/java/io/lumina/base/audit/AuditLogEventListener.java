package io.lumina.base.audit;

import io.lumina.base.infrastructure.entity.AuditLogDO;
import io.lumina.base.infrastructure.mapper.AuditLogMapper;
import io.lumina.framework.audit.event.AuditEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 审计日志事件监听器
 *
 * <p>监听 {@link AuditEvent}（由 AuditAspect 发布），异步持久化到审计表。
 * 使用 auditExecutor 线程池，不阻塞业务请求线程。
 *
 * @author Lumina Team
 * @since 1.1.0
 */
@Slf4j
@Component
public class AuditLogEventListener {

    @Autowired
    private AuditLogMapper auditLogMapper;

    @Async("auditExecutor")
    @EventListener(AuditEvent.class)
    public void onAuditEvent(AuditEvent event) {
        try {
            AuditLogDO auditLog = new AuditLogDO();
            auditLog.setTenantId(event.getTenantId());
            auditLog.setUserId(event.getUserId());
            auditLog.setUsername(event.getUsername());
            auditLog.setModule(event.getModule());
            auditLog.setAction(event.getAction());
            auditLog.setTargetType(event.getTargetType());
            auditLog.setTargetId(event.getTargetId());
            auditLog.setDescription(event.getDescription());
            auditLog.setRequestMethod(event.getRequestMethod());
            auditLog.setRequestUrl(event.getRequestUrl());
            auditLog.setRequestIp(event.getRequestIp());
            auditLog.setStatus(event.isSuccess() ? 1 : 0);
            auditLog.setErrorMsg(event.getErrorMsg());
            auditLog.setDurationMs(event.getDurationMs());

            auditLogMapper.insert(auditLog);
        } catch (Exception e) {
            AuditLogEventListener.log.warn("审计日志写入失败: module={}, action={}",
                    event.getModule(), event.getAction(), e);
        }
    }
}
