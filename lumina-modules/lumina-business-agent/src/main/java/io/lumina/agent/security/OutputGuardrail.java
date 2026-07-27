package io.lumina.agent.security;

/**
 * 输出护栏（Output Guardrail）
 *
 * <p>Agent 返回结果后，在交付用户之前进行最终检查：
 * <ul>
 *   <li>拦截包含敏感关键词的内容（如密码、密钥泄露）</li>
 *   <li>限制输出长度（防止 Token 炸弹）</li>
 *   <li>检测重复/循环输出（Agent 卡在循环中的症状）</li>
 * </ul>
 *
 * <p>与 {@link OutputSanitizer} 的区别：
 * <ul>
 *   <li>OutputSanitizer 做 PII 脱敏（手机/身份证打码）——<b>修改</b>内容</li>
 *   <li>OutputGuardrail 做安全检查——可以<b>拦截</b>（拒绝返回）或<b>重写</b></li>
 * </ul>
 *
 * @author Lumina Team
 * @since 3.8.0
 */
public interface OutputGuardrail {

    /**
     * 检查 Agent 输出是否合规
     *
     * @param output  Agent 的原始输出
     * @param agentId Agent ID（可按 Agent 配置不同规则）
     * @return 检查结果
     */
    GuardrailResult check(String output, Long agentId);

    /**
     * 护栏检查结果
     *
     * @param blocked   是否拦截（true = 拒绝返回，抛异常）
     * @param rewritten 重写后的内容（非 null 时替代原输出；blocked=true 时忽略）
     * @param reason    检查说明（日志/审计用）
     */
    record GuardrailResult(boolean blocked, String rewritten, String reason) {

        /** 通过检查，不修改 */
        public static GuardrailResult pass() {
            return new GuardrailResult(false, null, "通过");
        }

        /** 拦截 */
        public static GuardrailResult block(String reason) {
            return new GuardrailResult(true, null, reason);
        }

        /** 重写 */
        public static GuardrailResult rewrite(String rewritten, String reason) {
            return new GuardrailResult(false, rewritten, reason);
        }
    }
}
