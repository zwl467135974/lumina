package io.lumina.base.service.impl;

import io.lumina.base.api.dto.tenant.CreateTenantDTO;
import io.lumina.base.api.dto.tenant.UpdateTenantDTO;
import io.lumina.base.infrastructure.entity.TenantDO;
import io.lumina.base.infrastructure.mapper.PermissionMapper;
import io.lumina.base.infrastructure.mapper.RoleMapper;
import io.lumina.base.infrastructure.mapper.RolePermissionMapper;
import io.lumina.base.infrastructure.mapper.TenantMapper;
import io.lumina.base.infrastructure.mapper.UserMapper;
import io.lumina.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * TenantServiceImpl 单元测试
 *
 * <p>覆盖租户编码唯一性、删除前置校验（有用户禁止删除）。
 *
 * @author Lumina Team
 * @since 1.1.0
 */
@ExtendWith(MockitoExtension.class)
class TenantServiceImplTest {

    @InjectMocks
    private TenantServiceImpl tenantService;

    @Mock
    private TenantMapper tenantMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private RolePermissionMapper rolePermissionMapper;

    @Mock
    private PermissionMapper permissionMapper;

    @Test
    void createTenantSuccess() {
        CreateTenantDTO dto = new CreateTenantDTO();
        dto.setTenantCode("T001");
        dto.setTenantName("测试租户");

        when(tenantMapper.selectCount(any())).thenReturn(0L);
        when(tenantMapper.insert(any(TenantDO.class))).thenAnswer(inv -> {
            ((TenantDO) inv.getArgument(0)).setTenantId(20L);
            return 1;
        });
        when(roleMapper.insert(any(io.lumina.base.infrastructure.entity.RoleDO.class))).thenReturn(1);
        when(permissionMapper.selectAllPermissions()).thenReturn(Collections.emptyList());

        Long tenantId = tenantService.createTenant(dto);
        assertThat(tenantId).isEqualTo(20L);
    }

    @Test
    void createTenantDuplicateThrows() {
        CreateTenantDTO dto = new CreateTenantDTO();
        dto.setTenantCode("DUP");

        when(tenantMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> tenantService.createTenant(dto))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void updateTenantNotFoundThrows() {
        UpdateTenantDTO dto = new UpdateTenantDTO();
        dto.setTenantId(99L);

        when(tenantMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> tenantService.updateTenant(dto))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deleteTenantInUseThrows() {
        when(userMapper.selectCount(any())).thenReturn(5L);

        assertThatThrownBy(() -> tenantService.deleteTenant(10L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void getTenantByIdNotFoundThrows() {
        when(tenantMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> tenantService.getTenantById(99L))
                .isInstanceOf(BusinessException.class);
    }
}
