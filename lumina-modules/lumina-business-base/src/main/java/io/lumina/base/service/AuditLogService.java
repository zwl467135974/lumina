package io.lumina.base.service;

import io.lumina.base.infrastructure.entity.AuditLogDO;
import io.lumina.common.core.PageResult;

/**
 * 审计日志业务服务
 *
 * @author Lumina Team
 * @since 1.1.0
 */
public interface AuditLogService {

    /**
     * 分页查询审计日志
     *
     * @param module   业务模块（可选）
     * @param action   操作类型（可选）
     * @param userId   操作人 ID（可选）
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return 分页结果
     */
    PageResult<AuditLogDO> listAuditLogs(String module, String action, Long userId, Integer pageNum, Integer pageSize);
}
