package io.lumina.base.service.impl;

import io.lumina.base.api.dto.LoginDTO;
import io.lumina.base.api.vo.LoginVO;
import io.lumina.base.infrastructure.entity.RoleDO;
import io.lumina.base.infrastructure.entity.UserDO;
import io.lumina.base.infrastructure.mapper.PermissionMapper;
import io.lumina.base.infrastructure.mapper.RoleMapper;
import io.lumina.base.infrastructure.mapper.UserMapper;
import io.lumina.common.exception.BusinessException;
import io.lumina.common.util.JwtUtil;
import io.lumina.common.util.PasswordUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * AuthServiceImpl 单元测试
 *
 * <p>覆盖登录认证链：用户查询、密码校验、状态检查。
 *
 * @author Lumina Team
 * @since 1.1.0
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @InjectMocks
    private AuthServiceImpl authService;

    @Mock
    private UserMapper userMapper;

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private PermissionMapper permissionMapper;

    @Mock
    private JwtUtil jwtUtil;

    @Test
    void loginSuccess() {
        LoginDTO dto = new LoginDTO();
        dto.setTenantId(1L);
        dto.setUsername("testuser");
        dto.setPassword("pass123");

        UserDO user = new UserDO();
        user.setUserId(10L);
        user.setUsername("testuser");
        user.setTenantId(1L);
        user.setPassword(PasswordUtil.hash("pass123"));
        user.setStatus(1);
        when(userMapper.selectByTenantIdAndUsername(1L, "testuser")).thenReturn(user);

        RoleDO role = new RoleDO();
        role.setRoleId(1L);
        role.setRoleCode("USER");
        role.setRoleName("用户");
        role.setStatus(1);
        when(roleMapper.selectRolesByUserId(10L)).thenReturn(List.of(role));
        when(roleMapper.selectPermissionIdsByRoleIds(anyList())).thenReturn(Collections.emptyList());
        when(jwtUtil.generateToken(eq("testuser"), anyMap())).thenReturn("mock-token");

        LoginVO vo = authService.login(dto);

        assertThat(vo.getToken()).isEqualTo("mock-token");
        assertThat(vo.getUsername()).isEqualTo("testuser");
        assertThat(vo.getUserId()).isEqualTo(10L);
        assertThat(vo.getRoles()).contains("USER");
    }

    @Test
    void loginUserNotFoundThrows() {
        LoginDTO dto = new LoginDTO();
        dto.setTenantId(1L);
        dto.setUsername("ghost");
        dto.setPassword("x");

        when(userMapper.selectByTenantIdAndUsername(1L, "ghost")).thenReturn(null);

        assertThatThrownBy(() -> authService.login(dto))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void loginWrongPasswordThrows() {
        LoginDTO dto = new LoginDTO();
        dto.setTenantId(1L);
        dto.setUsername("testuser");
        dto.setPassword("wrong");

        UserDO user = new UserDO();
        user.setUserId(10L);
        user.setUsername("testuser");
        user.setPassword(PasswordUtil.hash("correct"));
        user.setStatus(1);
        when(userMapper.selectByTenantIdAndUsername(1L, "testuser")).thenReturn(user);

        assertThatThrownBy(() -> authService.login(dto))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void loginUserDisabledThrows() {
        LoginDTO dto = new LoginDTO();
        dto.setTenantId(1L);
        dto.setUsername("testuser");
        dto.setPassword("pass");

        UserDO user = new UserDO();
        user.setUserId(10L);
        user.setUsername("testuser");
        user.setPassword(PasswordUtil.hash("pass"));
        user.setStatus(0);
        when(userMapper.selectByTenantIdAndUsername(1L, "testuser")).thenReturn(user);

        assertThatThrownBy(() -> authService.login(dto))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void loginDefaultTenantWhenNull() {
        LoginDTO dto = new LoginDTO();
        dto.setTenantId(null);
        dto.setUsername("admin");
        dto.setPassword("pass");

        UserDO user = new UserDO();
        user.setUserId(1L);
        user.setUsername("admin");
        user.setTenantId(0L);
        user.setPassword(PasswordUtil.hash("pass"));
        user.setStatus(1);

        // tenantId 为 null 时默认查租户 0
        when(userMapper.selectByTenantIdAndUsername(0L, "admin")).thenReturn(user);

        RoleDO role = new RoleDO();
        role.setRoleId(1L);
        role.setRoleCode("ADMIN");
        role.setRoleName("管理员");
        role.setStatus(1);
        when(roleMapper.selectRolesByUserId(1L)).thenReturn(List.of(role));
        when(roleMapper.selectPermissionIdsByRoleIds(anyList())).thenReturn(Collections.emptyList());
        when(jwtUtil.generateToken(anyString(), anyMap())).thenReturn("token");

        LoginVO vo = authService.login(dto);
        assertThat(vo.getTenantId()).isEqualTo(0L);
    }
}
