package io.lumina.base;

import io.lumina.base.api.dto.permission.CreatePermissionDTO;
import io.lumina.base.api.vo.permission.PermissionVO;
import io.lumina.base.service.PermissionService;
import io.lumina.common.core.BaseContext;
import io.lumina.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 权限服务集成测试
 *
 * <p>核心验证点：
 * <ul>
 *   <li>lumina_permission 为全局表（无 tenant_id），不受租户拦截器影响</li>
 *   <li>权限 CRUD 正常工作，不产生 SQL 异常</li>
 *   <li>权限数据跨租户共享</li>
 *   <li>权限树构建正确</li>
 * </ul>
 *
 * <p>注：BaseIntegrationTest 使用 webEnvironment=NONE，无法直接测试 Controller 层
 * 的 @RequirePermission 注解拦截。此处通过 PermissionService + BaseContext
 * 验证权限数据正确性及 BaseContext.hasPermission() 判定逻辑。
 *
 * @author Lumina Team
 * @since 1.1.0
 */
@Transactional
class PermissionIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private PermissionService permissionService;

    @BeforeEach
    void setUp() {
        BaseContext.setTenantId(1L);
        BaseContext.setUserId(1L);
    }

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    // ========== 权限 CRUD ==========

    @Test
    void createPermissionSuccess() {
        Long id = createTestPermission("itest:perm:create", "测试创建权限");

        assertThat(id).isNotNull().isPositive();

        PermissionVO vo = permissionService.getPermissionById(id);
        assertThat(vo.getPermissionCode()).isEqualTo("itest:perm:create");
        assertThat(vo.getPermissionName()).isEqualTo("测试创建权限");
        assertThat(vo.getStatus()).isEqualTo(1);
    }

    @Test
    void listAllPermissionsIncludesSeed() {
        List<PermissionVO> all = permissionService.listAllPermissions();
        assertThat(all).isNotEmpty();
    }

    @Test
    void duplicatePermissionCodeThrows() {
        createTestPermission("itest:perm:dup", "重复权限");

        CreatePermissionDTO dup = buildPermissionDTO("itest:perm:dup", "重复权限2");
        assertThatThrownBy(() -> permissionService.createPermission(dup))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void updatePermissionSuccess() {
        Long id = createTestPermission("itest:perm:update", "原始名称");

        io.lumina.base.api.dto.permission.UpdatePermissionDTO update =
                new io.lumina.base.api.dto.permission.UpdatePermissionDTO();
        update.setPermissionId(id);
        update.setPermissionName("更新名称");

        Boolean result = permissionService.updatePermission(update);
        assertThat(result).isTrue();

        PermissionVO vo = permissionService.getPermissionById(id);
        assertThat(vo.getPermissionName()).isEqualTo("更新名称");
    }

    @Test
    void deletePermissionSuccess() {
        Long id = createTestPermission("itest:perm:delete", "待删除");

        Boolean result = permissionService.deletePermission(id);
        assertThat(result).isTrue();

        assertThatThrownBy(() -> permissionService.getPermissionById(id))
                .isInstanceOf(BusinessException.class);
    }

    // ========== 权限树 ==========

    @Test
    void getPermissionTreeReturnsHierarchy() {
        Long parentId = createTestPermission("itest:tree:root", "树根");

        CreatePermissionDTO child = buildPermissionDTO("itest:tree:child", "子节点");
        child.setParentId(parentId);
        permissionService.createPermission(child);

        List<PermissionVO> tree = permissionService.getPermissionTree();
        PermissionVO root = tree.stream()
                .filter(n -> "itest:tree:root".equals(n.getPermissionCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("树根节点未找到"));

        assertThat(root.getChildren()).isNotEmpty();
        assertThat(root.getChildren()).extracting(PermissionVO::getPermissionCode)
                .contains("itest:tree:child");
    }

    @Test
    void listByTypeReturnsFilteredResults() {
        createTestPermission("itest:type:menu", "菜单类型");

        List<PermissionVO> buttons = permissionService.listByType(2);
        assertThat(buttons).allMatch(p -> p.getPermissionType() == 2);

        List<PermissionVO> menus = permissionService.listByType(1);
        assertThat(menus).allMatch(p -> p.getPermissionType() == 1);
    }

    // ========== 跨租户共享 ==========

    @Test
    void permissionSharedAcrossTenants() {
        BaseContext.setTenantId(1L);
        Long id = createTestPermission("itest:perm:shared", "跨租户权限");
        List<PermissionVO> tenant1List = permissionService.listAllPermissions();

        BaseContext.setTenantId(2L);
        List<PermissionVO> tenant2List = permissionService.listAllPermissions();

        assertThat(tenant1List).isEqualTo(tenant2List);
        assertThat(tenant2List).extracting(PermissionVO::getPermissionCode)
                .contains("itest:perm:shared");

        PermissionVO vo = permissionService.getPermissionById(id);
        assertThat(vo.getPermissionCode()).isEqualTo("itest:perm:shared");
    }

    // ========== BaseContext 权限判定逻辑 ==========

    @Test
    void baseContextHasPermissionExactMatch() {
        BaseContext.setPermissions(new String[]{"system:user:list"});

        assertThat(BaseContext.hasPermission("system:user:list")).isTrue();
        assertThat(BaseContext.hasPermission("system:user:create")).isFalse();
    }

    @Test
    void baseContextSuperAdminBypassesCheck() {
        BaseContext.setRoles(new String[]{"SUPER_ADMIN"});
        BaseContext.setPermissions(null);

        assertThat(BaseContext.isSuperAdmin()).isTrue();
        assertThat(BaseContext.hasPermission("any:permission:code")).isTrue();
    }

    @Test
    void baseContextWildcardPermissionMatch() {
        BaseContext.setPermissions(new String[]{"system:*"});

        assertThat(BaseContext.hasPermission("system:user")).isTrue();
        assertThat(BaseContext.hasPermission("system:role")).isTrue();
        assertThat(BaseContext.hasPermission("agent:list")).isFalse();
    }

    @Test
    void baseContextNoPermissionWhenContextEmpty() {
        assertThat(BaseContext.hasPermission("any:permission")).isFalse();
        assertThat(BaseContext.isSuperAdmin()).isFalse();
    }

    @Test
    void baseContextTenantAdminRoleCheck() {
        BaseContext.setRoles(new String[]{"TENANT_ADMIN"});

        assertThat(BaseContext.isTenantAdmin()).isTrue();
        assertThat(BaseContext.isSuperAdmin()).isFalse();
    }

    // ========== 辅助方法 ==========

    private Long createTestPermission(String code, String name) {
        CreatePermissionDTO dto = buildPermissionDTO(code, name);
        return permissionService.createPermission(dto);
    }

    private CreatePermissionDTO buildPermissionDTO(String code, String name) {
        CreatePermissionDTO dto = new CreatePermissionDTO();
        dto.setParentId(0L);
        dto.setPermissionCode(code);
        dto.setPermissionName(name);
        dto.setPermissionType(2);
        return dto;
    }
}
