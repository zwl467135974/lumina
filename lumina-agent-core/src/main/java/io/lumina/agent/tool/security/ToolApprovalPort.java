package io.lumina.agent.tool.security;

/**
 * 工具审批端口（allow-once 语义，fail-closed）
 *
 * <p>拦截器返回 ASK 后，由审批端口向人类请求一次性授权。
 * <b>fail-closed 契约</b>：无实现、实现抛异常、超时、人类拒绝——
 * 一律视为未获批准（返回 false），绝不静默放行。
 *
 * <p>实现方注意：授权只对"被问及的那一次调用"生效，
 * 不产生会话级或租户级持久许可（allow-once，无授权记忆）。
 *
 * @author Lumina Team
 * @since 3.11.0
 */
public interface ToolApprovalPort {

    /**
     * 请求一次性人工审批
     *
     * @param context 工具执行上下文
     * @param reason  拦截器给出的审批原因
     * @return true=本次放行；false=拒绝/超时/通道不可用（fail-closed）
     */
    boolean requestApproval(ToolExecutionContext context, String reason);
}
