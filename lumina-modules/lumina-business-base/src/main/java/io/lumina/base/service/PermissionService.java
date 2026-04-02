package io.lumina.base.service;

import io.lumina.base.api.dto.permission.CreatePermissionDTO;
import io.lumina.base.api.dto.permission.UpdatePermissionDTO;
import io.lumina.base.api.vo.permission.PermissionVO;

import java.util.List;

public interface PermissionService {

    Long createPermission(CreatePermissionDTO dto);

    Boolean updatePermission(UpdatePermissionDTO dto);

    Boolean deletePermission(Long permissionId);

    List<PermissionVO> getPermissionTree();

    PermissionVO getPermissionById(Long permissionId);

    List<PermissionVO> listByType(Integer permissionType);
}
