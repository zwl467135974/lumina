package io.lumina.agent.security;

/**
 * 外部来源内容安检接缝（外部 Agent 返回 / 远端技能文本等）
 *
 * <p>agent-core 不感知具体检测策略（提示注入规则等实现在业务层）；
 * 外部内容进入模型上下文前必须经过本接缝——外部产出 = 外部可控的
 * 注入源，与用户输入同级但不可信度更高（无租户边界内的人类在环）。
 *
 * <p>实现方遵循 fail-closed：命中策略时不回传原文（摘要也不行——
 * 任何原文片段都可能携带载荷），仅返回拦截说明与元信息。
 * 未注册实现时调用方按原样放行（与 TraceSink/ToolUsageSink 同一
 * 可选装配模式）。
 *
 * @author Lumina Team
 * @since 3.12.1
 */
public interface ExternalContentSanitizer {

    /**
     * 安检外部内容，返回可安全进入模型上下文的文本
     *
     * @param content 外部原始内容
     * @param source  来源标识（如 "a2a:https://..."），用于拦截说明与日志
     * @return 通过时原样返回（或实现方标注来源后返回）；命中策略时返回拦截说明
     */
    String sanitize(String content, String source);
}
