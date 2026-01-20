package io.lumina.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.lumina.base.api.dto.role.AssignPermissionDTO;
import io.lumina.base.api.dto.role.CreateRoleDTO;
import io.lumina.base.api.dto.role.RoleQueryDTO;
import io.lumina.base.api.dto.role.UpdateRoleDTO;
import io.lumina.base.api.vo.role.RoleVO;
import io.lumina.base.infrastructure.entity.PermissionDO;
import io.lumina.base.infrastructure.entity.RoleDO;
import io.lumina.base.infrastructure.entity.RolePermissionDO;
import io.lumina.base.infrastructure.mapper.PermissionMapper;
import io.lumina.base.infrastructure.mapper.RoleMapper;
import io.lumina.base.infrastructure.mapper.RolePermissionMapper;
import io.lumina.base.infrastructure.mapper.UserRoleMapper;
import io.lumina.base.service.RoleService;
import io.lumina.common.core.BaseContext;
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
 * 角色服务实现
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
@Service
public class RoleServiceImpl implements RoleService {

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private RolePermissionMapper rolePermissionMapper;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Autowired
    private PermissionMapper permissionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRole(CreateRoleDTO dto) {
        log.info("创建角色: roleCode={}, tenantId={}", dto.getRoleCode(), BaseContext.getTenantId());

        // 1. 验证角色编码唯一性（租户内）
        LambdaQueryWrapper<RoleDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RoleDO::getTenantId, BaseContext.getTenantId());
        wrapper.eq(RoleDO::getRoleCode, dto.getRoleCode());
        RoleDO existingRole = roleMapper.selectOne(wrapper);
        if (existingRole != null) {
            throw new BusinessException("角色编码已存在");
        }

        // 2. 创建角色实体
        RoleDO roleDO = new RoleDO();
        roleDO.setTenantId(BaseContext.getTenantId());
        roleDO.setRoleCode(dto.getRoleCode());
        roleDO.setRoleName(dto.getRoleName());
        roleDO.setDescription(dto.getDescription());
        roleDO.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        roleDO.setStatus(1);

        // 3. 插入角色
        roleMapper.insert(roleDO);

        // 4. 分配权限（如果有）
        if (dto.getPermissionIds() != null && dto.getPermissionIds().length > 0) {
            for (Long permissionId : dto.getPermissionIds()) {
                RolePermissionDO rpDO = new RolePermissionDO();
                rpDO.setRoleId(roleDO.getRoleId());
                rpDO.setPermissionId(permissionId);
                rolePermissionMapper.insert(rpDO);
            }
        }

        log.info("角色创建成功: roleId={}, roleCode={}", roleDO.getRoleId(), roleDO.getRoleCode());
        return roleDO.getRoleId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateRole(UpdateRoleDTO dto) {
        log.info("更新角色: roleId={}", dto.getRoleId());

        // 1. 查询角色
        RoleDO roleDO = roleMapper.selectById(dto.getRoleId());
        if (roleDO == null) {
            throw new BusinessException("角色不存在");
        }

        // 2. 租户隔离验证
        if (!roleDO.getTenantId().equals(BaseContext.getTenantId())) {
            throw new BusinessException("无权限操作此角色");
        }

        // 3. 不能修改系统角色
        if (roleDO.getTenantId() == 0) {
            throw new BusinessException("不能修改系统角色");
        }

        // 4. 更新字段
        if (dto.getRoleName() != null) {
            roleDO.setRoleName(dto.getRoleName());
        }
        if (dto.getDescription() != null) {
            roleDO.setDescription(dto.getDescription());
        }
        if (dto.getSortOrder() != null) {
            roleDO.setSortOrder(dto.getSortOrder());
        }
        if (dto.getStatus() != null) {
            roleDO.setStatus(dto.getStatus());
        }

        // 5. 更新
        int result = roleMapper.updateById(roleDO);

        log.info("角色更新成功: roleId={}", dto.getRoleId());
        return result > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteRole(Long roleId) {
        log.info("删除角色: roleId={}", roleId);

        // 1. 查询角色
        RoleDO roleDO = roleMapper.selectById(roleId);
        if (roleDO == null) {
            throw new BusinessException("角色不存在");
        }

        // 2. 租户隔离验证
        if (!roleDO.getTenantId().equals(BaseContext.getTenantId())) {
            throw new BusinessException("无权限操作此角色");
        }

        // 3. 不能删除系统角色
        if (roleDO.getTenantId() == 0) {
            throw new BusinessException("不能删除系统角色");
        }

        // 4. 检查是否有用户使用此角色
        LambdaQueryWrapper<UserRoleDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserRoleDO::getRoleId, roleId);
        Long count = userRoleMapper.selectCount(wrapper);
        if (count > 0) {
            throw new BusinessException("角色正在使用中，无法删除");
        }

        // 5. 删除角色权限关联
        LambdaQueryWrapper<RolePermissionDO> rpWrapper = new LambdaQueryWrapper<>();
        rpWrapper.eq(RolePermissionDO::getRoleId, roleId);
        rolePermissionMapper.delete(rpWrapper);

        // 6. 逻辑删除角色
        roleDO.setDeleted(1);
        int result = roleMapper.updateById(roleDO);

        log.info("角色删除成功: roleId={}", roleId);
        return result > 0;
    }

