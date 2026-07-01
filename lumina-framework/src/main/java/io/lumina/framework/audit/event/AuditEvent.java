package io.lumina.framework.audit.event;

import lombok.Builder;
import lombok.Getter;

/**
 * 审计事件
 *
 * <p>由 {@code AuditAspect} 发布，由审计监听器消费并持久化。
 * Spring 4.2+ 支持发布任意 POJO 作为事件，无需继承 ApplicationEvent。
 *
 * @author Lumina Team
 * @since 1.1.0
 */
@Getter
@Builder
public class AuditEvent {

    /** 业务模块 */
    private final String module;
    /** 操作类型 */
    private final String action;
    /** 描述 */
    private final String description;
    /** 目标类型（如 User/Role/Agent） */
    private final String targetType;
    /** 目标 ID */
    private final String targetId;
    /** 是否成功 */
    private final boolean success;
    /** 错误信息（失败时） */
    private final String errorMsg;
    /** 耗时（毫秒） */
    private final long durationMs;
    /** 租户 ID */
    private final Long tenantId;
    /** 用户 ID */
    private final Long userId;
    /** 用户名 */
    private final String username;
    /** HTTP 请求方法 */
    private final String requestMethod;
    /** 请求 URL */
    private final String requestUrl;
    /** 请求 IP */
    private final String requestIp;
}
