package io.lumina.common.core;

import lombok.extern.slf4j.Slf4j;

/**
 * Base 上下文工具类
 *
 * <p>使用单个 {@link ThreadLocal}&lt;{@link LoginContext}&gt; 快照存储当前请求的用户上下文信息
 * （租户 ID、用户 ID、用户名、角色、权限）。
 *
 * <p>通过 {@link #current()} 与 {@link #setCurrent(LoginContext)} 暴露快照级读写，
 * 配合 Micrometer Context Propagation 实现跨线程（Reactor subscribeOn / boundedElastic）的自动传播，
 * 解决 Agent 执行引擎与工具调用在线程切换时上下文丢失、租户隔离失效的问题。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
public class BaseContext {

    /**
     * 当前请求的登录上下文快照
     */
    private static final ThreadLocal<LoginContext> CONTEXT = new ThreadLocal<>();

    /**
     * 当前会话 ID（独立于 LoginContext，用于 Agent 工具调用记录关联会话上下文）
     */
    private static final ThreadLocal<String> CONVERSATION_ID = new ThreadLocal<>();

    // ==================== 快照级操作（供 ThreadLocalAccessor / 跨线程传播使用）====================

    /**
     * 获取当前上下文快照
     *
     * @return 当前线程的 LoginContext，未设置时返回 null
     */
    public static LoginContext current() {
        return CONTEXT.get();
    }

    /**
     * 设置当前上下文快照
     *
     * @param ctx 上下文快照，传入 null 等同于 {@link #clear()}
     */
    public static void setCurrent(LoginContext ctx) {
        if (ctx == null) {
            CONTEXT.remove();
        } else {
            CONTEXT.set(ctx);
        }
    }

    // ==================== 个体字段读写 ====================

    /**
     * 设置租户 ID
     */
    public static void setTenantId(Long tenantId) {
        LoginContext cur = current();
        setCurrent(new LoginContext(
                tenantId,
                cur != null ? cur.userId() : null,
                cur != null ? cur.username() : null,
                cur != null ? cur.roles() : null,
                cur != null ? cur.permissions() : null
        ));
        log.debug("设置租户 ID: {}", tenantId);
    }

    /**
     * 获取租户 ID
     */
    public static Long getTenantId() {
        LoginContext ctx = current();
        return ctx != null ? ctx.tenantId() : null;
    }

    /**
     * 设置用户 ID
     */
    public static void setUserId(Long userId) {
        LoginContext cur = current();
        setCurrent(new LoginContext(
                cur != null ? cur.tenantId() : null,
                userId,
                cur != null ? cur.username() : null,
                cur != null ? cur.roles() : null,
                cur != null ? cur.permissions() : null
        ));
    }

    /**
     * 获取用户 ID
     */
    public static Long getUserId() {
        LoginContext ctx = current();
        return ctx != null ? ctx.userId() : null;
    }

    /**
     * 设置用户名
     */
    public static void setUsername(String username) {
        LoginContext cur = current();
        setCurrent(new LoginContext(
                cur != null ? cur.tenantId() : null,
                cur != null ? cur.userId() : null,
                username,
                cur != null ? cur.roles() : null,
                cur != null ? cur.permissions() : null
        ));
    }

    /**
     * 获取用户名
     */
    public static String getUsername() {
        LoginContext ctx = current();
        return ctx != null ? ctx.username() : null;
    }

    /**
     * 设置角色列表
     */
    public static void setRoles(String[] roles) {
        LoginContext cur = current();
        setCurrent(new LoginContext(
                cur != null ? cur.tenantId() : null,
                cur != null ? cur.userId() : null,
                cur != null ? cur.username() : null,
                roles,
                cur != null ? cur.permissions() : null
        ));
    }

    /**
     * 获取角色列表
     */
    public static String[] getRoles() {
        LoginContext ctx = current();
        return ctx != null ? ctx.roles() : null;
    }

    /**
     * 设置权限列表
     */
    public static void setPermissions(String[] permissions) {
        LoginContext cur = current();
        setCurrent(new LoginContext(
                cur != null ? cur.tenantId() : null,
                cur != null ? cur.userId() : null,
                cur != null ? cur.username() : null,
                cur != null ? cur.roles() : null,
                permissions
        ));
    }

    /**
     * 获取权限列表
     */
    public static String[] getPermissions() {
        LoginContext ctx = current();
        return ctx != null ? ctx.permissions() : null;
    }

    // ==================== 会话 ID ====================

    /**
     * 设置会话 ID（Agent 执行入口调用，工具适配器通过 {@link #getConversationId()} 读取）
     *
     * @param conversationId 会话 ID
     */
    public static void setConversationId(String conversationId) {
        CONVERSATION_ID.set(conversationId);
    }

    /**
     * 获取会话 ID
     *
     * @return 当前线程绑定的会话 ID，未设置时返回 null
     */
    public static String getConversationId() {
        return CONVERSATION_ID.get();
    }

    /**
     * 清除会话 ID
     */
    public static void clearConversationId() {
        CONVERSATION_ID.remove();
    }

    // ==================== 角色与权限判定 ====================

    /**
     * 判断是否是超级管理员
     */
    public static boolean isSuperAdmin() {
        String[] roles = getRoles();
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
        String[] roles = getRoles();
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

        String[] permissions = getPermissions();
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
        String[] roles = getRoles();
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
        CONTEXT.remove();
        CONVERSATION_ID.remove();
        log.debug("清除 Base 上下文");
    }

    /**
     * 从 HttpServletRequest Header 中提取并设置上下文信息
     *
     * <p>一次性构建不可变快照，避免多次 ThreadLocal 写入。
     *
     * @param userIdHeader      用户 ID Header（X-User-Id）
     * @param usernameHeader    用户名 Header（X-Username）
     * @param tenantIdHeader    租户 ID Header（X-Tenant-Id）
     * @param rolesHeader       角色列表 Header（X-Roles，逗号分隔）
     * @param permissionsHeader 权限列表 Header（X-Permissions，逗号分隔）
     */
    public static void initFromHeaders(String userIdHeader, String usernameHeader,
                                       String tenantIdHeader, String rolesHeader,
                                       String permissionsHeader) {
        Long userId = parseLongHeader("X-User-Id", userIdHeader);
        Long tenantId = parseLongHeader("X-Tenant-Id", tenantIdHeader);
        String[] roles = (rolesHeader != null && !rolesHeader.isEmpty()) ? rolesHeader.split(",") : null;
        String[] permissions = (permissionsHeader != null && !permissionsHeader.isEmpty())
                ? permissionsHeader.split(",") : null;

        setCurrent(new LoginContext(tenantId, userId, usernameHeader, roles, permissions));

        log.debug("BaseContext 初始化完成: userId={}, username={}, tenantId={}, roles={}",
                userId, usernameHeader, tenantId, roles);
    }

    private static Long parseLongHeader(String headerName, String value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "请求头 " + headerName + " 的值不是合法数字: " + value, e);
        }
    }
}
