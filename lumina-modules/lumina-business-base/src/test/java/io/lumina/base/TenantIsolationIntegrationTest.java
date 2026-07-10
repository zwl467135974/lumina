package io.lumina.base;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.lumina.base.api.dto.role.CreateRoleDTO;
import io.lumina.base.api.dto.role.RoleQueryDTO;
import io.lumina.base.api.dto.user.CreateUserDTO;
import io.lumina.base.api.dto.user.UserQueryDTO;
import io.lumina.base.api.vo.role.RoleVO;
import io.lumina.base.api.vo.user.UserVO;
import io.lumina.base.service.RoleService;
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
 * 租户隔离集成测试
 *
 * <p>核心验证点：lumina_user / lumina_role 表含 tenant_id 列，
 * TenantLineHandler 自动检测后会对其注入 tenant_id 条件。
 * 跨租户查询（列表/详情）必须返回空或抛出异常，确保数据不泄露。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>用户：跨租户 getUserById → 抛异常</li>
 *   <li>用户：跨租户 listUsers → 结果不含目标用户</li>
 *   <li>用户：跨租户 getUserByUsername → 抛异常</li>
 *   <li>角色：跨租户 getRoleById → 抛异常</li>
 *   <li>角色：跨租户 listRoles → 结果不含目标角色</li>
 *   <li>角色：租户内 roleCode 唯一，跨租户 roleCode 可重复</li>
 * </ul>
 *
 * @author Lumina Team
 * @since 1.1.0
 */
@Transactional
class TenantIsolationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @BeforeEach
    void setUp() {
        BaseContext.setTenantId(1L);
        BaseContext.setUserId(1L);
    }

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    // ========== 用户租户隔离 ==========

    @Test
    void userGetByIdCrossTenantThrows() {
        CreateUserDTO dto = new CreateUserDTO();
        dto.setUsername("iso_user_byId");
        dto.setPassword("pass");
        Long userId = userService.createUser(dto);

        BaseContext.setTenantId(2L);
        assertThatThrownBy(() -> userService.getUserById(userId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void userListCrossTenantExcludesUser() {
        CreateUserDTO dto = new CreateUserDTO();
        dto.setUsername("iso_user_list_unique");
        dto.setPassword("pass");
        userService.createUser(dto);

        BaseContext.setTenantId(2L);
        UserQueryDTO query = new UserQueryDTO();
        query.setUsername("iso_user_list_unique");

        Page<UserVO> page = userService.listUsers(query);
        assertThat(page.getRecords())
                .noneMatch(u -> "iso_user_list_unique".equals(u.getUsername()));
    }

    @Test
    void userGetByUsernameCrossTenantThrows() {
        CreateUserDTO dto = new CreateUserDTO();
        dto.setUsername("iso_user_byName");
        dto.setPassword("pass");
        userService.createUser(dto);

        BaseContext.setTenantId(2L);
        assertThatThrownBy(() -> userService.getUserByUsername("iso_user_byName"))
                .isInstanceOf(BusinessException.class);
    }

    // ========== 角色租户隔离 ==========

    @Test
    void roleGetByIdCrossTenantThrows() {
        CreateRoleDTO dto = new CreateRoleDTO();
        dto.setRoleCode("iso_role_byId");
        dto.setRoleName("隔离角色");
        Long roleId = roleService.createRole(dto);

        BaseContext.setTenantId(2L);
        assertThatThrownBy(() -> roleService.getRoleById(roleId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void roleListCrossTenantExcludesRole() {
        CreateRoleDTO dto = new CreateRoleDTO();
        dto.setRoleCode("iso_role_list_unique");
        dto.setRoleName("列表隔离角色");
        roleService.createRole(dto);

        BaseContext.setTenantId(2L);
        RoleQueryDTO query = new RoleQueryDTO();
        query.setRoleCode("iso_role_list_unique");

        Page<RoleVO> page = roleService.listRoles(query);
        assertThat(page.getRecords())
                .noneMatch(r -> "iso_role_list_unique".equals(r.getRoleCode()));
    }

    @Test
    void roleCodeDuplicateAllowedAcrossTenants() {
        CreateRoleDTO tenant1 = new CreateRoleDTO();
        tenant1.setRoleCode("iso_role_shared_code");
        tenant1.setRoleName("租户1角色");
        roleService.createRole(tenant1);

        BaseContext.setTenantId(2L);
        CreateRoleDTO tenant2 = new CreateRoleDTO();
        tenant2.setRoleCode("iso_role_shared_code");
        tenant2.setRoleName("租户2角色");

        Long roleId = roleService.createRole(tenant2);
        assertThat(roleId).isNotNull().isPositive();

        RoleVO role = roleService.getRoleById(roleId);
        assertThat(role.getTenantId()).isEqualTo(2L);
    }

    @Test
    void roleCodeDuplicateWithinSameTenantThrows() {
        CreateRoleDTO dto = new CreateRoleDTO();
        dto.setRoleCode("iso_role_dup_same");
        dto.setRoleName("角色1");
        roleService.createRole(dto);

        CreateRoleDTO dup = new CreateRoleDTO();
        dup.setRoleCode("iso_role_dup_same");
        dup.setRoleName("角色2");

        assertThatThrownBy(() -> roleService.createRole(dup))
                .isInstanceOf(BusinessException.class);
    }
}
