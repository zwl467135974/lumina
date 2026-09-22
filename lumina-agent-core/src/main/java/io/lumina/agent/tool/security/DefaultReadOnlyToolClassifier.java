package io.lumina.agent.tool.security;

import io.lumina.agent.config.LuminaAgentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 默认只读分类器（三层证据，任一命中即放行豁免）
 *
 * <p>判定来源（借鉴 ZCode bash-readonly-policy 的"分层证明"方法论，
 * 按 Lumina 工具面收窄为三层）：
 * <ol>
 *   <li><b>名单</b>：{@code readonly-tools} 配置（精确名 / {@code 前缀*} 通配），
 *       适合内置工具与整组 MCP 工具（如 {@code mcp__playwright__browser_snapshot}）</li>
 *   <li><b>自报注解</b>：MCP Tool Annotations 的 {@code readOnlyHint=true}
 *       （server 自报声明，作为证据之一但非充分安全保证）</li>
 *   <li><b>代码启发式</b>：{@code code.execute} 的静态分析——导入白名单 +
 *       危险词元黑名单双约束，判不出即不放行</li>
 * </ol>
 *
 * <p>总开关 {@code readonly-auto-approve} 默认关闭：关闭时一律返回 false
 * （等价于功能未上线，行为与 3.12 完全一致）。
 *
 * @author Lumina Team
 * @since 3.13.0
 */
@Slf4j
@Component
public class DefaultReadOnlyToolClassifier implements ReadOnlyToolClassifier {

    /** 代码解释器工具名（启发式判定的对象） */
    static final String CODE_EXECUTE_TOOL = "code.execute";

    private final LuminaAgentProperties agentProperties;

    public DefaultReadOnlyToolClassifier(LuminaAgentProperties agentProperties) {
        this.agentProperties = agentProperties;
    }

    @Override
    public boolean isProvablyReadOnly(ToolExecutionContext context) {
        if (context == null || context.getToolName() == null) {
            return false;
        }
        LuminaAgentProperties.SecurityConfig security = agentProperties.getTool().getSecurity();
        if (!security.isReadonlyAutoApprove()) {
            return false;
        }
        if (matchesReadonlyList(security, context.getToolName())) {
            log.debug("只读分级命中名单: tool={}", context.getToolName());
            return true;
        }
        if (Boolean.TRUE.equals(context.getReadOnlyHint())) {
            log.debug("只读分级命中 MCP readOnlyHint: tool={}", context.getToolName());
            return true;
        }
        if (CODE_EXECUTE_TOOL.equals(context.getToolName())
                && CodeReadOnlyHeuristic.isProvablyReadOnly(context.getParamsJson())) {
            log.debug("只读分级命中代码启发式: tool={}", context.getToolName());
            return true;
        }
        return false;
    }

    /** 名单匹配：精确名，或以 {@code *} 结尾的前缀通配（如 {@code mcp__playwright__browser_get*}） */
    private boolean matchesReadonlyList(LuminaAgentProperties.SecurityConfig security, String toolName) {
        for (String pattern : security.getReadonlyTools()) {
            if (pattern == null || pattern.isEmpty()) {
                continue;
            }
            if (pattern.endsWith("*")) {
                if (toolName.startsWith(pattern.substring(0, pattern.length() - 1))) {
                    return true;
                }
            } else if (pattern.equals(toolName)) {
                return true;
            }
        }
        return false;
    }
}
