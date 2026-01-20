package io.lumina.base.service;

import io.lumina.base.api.vo.permission.PermissionVO;

import java.util.List;

/**
 * 权限服务
 *
 * @author Lumina Team
 * @since 1.0.0
 */
public interface PermissionService {

    /**
     * 创建权限
     *
     * @param permissionCode 权限编码
     * @param permissionName 权限名称
     * @param parentId 父权限ID
     * @param permissionType 权限类型
     * @return 权限ID
     */
    Long createPermission(String permissionCode, String permissionName, Long parentId, Integer permissionType);

    /**
     * 更新权限
     *
     * @param permissionId 权限ID
     * @param permissionName 权限名称
     * @return 是否成功
     */
    Boolean updatePermission(Long permissionId, String permissionName);

    /**
     * 删除权限
     *
     * @param permissionId 权限ID
     * @return 是否成功
     */
    Boolean deletePermission(Long permissionId);

    /**
     * 获取权限树
     *
     * @return 权限树
     */
    List<PermissionVO> getPermissionTree();

    /**
     * 获取权限详情
     *
     * @param permissionId 权限ID
     * @return 权限 VO
     */
    PermissionVO getPermissionById(Long permissionId);
}
