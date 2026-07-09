package io.lumina.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.lumina.base.infrastructure.entity.AuditLogDO;
import io.lumina.base.infrastructure.mapper.AuditLogMapper;
import io.lumina.base.service.AuditLogService;
import io.lumina.common.core.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 审计日志业务服务实现
 *
 * @author Lumina Team
 * @since 1.1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogMapper auditLogMapper;

    @Override
    public PageResult<AuditLogDO> listAuditLogs(String module, String action, Long userId,
                                                Integer pageNum, Integer pageSize) {
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

        return PageResult.of(result.getRecords(), result.getTotal(), pageNum, pageSize);
    }
}
