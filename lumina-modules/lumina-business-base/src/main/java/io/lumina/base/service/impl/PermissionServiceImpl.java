package io.lumina.base.service.impl;

import io.lumina.base.api.vo.permission.PermissionVO;
import io.lumina.base.infrastructure.entity.PermissionDO;
import io.lumina.base.infrastructure.mapper.PermissionMapper;
import io.lumina.base.service.PermissionService;
import io.lumina.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 权限服务实现
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
@Service
public class PermissionServiceImpl implements PermissionService {

    @Autowired
    private PermissionMapper permissionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPermission(String permissionCode, String permissionName, Long parentId, Integer permissionType) {
        log.info("创建权限: permissionCode={}", permissionCode);

        // 1. 验证权限编码唯一性
        List<PermissionDO> existing = permissionMapper.selectByCode(permissionCode);
        if (!existing.isEmpty()) {
            throw new BusinessException("权限编码已存在");
        }

        // 2. 创建权限
        PermissionDO permissionDO = new PermissionDO();
        permissionDO.setPermissionCode(permissionCode);
        permissionDO.setPermissionName(permissionName);
        permissionDO.setParentId(parentId != null ? parentId : 0L);
        permissionDO.setPermissionType(permissionType);
        permissionDO.setStatus(1);

        permissionMapper.insert(permissionDO);

        log.info("权限创建成功: permissionId={}", permissionDO.getPermissionId());
        return permissionDO.getPermissionId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updatePermission(Long permissionId, String permissionName) {
        PermissionDO permissionDO = permissionMapper.selectById(permissionId);
        if (permissionDO == null) {
            throw new BusinessException("权限不存在");
        }

        permissionDO.setPermissionName(permissionName);
        int result = permissionMapper.updateById(permissionDO);

        log.info("权限更新成功: permissionId={}", permissionId);
        return result > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deletePermission(Long permissionId) {
        PermissionDO permissionDO = permissionMapper.selectById(permissionId);
        if (permissionDO == null) {
            throw new BusinessException("权限不存在");
        }

        permissionDO.setDeleted(1);
        int result = permissionMapper.updateById(permissionDO);

        log.info("权限删除成功: permissionId={}", permissionId);
        return result > 0;
    }

    @Override
    public List<PermissionVO> getPermissionTree() {
        List<PermissionDO> allPermissions = permissionMapper.selectAllPermissions();
        return buildTree(allPermissions, 0L);
    }

    @Override
    public PermissionVO getPermissionById(Long permissionId) {
        PermissionDO permissionDO = permissionMapper.selectById(permissionId);
        if (permissionDO == null) {
            throw new BusinessException("权限不存在");
        }

        PermissionVO vo = new PermissionVO();
        BeanUtils.copyProperties(permissionDO, vo);
        return vo;
    }

    /**
     * 构建权限树
     */
    private List<PermissionVO> buildTree(List<PermissionDO> permissions, Long parentId) {
        List<PermissionVO> tree = new ArrayList<>();

        for (PermissionDO permission : permissions) {
            if (permission.getParentId().equals(parentId)) {
                PermissionVO vo = toVO(permission);
                vo.setChildren(buildTree(permissions, permission.getPermissionId()));
                tree.add(vo);
            }
        }

        return tree;
    }

    private PermissionVO toVO(PermissionDO permissionDO) {
        PermissionVO vo = new PermissionVO();
        BeanUtils.copyProperties(permissionDO, vo);
        return vo;
    }
}
