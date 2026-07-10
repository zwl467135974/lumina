package io.lumina.base.api.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审计日志 VO
 *
 * @author Lumina Team
 * @since 1.1.0
 */
@Data
public class AuditLogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long auditId;
    private Long tenantId;
    private Long userId;
    private String username;
    private String module;
    private String action;
    private String targetType;
    private String targetId;
    private String description;
    private String requestMethod;
    private String requestUrl;
    private String requestIp;
    private Integer status;
    private String errorMsg;
    private Long durationMs;
    private LocalDateTime createTime;
}
