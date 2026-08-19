package io.lumina.agent.tool.security;

/**
 * 工具单调守卫（不可协商的终审否决）
 *
 * <p>借鉴 DeepSeek Harness 的单调守卫代数设计：<b>没有 allow 结果</b>——
 * 返回 null 表示弃权，返回非 null 拒绝理由即否决。守卫在所有拦截器
 * （含人工审批）<b>之后</b>求值，因此无论拦截器链如何排序、重试，
 * 一个守卫给出的拒绝在数学上不可能被任何其他策略翻回允许。
 *
 * <p>用法约定：
 * <ul>
 *   <li>可扩展的宽松策略（如租户配额提示）→ 放 {@link ToolExecutionInterceptor}</li>
 *   <li>不可协商的安全否决（如租户工具黑名单、权限校验）→ 放 {@code ToolGuard}</li>
 * </ul>
 *
 * @author Lumina Team
 * @since 3.11.0
 */
@FunctionalInterface
public interface ToolGuard {

    /**
     * 终审检查
     *
     * @param context 工具执行上下文（只读）
     * @return null=弃权（不改变现状）；非 null=拒绝理由（不可被翻转）
     */
    String check(ToolExecutionContext context);
}
