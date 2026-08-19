package io.lumina.agent.tool.security;

import io.lumina.agent.config.LuminaAgentProperties;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 配置驱动的内置工具拦截器
 *
 * <p>按配置名单决策：
 * <ul>
 *   <li>{@code lumina.agent.tool.security.deny-tools}：命中即 DENY（平台禁用）</li>
 *   <li>{@code lumina.agent.tool.security.approval-tools}：命中即 ASK（需人工审批）</li>
 * </ul>
 * deny 优先于 ask。业务定制策略请另外实现 {@link ToolExecutionInterceptor}
 * 注册为 Spring Bean，按 Order 加入链。
 *
 * @author Lumina Team
 * @since 3.11.0
 */
@Component
public class ConfigToolInterceptor implements ToolExecutionInterceptor {

    private final LuminaAgentProperties agentProperties;

    public ConfigToolInterceptor(LuminaAgentProperties agentProperties) {
        this.agentProperties = agentProperties;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public ToolDecision beforeExecute(ToolExecutionContext context) {
        List<String> denyTools = agentProperties.getTool().getSecurity().getDenyTools();
        if (denyTools != null && denyTools.contains(context.getToolName())) {
            return ToolDecision.deny("工具 " + context.getToolName() + " 已被平台策略禁用");
        }
        List<String> approvalTools = agentProperties.getTool().getSecurity().getApprovalTools();
        if (approvalTools != null && approvalTools.contains(context.getToolName())) {
            return ToolDecision.ask("工具 " + context.getToolName() + " 为高危工具，需人工审批后执行");
        }
        return ToolDecision.CONTINUE;
    }
}
