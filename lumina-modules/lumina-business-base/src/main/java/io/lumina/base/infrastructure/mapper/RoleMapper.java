package io.lumina.base.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lumina.base.infrastructure.entity.RoleDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色 Mapper
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Mapper
public interface RoleMapper extends BaseMapper<RoleDO> {

    /**
     * 根据用户 ID 查询角色列表
     *
     * @param userId 用户 ID
     * @return 角色列表
     */
    @Select("SELECT r.* FROM lumina_role r " +
            "INNER JOIN lumina_user_role ur ON r.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND r.deleted = 0")
    List<RoleDO> selectRolesByUserId(@Param("userId") Long userId);

    /**
     * 根据角色 ID 列表查询权限 ID 列表
     *
     * @param roleIds 角色 ID 列表
     * @return 权限 ID 列表
     */
    @Select("<script>" +
            "SELECT permission_id FROM lumina_role_permission WHERE role_id IN " +
            "<foreach collection='roleIds' item='roleId' open='(' separator=',' close=')'>" +
            "#{roleId}" +
            "</foreach>" +
            "</script>")
    List<Long> selectPermissionIdsByRoleIds(@Param("roleIds") List<Long> roleIds);
}
