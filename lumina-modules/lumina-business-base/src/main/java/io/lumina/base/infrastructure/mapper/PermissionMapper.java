package io.lumina.base.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lumina.base.infrastructure.entity.PermissionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 权限 Mapper
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Mapper
public interface PermissionMapper extends BaseMapper<PermissionDO> {

    /**
     * 根据权限 ID 列表查询权限列表
     *
     * @param permissionIds 权限 ID 列表
     * @return 权限列表
     */
    @Select("<script>" +
            "SELECT * FROM lumina_permission WHERE permission_id IN " +
            "<foreach collection='permissionIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            " AND deleted = 0" +
            "</script>")
    List<PermissionDO> selectPermissionsByIds(@Param("permissionIds") List<Long> permissionIds);

    /**
     * 查询所有权限（按父 ID 排序）
     *
     * @return 所有权限列表
     */
    @Select("SELECT * FROM lumina_permission WHERE deleted = 0 ORDER BY parent_id, sort_order")
    List<PermissionDO> selectAllPermissions();
}
