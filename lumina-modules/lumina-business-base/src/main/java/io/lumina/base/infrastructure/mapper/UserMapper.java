package io.lumina.base.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lumina.base.infrastructure.entity.UserDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户 Mapper
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Mapper
public interface UserMapper extends BaseMapper<UserDO> {

    /**
     * 根据租户 ID 和用户名查询用户
     *
     * @param tenantId 租户 ID
     * @param username 用户名
     * @return 用户实体
     */
    @Select("SELECT * FROM lumina_user WHERE tenant_id = #{tenantId} AND username = #{username} AND deleted = 0")
    UserDO selectByTenantIdAndUsername(@Param("tenantId") Long tenantId, @Param("username") String username);

    /**
     * 根据用户 ID 查询用户角色 ID 列表
     *
     * @param userId 用户 ID
     * @return 角色 ID 列表
     */
    @Select("SELECT role_id FROM lumina_user_role WHERE user_id = #{userId}")
    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);
}
