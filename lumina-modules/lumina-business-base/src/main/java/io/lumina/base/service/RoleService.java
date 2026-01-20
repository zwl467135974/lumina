package io.lumina.base.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.lumina.base.api.dto.role.AssignPermissionDTO;
import io.lumina.base.api.dto.role.CreateRoleDTO;
import io.lumina.base.api.dto.role.RoleQueryDTO;
import io.lumina.base.api.dto.role.UpdateRoleDTO;
import io.lumina.base.api.vo.role.RoleVO;

import java.util.List;

/**
 * 角色服务
 *
 * @author Lumina Team
 * @since 1.0.0
 */
public interface RoleService {

    /**
     * 创建角色
     *
     * @param dto 创建角色 DTO
     * @return 角色ID
     */
    Long createRole(CreateRoleDTO dto);

    /**
     * 更新角色
     *
     * @param dto 更新角色 DTO
     * @return 是否成功
     */
    Boolean updateRole(UpdateRoleDTO dto);

    /**
     * 删除角色
     *
     * @param roleId 角色ID
     * @return 是否成功
     */
    Boolean deleteRole(Long roleId);

    /**
     * 获取角色详情
     *
     * @param roleId 角色ID
     * @return 角色 VO
     */
    RoleVO getRoleById(Long roleId);

    /**
     * 分页查询角色
     *
     * @param dto 查询条件
     * @return 分页结果
     */
    Page<RoleVO> listRoles(RoleQueryDTO dto);

    /**
     * 分配权限
     *
     * @param dto 分配权限 DTO
     * @return 是否成功
     */
    Boolean assignPermissions(AssignPermissionDTO dto);

    /**
     * 获取角色的权限ID列表
     *
     * @param roleId 角色ID
     * @return 权限ID列表
     */
    List<Long> getRolePermissionIds(Long roleId);
}
