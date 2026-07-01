package io.lumina.base.service.impl;

import io.lumina.base.api.dto.role.CreateRoleDTO;
import io.lumina.base.api.dto.role.UpdateRoleDTO;
import io.lumina.base.infrastructure.entity.RoleDO;
import io.lumina.base.infrastructure.mapper.PermissionMapper;
import io.lumina.base.infrastructure.mapper.RoleMapper;
import io.lumina.base.infrastructure.mapper.RolePermissionMapper;
import io.lumina.base.infrastructure.mapper.UserRoleMapper;
import io.lumina.common.core.BaseContext;
import io.lumina.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * RoleServiceImpl 单元测试
 *
 * <p>覆盖租户隔离、系统角色保护、角色使用校验。
 *
 * @author Lumina Team
 * @since 1.1.0
 */
@ExtendWith(MockitoExtension.class)
class RoleServiceImplTest {

    @InjectMocks
    private RoleServiceImpl roleService;

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private RolePermissionMapper rolePermissionMapper;

    @Mock
    private UserRoleMapper userRoleMapper;

    @Mock
    private PermissionMapper permissionMapper;

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    @Test
    void createRoleSuccess() {
        BaseContext.setTenantId(1L);
        CreateRoleDTO dto = new CreateRoleDTO();
        dto.setRoleCode("EDITOR");
        dto.setRoleName("编辑");

        when(roleMapper.selectOne(any())).thenReturn(null);
        when(roleMapper.insert(any(RoleDO.class))).thenAnswer(inv -> {
            ((RoleDO) inv.getArgument(0)).setRoleId(50L);
            return 1;
        });

        Long roleId = roleService.createRole(dto);
        assertThat(roleId).isEqualTo(50L);
    }

    @Test
    void createRoleDuplicateThrows() {
        BaseContext.setTenantId(1L);
        CreateRoleDTO dto = new CreateRoleDTO();
        dto.setRoleCode("DUP");

        when(roleMapper.selectOne(any())).thenReturn(new RoleDO());

        assertThatThrownBy(() -> roleService.createRole(dto))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void updateRoleCrossTenantThrows() {
        BaseContext.setTenantId(1L);
        UpdateRoleDTO dto = new UpdateRoleDTO();
        dto.setRoleId(10L);

        RoleDO role = new RoleDO();
        role.setTenantId(2L);
        when(roleMapper.selectById(10L)).thenReturn(role);

        assertThatThrownBy(() -> roleService.updateRole(dto))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void updateSystemRoleThrows() {
        BaseContext.setTenantId(0L);
        UpdateRoleDTO dto = new UpdateRoleDTO();
        dto.setRoleId(1L);

        RoleDO role = new RoleDO();
        role.setTenantId(0L);
        when(roleMapper.selectById(1L)).thenReturn(role);

        assertThatThrownBy(() -> roleService.updateRole(dto))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deleteRoleInUseThrows() {
        BaseContext.setTenantId(1L);

        RoleDO role = new RoleDO();
        role.setTenantId(1L);
        when(roleMapper.selectById(10L)).thenReturn(role);
        when(userRoleMapper.selectCount(any())).thenReturn(3L);

        assertThatThrownBy(() -> roleService.deleteRole(10L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deleteSystemRoleThrows() {
        BaseContext.setTenantId(0L);

        RoleDO role = new RoleDO();
        role.setTenantId(0L);
        when(roleMapper.selectById(1L)).thenReturn(role);

        assertThatThrownBy(() -> roleService.deleteRole(1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void getRoleByIdNotFoundThrows() {
        when(roleMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> roleService.getRoleById(99L))
                .isInstanceOf(BusinessException.class);
    }
}
