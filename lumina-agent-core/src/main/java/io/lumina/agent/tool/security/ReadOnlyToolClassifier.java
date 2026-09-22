package io.lumina.agent.tool.security;

/**
 * 工具只读分类器（v3.13 只读分级与自动放行）
 *
 * <p>判定一次工具调用是否"可证明只读"。两个用途、两种门控：
 * <ul>
 *   <li>{@link #isProvablyReadOnly}：<b>纯判定</b>（无开关）——PLAN 会话模式
 *       的只读约束使用（用户显式选择 plan 即是开关），也是豁免判定的底座</li>
 *   <li>{@link #allowsExemption}：<b>豁免判定</b>（默认委托纯判定）——
 *       审批豁免用途使用，实现可叠加 {@code readonly-auto-approve} 总开关</li>
 * </ul>
 *
 * <p>语义边界：只读分级是<b>审批豁免层</b>，不是放行层——DENY 决策、
 * 单调守卫的否决不受其影响；它只能把"需要人批"降级为"免批"，
 * 永远不能把"被拒绝"翻回"放行"（单调性不破坏）。
 *
 * @author Lumina Team
 * @since 3.13.0
 */
public interface ReadOnlyToolClassifier {

    /**
     * 判定工具调用是否可证明只读（纯判定，无开关）
     *
     * @param context 工具执行上下文（只读视图）
     * @return true = 可证明只读；false = 判定不了或非只读
     */
    boolean isProvablyReadOnly(ToolExecutionContext context);

    /**
     * 审批豁免判定（默认委托纯判定）
     *
     * <p>管线在 ASK 汇总非空时调用此方法决定是否跳过审批；
     * {@link DefaultReadOnlyToolClassifier} 在此叠加
     * {@code readonly-auto-approve} 总开关（默认关闭）。
     *
     * @param context 工具执行上下文（只读视图）
     * @return true = 可豁免审批直接执行
     */
    default boolean allowsExemption(ToolExecutionContext context) {
        return isProvablyReadOnly(context);
    }
}
