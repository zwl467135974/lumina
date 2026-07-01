package io.lumina.base.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lumina.base.infrastructure.entity.AuditLogDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审计日志 Mapper
 *
 * @author Lumina Team
 * @since 1.1.0
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLogDO> {
}
