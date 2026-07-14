package io.lumina.agent.tool.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lumina.agent.manager.EnhancedToolManager;
import io.lumina.agent.tool.ToolDefinition;
import io.lumina.agent.util.JsonUtils;
import io.lumina.common.core.ErrorCode;
import io.lumina.common.exception.BusinessException;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具注册器
 *
 * <p>应用就绪后连接所有配置的 MCP Server，将其暴露的工具逐一注册到
 * {@link EnhancedToolManager}，使 Lumina Agent 可像调用本地工具一样调用 MCP 工具。
 *
 * <p>仅在 {@code lumina.mcp.enabled=true} 时激活。
 *
 * <p>容错策略：
 * <ul>
 *   <li>单个 server 的 listTools 失败只 {@code log.warn} 跳过，不影响其它 server</li>
 *   <li>单个工具的注册失败只 {@code log.warn} 跳过，不影响同一 server 的其它工具</li>
 * </ul>
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "lumina.mcp", name = "enabled", havingValue = "true")
public class McpToolRegistrar {

    private static final ObjectMapper objectMapper = JsonUtils.OBJECT_MAPPER;

    /**
     * 内容类型：文本
     */
    private static final String CONTENT_TYPE_TEXT = "text";

    private final McpServerProperties properties;
    private final McpClientRegistry registry;
    private final EnhancedToolManager enhancedToolManager;

