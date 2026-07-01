package io.lumina.base.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.lumina.base.infrastructure.entity.AuditLogDO;
import io.lumina.base.infrastructure.mapper.AuditLogMapper;
import io.lumina.common.core.PageResult;
import io.lumina.common.core.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.Min;
import java.util.stream.Collectors;

/**
 * 审计日志 Controller
 *
 * <p>提供审计日志分页查询接口。
 *
 * @author Lumina Team
 * @since 1.1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/audit-logs")
@org.springframework.validation.annotation.Validated
public class AuditLogController {

    @Autowired
    private AuditLogMapper auditLogMapper;

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
    public R<PageResult<AuditLogDO>> list(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
            @RequestParam(defaultValue = "20") @Min(1) Integer pageSize) {

        LambdaQueryWrapper<AuditLogDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(module)) {
            wrapper.eq(AuditLogDO::getModule, module);
        }
        if (StringUtils.hasText(action)) {
            wrapper.eq(AuditLogDO::getAction, action);
        }
        if (userId != null) {
            wrapper.eq(AuditLogDO::getUserId, userId);
        }
        wrapper.orderByDesc(AuditLogDO::getCreateTime);

        Page<AuditLogDO> page = new Page<>(pageNum, pageSize);
        Page<AuditLogDO> result = auditLogMapper.selectPage(page, wrapper);

        PageResult<AuditLogDO> pageResult = new PageResult<>();
        pageResult.setList(result.getRecords());
        pageResult.setTotal(result.getTotal());
        pageResult.setPageNum(pageNum);
        pageResult.setPageSize(pageSize);
        pageResult.setPages((int) result.getPages());

        return R.success(pageResult);
    }
}
