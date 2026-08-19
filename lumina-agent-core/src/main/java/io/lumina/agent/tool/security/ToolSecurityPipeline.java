package io.lumina.agent.tool.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 工具安全管线（拦截器链 → 审批 → 单调守卫）
 *
 * <p>执行顺序（借鉴 DeepSeek Harness 工具执行管线的分层）：
 * <ol>
 *   <li>拦截器链（有序，DENY 立即拒绝；ASK 汇总）</li>
 *   <li>审批（ASK 时经 {@link ToolApprovalPort} 请求 allow-once，fail-closed）</li>
 *   <li>单调守卫（最后说话：任何非 null 即否决，无法被前面任何层翻回）</li>
 * </ol>
 *
 * <p>fail-closed：任一策略组件抛异常按拒绝处理，绝不因策略故障放行调用。
 *
 * @author Lumina Team
 * @since 3.11.0
 */
@Slf4j
public class ToolSecurityPipeline {

    private final List<ToolExecutionInterceptor> interceptors;
    private final List<ToolGuard> guards;

    @Nullable
    private final ToolApprovalPort approvalPort;

    public ToolSecurityPipeline(List<ToolExecutionInterceptor> interceptors,
                                List<ToolGuard> guards,
                                @Nullable ToolApprovalPort approvalPort) {
        this.interceptors = new ArrayList<>(interceptors != null ? interceptors : List.of());
        this.interceptors.sort(Comparator.comparingInt(ToolExecutionInterceptor::getOrder));
        this.guards = new ArrayList<>(guards != null ? guards : List.of());
        this.approvalPort = approvalPort;
    }

    /**
     * 执行安全检查
     *
     * @param context 工具执行上下文
     * @return null=放行；非 null=拒绝理由（模型可见）
     */
    public String check(ToolExecutionContext context) {
        // 1. 拦截器链
        List<String> askReasons = new ArrayList<>();
        for (ToolExecutionInterceptor interceptor : interceptors) {
            ToolDecision decision;
            try {
                decision = interceptor.beforeExecute(context);
            } catch (Exception e) {
                log.warn("工具拦截器异常，按拒绝处理（fail-closed）: interceptor={}, tool={}, error={}",
                        interceptor.getClass().getSimpleName(), context.getToolName(), e.getMessage());
                return "安全策略内部错误（fail-closed）: " + e.getMessage();
            }
            if (decision == null) {
                continue;
            }
            switch (decision.type()) {
                case DENY -> {
                    log.warn("工具调用被拦截器拒绝: tool={}, reason={}",
                            context.getToolName(), decision.reason());
                    return decision.reason();
                }
                case ASK -> {
                    if (decision.reason() != null) {
                        askReasons.add(decision.reason());
                    }
                }
                default -> {
                    // CONTINUE：链上继续
                }
            }
        }

        // 2. 审批（fail-closed：无端口/异常/超时/拒绝都视为未批准）
        if (!askReasons.isEmpty()) {
            String reason = String.join("; ", askReasons);
            boolean approved = false;
            if (approvalPort != null) {
                try {
                    approved = approvalPort.requestApproval(context, reason);
                } catch (Exception e) {
                    log.warn("工具审批通道异常，按拒绝处理（fail-closed）: tool={}, error={}",
                            context.getToolName(), e.getMessage());
                }
            } else {
                log.warn("工具 {} 需要审批但未配置审批端口（ToolApprovalPort），按拒绝处理", context.getToolName());
            }
            if (!approved) {
                return "需要人工审批但未获批准（被拒绝或审批通道不可用）: " + reason;
            }
            log.info("工具调用获人工批准（allow-once）: tool={}", context.getToolName());
        }

        // 3. 单调守卫（最后说话，无 allow 臂）
        for (ToolGuard guard : guards) {
            String reason;
            try {
                reason = guard.check(context);
            } catch (Exception e) {
                log.warn("工具守卫异常，按拒绝处理（fail-closed）: guard={}, tool={}, error={}",
                        guard.getClass().getSimpleName(), context.getToolName(), e.getMessage());
                return "安全守卫内部错误（fail-closed）: " + e.getMessage();
            }
            if (reason != null) {
                log.warn("工具调用被守卫否决: tool={}, reason={}", context.getToolName(), reason);
                return reason;
            }
        }
        return null;
    }
}
