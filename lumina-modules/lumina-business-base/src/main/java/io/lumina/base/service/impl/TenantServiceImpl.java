package io.lumina.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.lumina.base.api.dto.tenant.CreateTenantDTO;
import io.lumina.base.api.dto.tenant.TenantQueryDTO;
import io.lumina.base.api.dto.tenant.UpdateTenantDTO;
import io.lumina.base.api.vo.tenant.TenantVO;
import io.lumina.base.infrastructure.entity.PermissionDO;
import io.lumina.base.infrastructure.entity.RoleDO;
import io.lumina.base.infrastructure.entity.RolePermissionDO;
import io.lumina.base.infrastructure.entity.TenantDO;
import io.lumina.base.infrastructure.entity.UserDO;
import io.lumina.base.infrastructure.mapper.PermissionMapper;
import io.lumina.base.infrastructure.mapper.RoleMapper;
import io.lumina.base.infrastructure.mapper.RolePermissionMapper;
import io.lumina.base.infrastructure.mapper.TenantMapper;
import io.lumina.base.infrastructure.mapper.UserMapper;
import io.lumina.base.service.TenantService;
import io.lumina.common.core.BaseContext;
import io.lumina.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 租户服务实现
 */
@Slf4j
@Service
public class TenantServiceImpl implements TenantService {

    @Autowired
    private TenantMapper tenantMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private RolePermissionMapper rolePermissionMapper;

    @Autowired
    private PermissionMapper permissionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTenant(CreateTenantDTO dto) {
        log.info("创建租户: tenantCode={}", dto.getTenantCode());

        // 验证租户编码唯一性
        LambdaQueryWrapper<TenantDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TenantDO::getTenantCode, dto.getTenantCode());
        if (tenantMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("租户编码已存在");
        }

        TenantDO tenantDO = new TenantDO();
        BeanUtils.copyProperties(dto, tenantDO);
        tenantDO.setStatus(1);

        tenantMapper.insert(tenantDO);

        // 创建租户默认角色
        createDefaultRolesForTenant(tenantDO.getTenantId());

        log.info("租户创建成功: tenantId={}", tenantDO.getTenantId());
        return tenantDO.getTenantId();
    }

    /**
     * 为租户创建默认角色
     *
     * @param tenantId 租户 ID
     */
    private void createDefaultRolesForTenant(Long tenantId) {
        log.info("为租户创建默认角色: tenantId={}", tenantId);

        // 创建租户管理员角色
        RoleDO adminRole = new RoleDO();
        adminRole.setTenantId(tenantId);
        adminRole.setRoleCode("TENANT_ADMIN");
        adminRole.setRoleName("租户管理员");
        adminRole.setDescription("租户管理员，拥有租户内所有权限");
        adminRole.setSortOrder(1);
        adminRole.setStatus(1);
        roleMapper.insert(adminRole);

        // 创建租户普通用户角色
        RoleDO userRole = new RoleDO();
        userRole.setTenantId(tenantId);
        userRole.setRoleCode("TENANT_USER");
        userRole.setRoleName("租户用户");
        userRole.setDescription("租户普通用户");
        userRole.setSortOrder(2);
        userRole.setStatus(1);
        roleMapper.insert(userRole);

        // 获取所有权限（为管理员角色分配所有权限）
        List<PermissionDO> allPermissions = permissionMapper.selectAllPermissions();
        if (!allPermissions.isEmpty()) {
            List<Long> permissionIds = allPermissions.stream()
                    .map(PermissionDO::getPermissionId)
                    .collect(Collectors.toList());

            // 为管理员角色分配所有权限
            for (Long permissionId : permissionIds) {
                RolePermissionDO rp = new RolePermissionDO();
                rp.setRoleId(adminRole.getRoleId());
                rp.setPermissionId(permissionId);
                rolePermissionMapper.insert(rp);
            }
            log.info("为租户管理员角色分配权限: roleId={}, permissionCount={}",
                    adminRole.getRoleId(), permissionIds.size());
        }

        log.info("租户默认角色创建成功: tenantId={}, adminRoleId={}, userRoleId={}",
                tenantId, adminRole.getRoleId(), userRole.getRoleId());
    }

    @Override
    public Boolean updateTenant(UpdateTenantDTO dto) {
        TenantDO tenantDO = tenantMapper.selectById(dto.getTenantId());
        if (tenantDO == null) {
            throw new BusinessException("租户不存在");
        }

        BeanUtils.copyProperties(dto, tenantDO, "tenantId");
        return tenantMapper.updateById(tenantDO) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteTenant(Long tenantId) {
        // 检查是否有用户
        LambdaQueryWrapper<UserDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDO::getTenantId, tenantId);
        if (userMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("租户下有用户，无法删除");
        }

        TenantDO tenantDO = tenantMapper.selectById(tenantId);
        tenantDO.setDeleted(1);
        return tenantMapper.updateById(tenantDO) > 0;
    }

    @Override
    public TenantVO getTenantById(Long tenantId) {
        TenantDO tenantDO = tenantMapper.selectById(tenantId);
        if (tenantDO == null) {
            throw new BusinessException("租户不存在");
        }

        TenantVO vo = new TenantVO();
        BeanUtils.copyProperties(tenantDO, vo);
        return vo;
    }

    @Override
    public Page<TenantVO> listTenants(TenantQueryDTO dto) {
        LambdaQueryWrapper<TenantDO> wrapper = new LambdaQueryWrapper<>();

        if (dto.getTenantCode() != null) {
            wrapper.like(TenantDO::getTenantCode, dto.getTenantCode());
        }
        if (dto.getTenantName() != null) {
            wrapper.like(TenantDO::getTenantName, dto.getTenantName());
        }
        if (dto.getStatus() != null) {
            wrapper.eq(TenantDO::getStatus, dto.getStatus());
        }

        Page<TenantDO> page = new Page<>(dto.getCurrent(), dto.getSize());
        Page<TenantDO> result = tenantMapper.selectPage(page, wrapper);

        Page<TenantVO> voPage = new Page<>();
        voPage.setCurrent(result.getCurrent());
        voPage.setSize(result.getSize());
        voPage.setTotal(result.getTotal());

        List<TenantVO> vos = result.getRecords().stream()
                .map(t -> {
                    TenantVO vo = new TenantVO();
                    BeanUtils.copyProperties(t, vo);
                    return vo;
                })
                .collect(Collectors.toList());
        voPage.setRecords(vos);

        return voPage;
    }
}
