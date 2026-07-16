package io.lumina.base.audit;

import io.lumina.base.infrastructure.entity.AuditLogDO;
import io.lumina.base.infrastructure.mapper.AuditLogMapper;
import io.lumina.framework.audit.event.AuditEvent;
import lombok.extern.slf4j.Slf4j;

/**
 * 审计日志持久化辅助类
 *
 * <p>提取 AuditEvent → AuditLogDO 的转换逻辑，供 {@link AuditLogEventListener}（降级路径）
 * 和 {@link AuditLogConsumer}（MQ 路径）共用。
 *
 * @author Lumina Team
 * @since 3.3.1
 */
@Slf4j
public final class AuditLogPersistenceHelper {

    private AuditLogPersistenceHelper() {}

    /**
     * 将 AuditEvent 转换为 AuditLogDO 并持久化
     */
    public static void persist(AuditEvent event, AuditLogMapper auditLogMapper) {
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
            log.warn("审计日志写入失败: module={}, action={}", event.getModule(), event.getAction(), e);
        }
    }
}
