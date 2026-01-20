package io.lumina.common.core;

import lombok.extern.slf4j.Slf4j;

/**
 * Base 上下文工具类
 *
 * 使用 ThreadLocal 存储当前请求的用户上下文信息（租户 ID、用户 ID、角色、权限）
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
public class BaseContext {

    /**
     * 租户 ID
     */
    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();

    /**
     * 用户 ID
     */
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    /**
     * 用户名
     */
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();

    /**
     * 角色列表
     */
    private static final ThreadLocal<String[]> ROLES = new ThreadLocal<>();

    /**
     * 权限列表
     */
    private static final ThreadLocal<String[]> PERMISSIONS = new ThreadLocal<>();

    /**
     * 设置租户 ID
     */
    public static void setTenantId(Long tenantId) {
        TENANT_ID.set(tenantId);
        log.debug("设置租户 ID: {}", tenantId);
    }

    /**
     * 获取租户 ID
     */
    public static Long getTenantId() {
        return TENANT_ID.get();
    }

    /**
     * 设置用户 ID
     */
    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    /**
     * 获取用户 ID
     */
    public static Long getUserId() {
        return USER_ID.get();
    }

    /**
     * 设置用户名
     */
    public static void setUsername(String username) {
        USERNAME.set(username);
    }

    /**
     * 获取用户名
     */
    public static String getUsername() {
        return USERNAME.get();
    }

    /**
     * 设置角色列表
     */
    public static void setRoles(String[] roles) {
        ROLES.set(roles);
    }

    /**
     * 获取角色列表
     */
    public static String[] getRoles() {
        return ROLES.get();
    }

    /**
     * 设置权限列表
     */
    public static void setPermissions(String[] permissions) {
        PERMISSIONS.set(permissions);
    }

    /**
     * 获取权限列表
     */
    public static String[] getPermissions() {
        return PERMISSIONS.get();
    }

    /**
     * 判断是否是超级管理员
     */
    public static boolean isSuperAdmin() {
        String[] roles = ROLES.get();
        if (roles == null) {
            return false;
        }
        for (String role : roles) {
            if ("SUPER_ADMIN".equals(role)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否是租户管理员
     */
    public static boolean isTenantAdmin() {
        String[] roles = ROLES.get();
        if (roles == null) {
            return false;
        }
        for (String role : roles) {
            if ("TENANT_ADMIN".equals(role)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否拥有指定权限
     */
    public static boolean hasPermission(String permission) {
        // 超级管理员拥有所有权限
        if (isSuperAdmin()) {
            return true;
        }

        String[] permissions = PERMISSIONS.get();
        if (permissions == null) {
            return false;
        }

        for (String perm : permissions) {
            if (perm.equals(permission)) {
                return true;
            }
            // 支持通配符匹配（例如：system:* 匹配 system:user、system:role 等）
            if (perm.endsWith("*")) {
                String prefix = perm.substring(0, perm.length() - 1);
                if (permission.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 判断是否拥有指定角色
     */
    public static boolean hasRole(String role) {
        String[] roles = ROLES.get();
        if (roles == null) {
            return false;
        }
        for (String r : roles) {
            if (r.equals(role)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 清除上下文信息
     */
    public static void clear() {
        TENANT_ID.remove();
        USER_ID.remove();
        USERNAME.remove();
        ROLES.remove();
        PERMISSIONS.remove();
        log.debug("清除 Base 上下文");
    }

    /**
     * 从 HttpServletRequest Header 中提取并设置上下文信息
     *
     * @param userIdHeader    用户 ID Header
     * @param usernameHeader  用户名 Header
     * @param tenantIdHeader  租户 ID Header
     * @param rolesHeader     角色列表 Header
     * @param permissionsHeader 权限列表 Header
     */
    public static void initFromHeaders(String userIdHeader, String usernameHeader,
                                      String tenantIdHeader, String rolesHeader,
                                      String permissionsHeader) {
        try {
            if (userIdHeader != null) {
                setUserId(Long.valueOf(userIdHeader));
            }
            if (usernameHeader != null) {
                setUsername(usernameHeader);
            }
            if (tenantIdHeader != null) {
                setTenantId(Long.valueOf(tenantIdHeader));
            }
            if (rolesHeader != null && !rolesHeader.isEmpty()) {
                setRoles(rolesHeader.split(","));
            }
            if (permissionsHeader != null && !permissionsHeader.isEmpty()) {
                setPermissions(permissionsHeader.split(","));
            }
            log.debug("BaseContext 初始化完成: userId={}, username={}, tenantId={}, roles={}",
                    getUserId(), getUsername(), getTenantId(), getRoles());
        } catch (Exception e) {
            log.error("BaseContext 初始化失败", e);
        }
    }
}
