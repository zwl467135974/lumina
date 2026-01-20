package io.lumina.base.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lumina.base.infrastructure.entity.RolePermissionDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色-权限关联 Mapper
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermissionDO> {
}
