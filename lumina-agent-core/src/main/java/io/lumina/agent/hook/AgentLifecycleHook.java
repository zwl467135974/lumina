package io.lumina.agent.hook;

import org.springframework.core.Ordered;

/**
 * 决策型生命周期 Hook（v3.14 批次 3.1，借鉴 ZCode hooks / DSH B1）
 *
 * <p>企业合规门（脱敏、外发审计、输入风控、完成标准校验）实现本接口并注册为
 * Spring Bean 即可介入 Agent 轮次的关键决策点，<b>不改引擎</b>。与观测事件总线
 * （{@code AgentTurnEvent}）的职责划分：事件是单向通知（指标/审计/计费），
 * 钩子是同步决策（可拒绝、可改写、可注入）。
 *
 * <p>决策点与轮次时序：
 * <ol>
 *   <li>{@link #onSessionStart} —— 会话首轮（历史记忆为空）提交前，注入会话级上下文</li>
 *   <li>{@link #onUserPromptSubmit} —— 用户输入提交 LLM 前，可拒绝/替换/附加上下文</li>
 *   <li>{@link #onPreToolUse} —— 工具执行前（在安全管线之前，便宜检查先行）</li>
 *   <li>{@link #onPostToolUse} / {@link #onPostToolUseFailure} —— 工具执行后（纯观测）</li>
 *   <li>{@link #onStop} —— 轮次产出最终回答后，可判定"未达完成标准"注入继续指令</li>
 * </ol>
 *
 * <p>工程契约：
 * <ul>
 *   <li>实现必须快速返回（同步处于轮次关键路径），单次调用受
 *       {@code lumina.agent.hooks.timeout-ms} 超时保护——超时/异常按放行处理（中立决策），
 *       绝不崩回合；需要强一致的合规门应在实现内自行降级为 deny</li>
 *   <li>输出字段受大小上限截断（{@code lumina.agent.hooks.max-*-chars}）</li>
 *   <li>阻断路径（DENY / CONTINUE）均带钩子名与理由的审计日志，拒绝理由对模型/调用方可见</li>
 *   <li>Stop 的 CONTINUE 受每回合防循环上限（{@code max-stop-continues}）约束，达上限强制结束</li>
 * </ul>
 *
 * <p>默认全局开关 {@code lumina.agent.hooks.enabled}（默认 false）——零钩子时本就空转，
 * 开关是企业合规介入的显式授权动作。
 *
 * @author Lumina Team
 * @since 3.14.0
 */
public interface AgentLifecycleHook extends Ordered {

    /** 多钩子执行顺序（默认 0，同序按注册序） */
    @Override
    default int getOrder() {
        return 0;
    }

    /**
     * 钩子名（审计标识）
     *
     * <p>默认取类简单名；实现可覆盖以提供业务语义名（如 "pii-guard"）。
     */
    default String getName() {
        return getClass().getSimpleName();
    }

    /**
     * 会话首启（该 conversationId 无历史记忆）——只支持 ADD_CONTEXT 注入会话级上下文
     */
    default HookDecision onSessionStart(SessionStartInput input) {
        return HookDecision.allow();
    }

    /**
     * 用户输入提交前——支持 DENY（终止回合）/ REPLACE_INPUT / ADD_CONTEXT
     */
    default HookDecision onUserPromptSubmit(PromptSubmitInput input) {
        return HookDecision.allow();
    }

    /**
     * 工具执行前——支持 DENY（阻断调用，理由对模型可见）
     */
    default HookDecision onPreToolUse(ToolUseInput input) {
        return HookDecision.allow();
    }

    /**
     * 工具执行成功后（纯观测，看到的是模型可见的最终结果——含 spill 预览替换）
     */
    default void onPostToolUse(ToolResultInput input) {
    }

    /**
     * 工具执行失败后（纯观测：执行异常/超时，不含策略拒绝——被拒的调用没有"执行"）
     */
    default void onPostToolUseFailure(ToolResultInput input) {
    }

    /**
     * 轮次结束前——CONTINUE 判定"未达完成标准"并注入理由继续执行（防循环上限内）
     */
    default HookDecision onStop(StopInput input) {
        return HookDecision.allow();
    }

    /** 会话首启输入 */
    record SessionStartInput(String businessType, Long agentId, String agentName, String conversationId) {
    }

    /** 用户输入提交前输入 */
    record PromptSubmitInput(String businessType, Long agentId, String agentName, String conversationId,
                             String sessionMode, String prompt, boolean streaming) {
    }

    /** 工具执行前输入 */
    record ToolUseInput(String toolName, String category, String paramsJson, String conversationId) {
    }

    /** 工具执行后输入（成功时 errorMessage 为 null，失败时 resultText 为 null） */
    record ToolResultInput(String toolName, String category, String paramsJson, String resultText,
                           String errorMessage, long durationMs, String conversationId) {
    }

    /** 轮次结束前输入（continueCount 已用的继续次数，maxContinues 防循环上限） */
    record StopInput(String businessType, Long agentId, String agentName, String conversationId,
                     String responseText, int continueCount, int maxContinues) {
    }
}
