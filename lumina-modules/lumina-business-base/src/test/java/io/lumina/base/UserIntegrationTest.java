package io.lumina.base;

import io.lumina.base.api.dto.user.CreateUserDTO;
import io.lumina.base.api.dto.user.ResetPasswordDTO;
import io.lumina.base.api.vo.user.UserVO;
import io.lumina.base.service.UserService;
import io.lumina.common.core.BaseContext;
import io.lumina.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 用户服务集成测试（Testcontainers MySQL）
 *
 * <p>验证真实 SQL 执行 + 租户拦截器自动注入 + Flyway 建表。
 * 每个测试方法事务回滚，数据互不影响。
 *
 * @author Lumina Team
 * @since 1.1.0
 */
@Transactional
class UserIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserService userService;

    @BeforeEach
    void setUp() {
        BaseContext.setTenantId(1L);
        BaseContext.setUserId(1L);
    }

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    @Test
    void createUserAndQueryById() {
        CreateUserDTO dto = new CreateUserDTO();
        dto.setUsername("itest_create");
        dto.setPassword("pass123");
        dto.setNickname("测试用户");

        Long userId = userService.createUser(dto);
        assertThat(userId).isNotNull().isPositive();

        UserVO user = userService.getUserById(userId);
        assertThat(user.getUsername()).isEqualTo("itest_create");
        assertThat(user.getNickname()).isEqualTo("测试用户");
    }

    @Test
    void duplicateUsernameThrows() {
        CreateUserDTO dto = new CreateUserDTO();
        dto.setUsername("itest_dup");
        dto.setPassword("pass");
        userService.createUser(dto);

        CreateUserDTO dup = new CreateUserDTO();
        dup.setUsername("itest_dup");
        dup.setPassword("pass");

        assertThatThrownBy(() -> userService.createUser(dup))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void tenantIsolationCrossTenantHidden() {
        CreateUserDTO dto = new CreateUserDTO();
        dto.setUsername("tenant1_user");
        dto.setPassword("pass");
        Long userId = userService.createUser(dto);

        // 切换到租户 2，租户拦截器自动注入 tenant_id=2，查不到租户 1 的用户
        BaseContext.setTenantId(2L);
        assertThatThrownBy(() -> userService.getUserById(userId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deleteUserSuccess() {
        CreateUserDTO dto = new CreateUserDTO();
        dto.setUsername("itest_delete");
        dto.setPassword("pass");
        Long userId = userService.createUser(dto);

        Boolean result = userService.deleteUser(userId);
        assertThat(result).isTrue();

        assertThatThrownBy(() -> userService.getUserById(userId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void resetPasswordSuccess() {
        CreateUserDTO dto = new CreateUserDTO();
        dto.setUsername("itest_reset");
        dto.setPassword("oldpass");
        Long userId = userService.createUser(dto);

        ResetPasswordDTO reset = new ResetPasswordDTO();
        reset.setUserId(userId);
        reset.setNewPassword("newpass123");
        reset.setConfirmPassword("newpass123");

        Boolean result = userService.resetPassword(reset);
        assertThat(result).isTrue();
    }

    @Test
    void getUserByUsernameTenantScoped() {
        CreateUserDTO dto = new CreateUserDTO();
        dto.setUsername("itest_byname");
        dto.setPassword("pass");
        userService.createUser(dto);

        UserVO user = userService.getUserByUsername("itest_byname");
        assertThat(user.getUsername()).isEqualTo("itest_byname");

        // 租户 2 查不到
        BaseContext.setTenantId(2L);
        assertThatThrownBy(() -> userService.getUserByUsername("itest_byname"))
                .isInstanceOf(BusinessException.class);
    }
}
