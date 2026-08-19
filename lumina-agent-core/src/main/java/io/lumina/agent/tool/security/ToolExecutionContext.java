package io.lumina.agent.tool.security;

/**
 * 工具执行上下文（安全检查的只读视图）
 *
 * <p>携带工具调用的身份信息，供拦截器/守卫/审批做决策。
 * 借鉴 DeepSeek Harness 的"身份保护"设计：上下文在进入安全管线时物化，
 * 策略只能读身份做判断，不能篡改调用再放行。
 *
 * @author Lumina Team
 * @since 3.11.0
 */
public final class ToolExecutionContext {

    private final String toolName;
    private final String category;
    private final String paramsJson;
    private final String conversationId;
    private final Long tenantId;
    private final Long userId;

    public ToolExecutionContext(String toolName, String category, String paramsJson,
                                String conversationId, Long tenantId, Long userId) {
        this.toolName = toolName;
        this.category = category;
        this.paramsJson = paramsJson;
        this.conversationId = conversationId;
        this.tenantId = tenantId;
        this.userId = userId;
    }

    public String getToolName() {
        return toolName;
    }

    public String getCategory() {
        return category;
    }

    public String getParamsJson() {
        return paramsJson;
    }

    public String getConversationId() {
        return conversationId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public Long getUserId() {
        return userId;
    }
}