    @Override
    public RoleVO getRoleById(Long roleId) {
        log.info("查询角色详情: roleId={}", roleId);

        // 1. 查询角色
        RoleDO roleDO = roleMapper.selectById(roleId);
        if (roleDO == null) {
            throw new BusinessException("角色不存在");
        }

        // 2. 租户隔离验证
        if (!roleDO.getTenantId().equals(BaseContext.getTenantId())) {
            throw new BusinessException("无权限查看此角色");
        }

        // 3. 转换为 VO
        RoleVO roleVO = toVO(roleDO);

        // 4. 加载权限
        List<Long> permissionIds = getRolePermissionIds(roleId);
        if (!permissionIds.isEmpty()) {
            List<PermissionDO> permissionDOs = permissionMapper.selectBatchIds(permissionIds);
            List<RoleVO.PermissionVO> permissionVOs = permissionDOs.stream()
                    .map(pDO -> {
                        RoleVO.PermissionVO pVO = new RoleVO.PermissionVO();
                        pVO.setPermissionId(pDO.getPermissionId());
                        pVO.setPermissionCode(pDO.getPermissionCode());
                        pVO.setPermissionName(pDO.getPermissionName());
                        return pVO;
                    })
                    .collect(Collectors.toList());
            roleVO.setPermissions(permissionVOs);
        }

        return roleVO;
    }

    @Override
    public Page<RoleVO> listRoles(RoleQueryDTO dto) {
        log.info("分页查询角色: current={}, size={}", dto.getCurrent(), dto.getSize());

        // 1. 构建查询条件
        LambdaQueryWrapper<RoleDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RoleDO::getTenantId, BaseContext.getTenantId());

        if (dto.getRoleCode() != null && !dto.getRoleCode().isEmpty()) {
            wrapper.like(RoleDO::getRoleCode, dto.getRoleCode());
        }
        if (dto.getRoleName() != null && !dto.getRoleName().isEmpty()) {
            wrapper.like(RoleDO::getRoleName, dto.getRoleName());
        }
        if (dto.getStatus() != null) {
            wrapper.eq(RoleDO::getStatus, dto.getStatus());
        }

        // 2. 分页查询
        Page<RoleDO> page = new Page<>(dto.getCurrent(), dto.getSize());
        Page<RoleDO> roleDOPage = roleMapper.selectPage(page, wrapper);

        // 3. 转换为 VO
        Page<RoleVO> roleVOPage = new Page<>();
        roleVOPage.setCurrent(roleDOPage.getCurrent());
        roleVOPage.setSize(roleDOPage.getSize());
        roleVOPage.setTotal(roleDOPage.getTotal());

        List<RoleVO> roleVOs = roleDOPage.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        roleVOPage.setRecords(roleVOs);

        return roleVOPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean assignPermissions(AssignPermissionDTO dto) {
        log.info("分配权限: roleId={}, permissionIds={}", dto.getRoleId(), dto.getPermissionIds());

        // 1. 查询角色
        RoleDO roleDO = roleMapper.selectById(dto.getRoleId());
        if (roleDO == null) {
            throw new BusinessException("角色不存在");
        }

        // 2. 租户隔离验证
        if (!roleDO.getTenantId().equals(BaseContext.getTenantId())) {
            throw new BusinessException("无权限操作此角色");
        }

        // 3. 不能修改系统角色
        if (roleDO.getTenantId() == 0) {
            throw new BusinessException("不能修改系统角色的权限");
        }

        // 4. 删除旧权限
        LambdaQueryWrapper<RolePermissionDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RolePermissionDO::getRoleId, dto.getRoleId());
        rolePermissionMapper.delete(wrapper);

        // 5. 分配新权限
        for (Long permissionId : dto.getPermissionIds()) {
            RolePermissionDO rpDO = new RolePermissionDO();
            rpDO.setRoleId(dto.getRoleId());
            rpDO.setPermissionId(permissionId);
            rolePermissionMapper.insert(rpDO);
        }

        log.info("权限分配成功: roleId={}", dto.getRoleId());
        return true;
    }

    @Override
    public List<Long> getRolePermissionIds(Long roleId) {
        return roleMapper.selectPermissionIdsByRoleIds(java.util.Collections.singletonList(roleId));
    }

    /**
     * DO -> VO 转换
     */
    private RoleVO toVO(RoleDO roleDO) {
        RoleVO roleVO = new RoleVO();
        BeanUtils.copyProperties(roleDO, roleVO);
        return roleVO;
    }
}
