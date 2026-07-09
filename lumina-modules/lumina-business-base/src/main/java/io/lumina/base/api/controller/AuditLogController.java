package io.lumina.base.api.controller;

import io.lumina.base.annotation.RequirePermission;
import io.lumina.base.infrastructure.entity.AuditLogDO;
import io.lumina.base.service.AuditLogService;
import io.lumina.common.core.PageResult;
import io.lumina.common.core.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.Min;

/**
 * 审计日志 Controller
 *
 * <p>提供审计日志分页查询接口。
 *
 * @author Lumina Team
 * @since 1.1.0
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    /**
     * 分页查询审计日志
     *
     * @param module   业务模块（可选）
     * @param action   操作类型（可选）
     * @param userId   操作人 ID（可选）
     * @param pageNum  页码
     * @param pageSize 每页数量
     */
    @GetMapping
    @RequirePermission("system:audit:list")
    public R<PageResult<AuditLogDO>> list(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
            @RequestParam(defaultValue = "20") @Min(1) Integer pageSize) {
        PageResult<AuditLogDO> pageResult = auditLogService.listAuditLogs(module, action, userId, pageNum, pageSize);
        return R.success(pageResult);
    }
}
