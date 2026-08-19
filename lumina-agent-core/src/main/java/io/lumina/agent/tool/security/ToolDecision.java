package io.lumina.agent.tool.security;

/**
 * 工具拦截决策
 *
 * <p>拦截器（可扩展策略）的三种决策：
 * <ul>
 *   <li>{@link Type#CONTINUE} 弃权，链上继续</li>
 *   <li>{@link Type#DENY} 拒绝（带理由，模型可见以便自纠）</li>
 *   <li>{@link Type#ASK} 需要人工审批（fail-closed：审批不通过等同拒绝）</li>
 * </ul>
 *
 * @author Lumina Team
 * @since 3.11.0
 */
public record ToolDecision(Type type, String reason) {

    public enum Type {
        CONTINUE, DENY, ASK
    }

    public static final ToolDecision CONTINUE = new ToolDecision(Type.CONTINUE, null);

    public static ToolDecision deny(String reason) {
        return new ToolDecision(Type.DENY, reason);
    }

    public static ToolDecision ask(String reason) {
        return new ToolDecision(Type.ASK, reason);
    }
}
