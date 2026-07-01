package io.lumina.base.audit;

import io.lumina.base.infrastructure.entity.AuditLogDO;
import io.lumina.base.infrastructure.mapper.AuditLogMapper;
import io.lumina.framework.audit.event.AuditEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 审计日志事件监听器
 *
 * <p>监听 {@link AuditEvent}（由 AuditAspect 发布），转换为 DO 持久化到审计表。
 *
 * @author Lumina Team
 * @since 1.1.0
 */
@Slf4j
@Component
public class AuditLogEventListener {

    @Autowired
    private AuditLogMapper auditLogMapper;

    @EventListener(AuditEvent.class)
    public void onAuditEvent(AuditEvent event) {
        try {
            AuditLogDO log = new AuditLogDO();
            log.setTenantId(event.getTenantId());
            log.setUserId(event.getUserId());
            log.setUsername(event.getUsername());
            log.setModule(event.getModule());
            log.setAction(event.getAction());
            log.setTargetType(event.getTargetType());
            log.setTargetId(event.getTargetId());
            log.setDescription(event.getDescription());
            log.setRequestMethod(event.getRequestMethod());
            log.setRequestUrl(event.getRequestUrl());
            log.setRequestIp(event.getRequestIp());
            log.setStatus(event.isSuccess() ? 1 : 0);
            log.setErrorMsg(event.getErrorMsg());
            log.setDurationMs(event.getDurationMs());

            auditLogMapper.insert(log);
        } catch (Exception e) {
            // 审计失败不影响主流程，仅记录日志
            AuditLogEventListener.log.warn("审计日志写入失败: module={}, action={}",
                    event.getModule(), event.getAction(), e);
        }
    }
}
