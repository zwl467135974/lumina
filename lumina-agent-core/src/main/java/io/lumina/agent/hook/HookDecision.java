package io.lumina.agent.hook;

/**
 * 生命周期钩子决策输出（标准决策词汇）
 *
 * <p>各决策点支持的动作（其余动作在该点无效，{@link AgentHookInvoker} 按中立处理并告警）：
 * <ul>
 *   <li><b>UserPromptSubmit</b>：ALLOW / DENY / REPLACE_INPUT / ADD_CONTEXT</li>
 *   <li><b>SessionStart</b>：ALLOW / ADD_CONTEXT（会话首启不允许拒绝）</li>
 *   <li><b>PreToolUse</b>：ALLOW / DENY</li>
 *   <li><b>Stop</b>：ALLOW（允许结束）/ CONTINUE（判定未达完成标准，注入理由继续执行）</li>
 *   <li><b>PostToolUse / PostToolUseFailure</b>：纯观测，无决策</li>
 * </ul>
 *
 * <p>组合语义（多钩子按 Order 升序）：DENY 支配且短路后续钩子；REPLACE_INPUT
 * 首个生效；ADD_CONTEXT 累积合并。所有输出字段经大小上限截断（防钩子撑爆上下文）。
 *
 * @author Lumina Team
 * @since 3.14.0
 */
public record HookDecision(Action action, String reason, String replacementInput, String additionalContext) {

    /** 决策动作 */
    public enum Action {
        /** 放行（各点的中立语义） */
        ALLOW,
        /** 拒绝：UserPromptSubmit 终止回合 / PreToolUse 阻断调用，理由对调用方可见 */
        DENY,
        /** 替换输入（仅 UserPromptSubmit）：以 replacementInput 替代原用户输入 */
        REPLACE_INPUT,
        /** 追加上下文：注入 additionalContext（会话级/轮次级附加上下文） */
        ADD_CONTEXT,
        /** 阻止结束并继续执行（仅 Stop）：以 reason 作为继续指令注入，受防循环上限约束 */
        CONTINUE
    }

    public static HookDecision allow() {
        return new HookDecision(Action.ALLOW, null, null, null);
    }

    public static HookDecision deny(String reason) {
        return new HookDecision(Action.DENY, reason, null, null);
    }

    public static HookDecision replaceInput(String replacementInput, String reason) {
        return new HookDecision(Action.REPLACE_INPUT, reason, replacementInput, null);
    }

    public static HookDecision addContext(String additionalContext) {
        return new HookDecision(Action.ADD_CONTEXT, null, null, additionalContext);
    }

    public static HookDecision continueTurn(String reason) {
        return new HookDecision(Action.CONTINUE, reason, null, null);
    }

    public boolean denied() {
        return action == Action.DENY;
    }
}
