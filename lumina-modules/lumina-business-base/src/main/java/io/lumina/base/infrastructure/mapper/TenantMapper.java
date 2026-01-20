package io.lumina.base.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lumina.base.infrastructure.entity.TenantDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户 Mapper
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Mapper
public interface TenantMapper extends BaseMapper<TenantDO> {
}
