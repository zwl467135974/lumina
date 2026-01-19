package io.lumina.agent.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lumina.agent.infrastructure.entity.UserDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * User Mapper
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Mapper
public interface UserMapper extends BaseMapper<UserDO> {
}
