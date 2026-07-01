package io.lumina.base.service.impl;

import io.lumina.base.api.dto.user.AssignRoleDTO;
import io.lumina.base.api.dto.user.CreateUserDTO;
import io.lumina.base.api.dto.user.ResetPasswordDTO;
import io.lumina.base.api.dto.user.UpdateUserDTO;
import io.lumina.base.infrastructure.entity.RoleDO;
import io.lumina.base.infrastructure.entity.UserDO;
import io.lumina.base.infrastructure.mapper.RoleMapper;
import io.lumina.base.infrastructure.mapper.UserMapper;
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
 * UserServiceImpl 单元测试
 *
 * <p>覆盖租户隔离、admin 保护、用户名唯一性等核心业务校验。
 *
 * @author Lumina Team
 * @since 1.1.0
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @InjectMocks
    private UserServiceImpl userService;

    @Mock
    private UserMapper userMapper;

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private UserRoleMapper userRoleMapper;

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    @Test
    void createUserSuccess() {
        BaseContext.setTenantId(1L);
        CreateUserDTO dto = new CreateUserDTO();
        dto.setUsername("newuser");
        dto.setPassword("pass123");

        when(userMapper.selectByTenantIdAndUsername(1L, "newuser")).thenReturn(null);
        when(userMapper.insert(any(UserDO.class))).thenAnswer(inv -> {
            ((UserDO) inv.getArgument(0)).setUserId(100L);
            return 1;
        });

        Long userId = userService.createUser(dto);
        assertThat(userId).isEqualTo(100L);
    }

    @Test
    void createUserDuplicateThrows() {
        BaseContext.setTenantId(1L);
        CreateUserDTO dto = new CreateUserDTO();
        dto.setUsername("dup");

        when(userMapper.selectByTenantIdAndUsername(1L, "dup")).thenReturn(new UserDO());

        assertThatThrownBy(() -> userService.createUser(dto))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void updateUserCrossTenantThrows() {
        BaseContext.setTenantId(1L);
        UpdateUserDTO dto = new UpdateUserDTO();
        dto.setUserId(10L);

        UserDO user = new UserDO();
        user.setTenantId(2L);
        when(userMapper.selectById(10L)).thenReturn(user);

        assertThatThrownBy(() -> userService.updateUser(dto))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deleteSystemAdminThrows() {
        BaseContext.setTenantId(0L);

        UserDO admin = new UserDO();
        admin.setTenantId(0L);
        admin.setUsername("admin");
        when(userMapper.selectById(1L)).thenReturn(admin);

        assertThatThrownBy(() -> userService.deleteUser(1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deleteUserCrossTenantThrows() {
        BaseContext.setTenantId(1L);

        UserDO user = new UserDO();
        user.setTenantId(2L);
        when(userMapper.selectById(5L)).thenReturn(user);

        assertThatThrownBy(() -> userService.deleteUser(5L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void resetPasswordMismatchThrows() {
        ResetPasswordDTO dto = new ResetPasswordDTO();
        dto.setNewPassword("a");
        dto.setConfirmPassword("b");

        assertThatThrownBy(() -> userService.resetPassword(dto))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void assignRolesCrossTenantRoleThrows() {
        BaseContext.setTenantId(1L);
        AssignRoleDTO dto = new AssignRoleDTO();
        dto.setUserId(10L);
        dto.setRoleIds(java.util.List.of(5L));

        UserDO user = new UserDO();
        user.setTenantId(1L);
        user.setUsername("normal");
        when(userMapper.selectById(10L)).thenReturn(user);

        RoleDO role = new RoleDO();
        role.setTenantId(2L);
        when(roleMapper.selectById(5L)).thenReturn(role);

        assertThatThrownBy(() -> userService.assignRoles(dto))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void getUserByIdNotFoundThrows() {
        when(userMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> userService.getUserById(99L))
                .isInstanceOf(BusinessException.class);
    }
}
