package io.lumina.common.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link BaseContext} 上下文测试
 *
 * @author Lumina Team
 * @since 1.0.0
 */
class BaseContextTest {

    @AfterEach
    void clear() {
        BaseContext.clear();
    }

    @Test
    void setAndGetShouldWorkOnSingleThread() {
        BaseContext.setTenantId(100L);
        BaseContext.setUserId(1L);
        BaseContext.setUsername("alice");
        BaseContext.setRoles(new String[]{"TENANT_ADMIN"});
        BaseContext.setPermissions(new String[]{"system:user:list"});

        assertThat(BaseContext.getTenantId()).isEqualTo(100L);
        assertThat(BaseContext.getUserId()).isEqualTo(1L);
        assertThat(BaseContext.getUsername()).isEqualTo("alice");
        assertThat(BaseContext.getRoles()).containsExactly("TENANT_ADMIN");
        assertThat(BaseContext.getPermissions()).containsExactly("system:user:list");
    }

    @Test
    void snapshotShouldAggregateAllFields() {
        BaseContext.setCurrent(new LoginContext(200L, 2L, "bob",
                new String[]{"TENANT_USER"}, new String[]{"agent:execute"}));

        LoginContext snapshot = BaseContext.current();
        assertThat(snapshot.tenantId()).isEqualTo(200L);
        assertThat(snapshot.userId()).isEqualTo(2L);
        assertThat(snapshot.username()).isEqualTo("bob");
        assertThat(snapshot.roles()).containsExactly("TENANT_USER");
        assertThat(snapshot.permissions()).containsExactly("agent:execute");
    }

    @Test
    void initFromHeadersShouldParseAllHeaders() {
        BaseContext.initFromHeaders("1", "alice", "100", "TENANT_ADMIN", "system:user:list,system:role:list");

        assertThat(BaseContext.getUserId()).isEqualTo(1L);
        assertThat(BaseContext.getTenantId()).isEqualTo(100L);
        assertThat(BaseContext.getRoles()).containsExactly("TENANT_ADMIN");
        assertThat(BaseContext.getPermissions()).containsExactly("system:user:list", "system:role:list");
    }

    @Test
    void initFromHeadersShouldHandleNullAndEmpty() {
        BaseContext.initFromHeaders(null, null, null, "", "");

        assertThat(BaseContext.getUserId()).isNull();
        assertThat(BaseContext.getTenantId()).isNull();
        assertThat(BaseContext.getRoles()).isNull();
        assertThat(BaseContext.getPermissions()).isNull();
    }

    @Test
    void isSuperAdminShouldCheckRoles() {
        BaseContext.setRoles(new String[]{"TENANT_USER"});
        assertThat(BaseContext.isSuperAdmin()).isFalse();

        BaseContext.setRoles(new String[]{"SUPER_ADMIN"});
        assertThat(BaseContext.isSuperAdmin()).isTrue();
    }

    @Test
    void hasPermissionShouldSupportWildcard() {
        BaseContext.setRoles(new String[]{"TENANT_USER"});
        BaseContext.setPermissions(new String[]{"system:*"});

        assertThat(BaseContext.hasPermission("system:user:create")).isTrue();
        assertThat(BaseContext.hasPermission("agent:execute")).isFalse();
    }

    @Test
    void superAdminShouldHaveAllPermissions() {
        BaseContext.setRoles(new String[]{"SUPER_ADMIN"});
        // 不设置任何权限
        assertThat(BaseContext.hasPermission("any:permission")).isTrue();
    }

    @Test
    void hasRoleShouldMatchExactly() {
        BaseContext.setRoles(new String[]{"TENANT_ADMIN", "TENANT_USER"});
        assertThat(BaseContext.hasRole("TENANT_ADMIN")).isTrue();
        assertThat(BaseContext.hasRole("SUPER_ADMIN")).isFalse();
    }

    @Test
    void clearShouldRemoveAllFields() {
        BaseContext.setTenantId(1L);
        BaseContext.setUserId(2L);

        BaseContext.clear();

        assertThat(BaseContext.getTenantId()).isNull();
        assertThat(BaseContext.getUserId()).isNull();
        assertThat(BaseContext.current()).isNull();
    }
}
