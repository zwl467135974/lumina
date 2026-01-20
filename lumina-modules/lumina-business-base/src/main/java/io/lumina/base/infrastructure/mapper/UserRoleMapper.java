package io.lumina.base.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lumina.base.infrastructure.entity.UserRoleDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户-角色关联 Mapper
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Mapper
public interface UserRoleMapper extends BaseMapper<UserRoleDO> {
}
