package io.lumina.agent.tool.security;

/**
 * 工具只读分类器（v3.13 只读分级与自动放行）
 *
 * <p>判定一次工具调用是否"可证明只读"。可证明只读的调用在安全管线中
 * 豁免人工审批（ASK → 放行）；判定不了即返回 false 走原有审批路径
 * （fail-closed：证据不足不放行）。
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
     * 判定工具调用是否可证明只读
     *
     * @param context 工具执行上下文（只读视图）
     * @return true = 可证明只读（豁免审批）；false = 判定不了或非只读
     */
    boolean isProvablyReadOnly(ToolExecutionContext context);
}
