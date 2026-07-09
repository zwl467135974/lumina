package io.lumina.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.lumina.base.api.dto.permission.CreatePermissionDTO;
import io.lumina.base.api.dto.permission.UpdatePermissionDTO;
import io.lumina.base.api.vo.permission.PermissionVO;
import io.lumina.base.infrastructure.entity.PermissionDO;
import io.lumina.base.infrastructure.mapper.PermissionMapper;
import io.lumina.base.service.PermissionService;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PermissionServiceImpl implements PermissionService {

    @Autowired
    private PermissionMapper permissionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPermission(CreatePermissionDTO dto) {
        log.info("创建权限: permissionCode={}", dto.getPermissionCode());

        List<PermissionDO> existing = permissionMapper.selectByCode(dto.getPermissionCode());
        if (!existing.isEmpty()) {
            throw new BusinessException(ErrorCode.PERMISSION_ALREADY_EXISTS);
        }

        PermissionDO permissionDO = new PermissionDO();
        permissionDO.setPermissionCode(dto.getPermissionCode());
        permissionDO.setPermissionName(dto.getPermissionName());
        permissionDO.setParentId(dto.getParentId() != null ? dto.getParentId() : 0L);
        permissionDO.setPermissionType(dto.getPermissionType());
        permissionDO.setPath(dto.getPath());
        permissionDO.setComponent(dto.getComponent());
        permissionDO.setIcon(dto.getIcon());
        permissionDO.setSortOrder(dto.getSortOrder());
        permissionDO.setVisible(dto.getVisible());
        permissionDO.setStatus(1);

        permissionMapper.insert(permissionDO);

        log.info("权限创建成功: permissionId={}", permissionDO.getPermissionId());
        return permissionDO.getPermissionId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updatePermission(UpdatePermissionDTO dto) {
        PermissionDO permissionDO = permissionMapper.selectById(dto.getPermissionId());
        if (permissionDO == null) {
            throw new BusinessException(ErrorCode.PERMISSION_NOT_FOUND);
        }

        if (dto.getPermissionName() != null) {
            permissionDO.setPermissionName(dto.getPermissionName());
        }
        if (dto.getPath() != null) {
            permissionDO.setPath(dto.getPath());
        }
        if (dto.getComponent() != null) {
            permissionDO.setComponent(dto.getComponent());
        }
        if (dto.getIcon() != null) {
            permissionDO.setIcon(dto.getIcon());
        }
        if (dto.getSortOrder() != null) {
            permissionDO.setSortOrder(dto.getSortOrder());
        }
        if (dto.getVisible() != null) {
            permissionDO.setVisible(dto.getVisible());
        }
        if (dto.getStatus() != null) {
            permissionDO.setStatus(dto.getStatus());
        }

        int result = permissionMapper.updateById(permissionDO);
        log.info("权限更新成功: permissionId={}", dto.getPermissionId());
        return result > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deletePermission(Long permissionId) {
        PermissionDO permissionDO = permissionMapper.selectById(permissionId);
        if (permissionDO == null) {
            throw new BusinessException(ErrorCode.PERMISSION_NOT_FOUND);
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
            throw new BusinessException(ErrorCode.PERMISSION_NOT_FOUND);
        }

        PermissionVO vo = new PermissionVO();
        BeanUtils.copyProperties(permissionDO, vo);
        return vo;
    }

    @Override
    public List<PermissionVO> listByType(Integer permissionType) {
        List<PermissionDO> list = permissionMapper.selectList(
                new LambdaQueryWrapper<PermissionDO>()
                        .eq(PermissionDO::getPermissionType, permissionType)
                        .eq(PermissionDO::getDeleted, 0)
        );
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public List<PermissionVO> listAllPermissions() {
        return permissionMapper.selectAllPermissions().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

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