    /**
     * 应用就绪后执行 MCP 工具注册
     *
     * <p>流程：
     * <ol>
     *   <li>初始化所有 MCP 客户端（连接 + 握手）</li>
     *   <li>遍历每个 client，listTools 获取工具清单</li>
     *   <li>对每个 MCP Tool 构造 {@link ToolDefinition} 并注册到 {@link EnhancedToolManager}</li>
     * </ol>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("MCP 已启用，开始接入 MCP Server 并注册工具");

        // 1. 初始化所有客户端（连接 + 握手）
        registry.init(properties);

        // 2. 遍历 client，注册其工具
        int totalRegistered = 0;
        for (Map.Entry<String, McpSyncClient> entry : registry.getAllClientEntries().entrySet()) {
            String serverName = entry.getKey();
            McpSyncClient client = entry.getValue();
            totalRegistered += registerToolsFromServer(serverName, client);
        }

        log.info("MCP 工具注册完成，共注册 {} 个工具", totalRegistered);
    }

    /**
     * 注册单个 MCP Server 暴露的所有工具
     *
     * @param serverName server 名称
     * @param client     已握手的 MCP 客户端
     * @return 成功注册的工具数
     */
    private int registerToolsFromServer(String serverName, McpSyncClient client) {
        List<McpSchema.Tool> tools;
        try {
            McpSchema.ListToolsResult result = client.listTools();
            tools = result == null ? null : result.tools();
        } catch (Throwable e) {
            log.warn("MCP Server [{}] listTools 失败，已跳过: {}", serverName, e.getMessage(), e);
            return 0;
        }

        if (tools == null || tools.isEmpty()) {
            log.info("MCP Server [{}] 未暴露任何工具", serverName);
            return 0;
        }

        log.info("MCP Server [{}] 暴露 {} 个工具，开始注册", serverName, tools.size());
        int count = 0;
        for (McpSchema.Tool tool : tools) {
            if (registerOneTool(serverName, client, tool)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 注册单个 MCP 工具
     *
     * @return 是否注册成功
     */
    private boolean registerOneTool(String serverName, McpSyncClient client, McpSchema.Tool mcpTool) {
        String originalName = mcpTool.name();
        if (originalName == null || originalName.isEmpty()) {
            log.warn("MCP Server [{}] 存在无名称的工具，已跳过", serverName);
            return false;
        }

        String registeredName = properties.getToolPrefix() + serverName + "__" + originalName;
        String description = mcpTool.description() == null ? ("MCP tool: " + originalName) : mcpTool.description();
        String category = "mcp." + serverName;
        String parameters = serializeInputSchema(mcpTool.inputSchema());

        ToolDefinition definition = ToolDefinition.create(registeredName, description, category, params ->
                invokeTool(client, originalName, registeredName, params));
        definition.setParameters(parameters);

        try {
            enhancedToolManager.registerToolDefinition(definition);
            return true;
        } catch (Throwable e) {
            log.warn("注册 MCP 工具 [{}] 失败: {}", registeredName, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 调用 MCP 工具并返回拼接后的文本结果
     *
     * @param client          MCP 客户端
     * @param originalName    MCP 原始工具名
     * @param registeredName  注册后的工具名（用于日志）
     * @param params          JSON 格式参数字符串
     * @return 拼接后的文本结果
     */
    @SuppressWarnings("unchecked")
    private Object invokeTool(McpSyncClient client, String originalName, String registeredName, String params) {
        Map<String, Object> args;
        try {
            if (params == null || params.trim().isEmpty() || "{}".equals(params.trim())) {
                args = new LinkedHashMap<>();
            } else {
                args = objectMapper.readValue(params, Map.class);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("MCP 工具 [" + registeredName + "] 参数解析失败: " + e.getMessage(), e);
        }

        McpSchema.CallToolResult result;
        try {
            result = client.callTool(new McpSchema.CallToolRequest(originalName, args));
        } catch (Throwable e) {
            throw new BusinessException(ErrorCode.MCP_TOOL_CALL_FAILED, "MCP 工具 [" + registeredName + "] 调用失败: " + e.getMessage(), e);
        }

        return extractText(result, registeredName);
    }

    /**
     * 从 CallToolResult 提取文本内容
     *
     * <p>遍历 {@code result.content()}，把 TextContent 的 text 拼接成单个字符串。
     * 非文本内容（image/audio/resource）以占位符形式追加，保证结果可读。
     * 若 server 返回错误标志，将错误信息一并写入返回值。
     */
    private String extractText(McpSchema.CallToolResult result, String registeredName) {
        if (result == null) {
            return "";
        }

        List<McpSchema.Content> contents = result.content();
        if (contents == null || contents.isEmpty()) {
            // result 提供了纯文本构造重载，content 可能为空但 isError 可能被设置
            return "";
        }

        List<String> parts = new ArrayList<>();
        for (McpSchema.Content content : contents) {
            if (content == null) {
                continue;
            }
            try {
                String type = content.type();
                if (CONTENT_TYPE_TEXT.equals(type) && content instanceof McpSchema.TextContent) {
                    String text = ((McpSchema.TextContent) content).text();
                    if (text != null && !text.isEmpty()) {
                        parts.add(text);
                    }
                } else {
                    parts.add("[" + type + "]");
                }
            } catch (Throwable e) {
                log.warn("MCP 工具 [{}] 解析 content 失败: {}", registeredName, e.getMessage());
            }
        }

        String joined = String.join("\n", parts);

        // MCP 协议中 isError=true 表示工具执行出错（非传输错误），把错误标志透出给上层
        if (Boolean.TRUE.equals(result.isError())) {
            return "MCP tool error: " + joined;
        }
        return joined;
    }

    /**
     * 将 MCP Tool 的 inputSchema 序列化为 JSON Schema 字符串
     *
     * <p>{@link McpSchema.JsonSchema} 是 record，直接序列化字段名与 JSON Schema 略有出入
     * （如 additionalProperties），这里手动构造以保证与标准 JSON Schema 形态一致。
     */
    private String serializeInputSchema(McpSchema.JsonSchema inputSchema) {
        if (inputSchema == null) {
            return "{\"type\":\"object\",\"properties\":{}}";
        }
        try {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", inputSchema.type() == null ? "object" : inputSchema.type());
            if (inputSchema.properties() != null) {
                schema.put("properties", inputSchema.properties());
            }
            if (inputSchema.required() != null) {
                schema.put("required", inputSchema.required());
            }
            if (inputSchema.additionalProperties() != null) {
                schema.put("additionalProperties", inputSchema.additionalProperties());
            }
            if (inputSchema.defs() != null && !inputSchema.defs().isEmpty()) {
                schema.put("$defs", inputSchema.defs());
            }
            if (inputSchema.definitions() != null && !inputSchema.definitions().isEmpty()) {
                schema.put("definitions", inputSchema.definitions());
            }
            return objectMapper.writeValueAsString(schema);
        } catch (Exception e) {
            log.warn("序列化 MCP 工具 inputSchema 失败: {}", e.getMessage());
            return "{\"type\":\"object\",\"properties\":{}}";
        }
    }

    /**
     * 销毁时关闭所有 MCP 客户端（stdio 会留子进程，必须显式关闭）
     *
     * <p>委托给 {@link McpClientRegistry#close()}，此处由 registry 自身的 @PreDestroy 负责，
     * 这里仅保留钩子以保证注册器销毁顺序可控（registry 作为依赖 bean 会先于本类销毁）。
     */
    @PreDestroy
    public void destroy() {
        log.info("McpToolRegistrar 销毁中，客户端关闭由 McpClientRegistry @PreDestroy 负责");
    }
}
