package io.lumina.agent.tool.security;

import org.springframework.core.Ordered;

/**
 * 工具执行拦截器（可扩展策略层）
 *
 * <p>在工具执行前运行，可以拒绝（DENY）或要求人工审批（ASK）。
 * 拦截器是"可协商策略"：任何拦截器的拒绝仍可能被后续配置调整，
 * 但<b>永远无法翻越 {@link ToolGuard 单调守卫}</b>——守卫在所有拦截器
 * 和审批之后求值，且只有 deny 臂。
 *
 * <p>实现类注册为 Spring Bean 即自动加入链，按 {@link #getOrder()} 升序执行。
 *
 * @author Lumina Team
 * @since 3.11.0
 */
public interface ToolExecutionInterceptor extends Ordered {

    /**
     * 工具执行前决策
     *
     * @param context 工具执行上下文（只读）
     * @return 决策（CONTINUE/DENY/ASK）
     */
    ToolDecision beforeExecute(ToolExecutionContext context);
}
