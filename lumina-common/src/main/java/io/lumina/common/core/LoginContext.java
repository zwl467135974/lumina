package io.lumina.common.core;

/**
 * 登录上下文快照
 *
 * <p>聚合当前请求的租户、用户、角色、权限信息，作为 {@link BaseContext} 的 ThreadLocal 存储单元。
 * <p>采用 record 保证不可变性，便于在 Reactor 跨线程传播时安全共享，避免可变状态引发的并发问题。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
public record LoginContext(
        Long tenantId,
        Long userId,
        String username,
        String[] roles,
        String[] permissions
) {

    /**
     * 空上下文（所有字段为 null）
     *
     * @return 空的 LoginContext 实例
     */
    public static LoginContext empty() {
        return new LoginContext(null, null, null, null, null);
    }
}
