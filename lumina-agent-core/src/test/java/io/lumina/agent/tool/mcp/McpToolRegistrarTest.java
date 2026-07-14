package io.lumina.agent.tool.mcp;

import io.lumina.agent.manager.EnhancedToolManager;
import io.lumina.agent.tool.ToolDefinition;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * MCP 工具注册器单元测试
 *
 * <p>验证 MCP Server 工具的注册流程、容错策略、调用链路与文本提取逻辑。
 * 全程 Mock {@link McpSyncClient}，不产生真实网络/进程 IO。
 *
 * @author Lumina Team
 * @since 3.2.0
 */
@ExtendWith(MockitoExtension.class)
class McpToolRegistrarTest {

    @Mock
    private McpServerProperties properties;

    @Mock
    private McpClientRegistry registry;

    @Mock
    private EnhancedToolManager enhancedToolManager;

    @Mock
    private McpSyncClient client;

    private McpToolRegistrar registrar;

    @BeforeEach
    void setUp() {
        registrar = new McpToolRegistrar(properties, registry, enhancedToolManager);
    }

    // ==================== 注册流程 ====================

    @Test
    void registerMultipleToolsSuccess() {
        // given: properties 配置前缀
        when(properties.getToolPrefix()).thenReturn("mcp__");

        // given: registry 返回一个 client
        Map<String, McpSyncClient> clients = new LinkedHashMap<>();
        clients.put("test-server", client);
        when(registry.getAllClientEntries()).thenReturn(clients);
        doNothing().when(registry).init(any());

        // given: client.listTools 返回 2 个工具
        McpSchema.Tool tool1 = buildTool("echo", "回显工具");
        McpSchema.Tool tool2 = buildTool("calc", "计算工具");
        when(client.listTools()).thenReturn(
                new McpSchema.ListToolsResult(List.of(tool1, tool2), null));

        // when: 触发注册
        registrar.onApplicationReady();

        // then: registerToolDefinition 被调用 2 次
        ArgumentCaptor<ToolDefinition> captor = ArgumentCaptor.forClass(ToolDefinition.class);
        verify(enhancedToolManager, times(2)).registerToolDefinition(captor.capture());

        List<ToolDefinition> registered = captor.getAllValues();
        assertThat(registered).hasSize(2);
        assertThat(registered.get(0).getName()).isEqualTo("mcp__test-server__echo");
        assertThat(registered.get(0).getCategory()).isEqualTo("mcp.test-server");
        assertThat(registered.get(1).getName()).isEqualTo("mcp__test-server__calc");
    }

    @Test
    void listToolsFailureSkipsServer() {
        // given: registry 返回 client，但 listTools 抛异常
        Map<String, McpSyncClient> clients = new LinkedHashMap<>();
        clients.put("bad-server", client);
        when(registry.getAllClientEntries()).thenReturn(clients);
        doNothing().when(registry).init(any());
        when(client.listTools()).thenThrow(new RuntimeException("connection lost"));

        // when: 触发注册
        registrar.onApplicationReady();

        // then: 不注册任何工具，不抛异常
        verify(enhancedToolManager, never()).registerToolDefinition(any());
    }

    @Test
    void emptyToolListSkipsRegistration() {
        // given: client 返回空工具列表
        Map<String, McpSyncClient> clients = new LinkedHashMap<>();
        clients.put("empty-server", client);
        when(registry.getAllClientEntries()).thenReturn(clients);
        doNothing().when(registry).init(any());
        when(client.listTools()).thenReturn(
                new McpSchema.ListToolsResult(List.of(), null));

        // when
        registrar.onApplicationReady();

        // then
        verify(enhancedToolManager, never()).registerToolDefinition(any());
    }

    @Test
    void unnamedToolSkipped() {
        // given: MCP Tool 的 name() 返回空字符串
        // （SDK builder 不允许空 name，但 registrar 仍需防御性检查——mock 返回 name()=空）
        McpSchema.Tool unnamed = mock(McpSchema.Tool.class);
        when(unnamed.name()).thenReturn("");
        McpSchema.Tool named = buildTool("valid", "有效工具");

        when(properties.getToolPrefix()).thenReturn("mcp__");
        Map<String, McpSyncClient> clients = new LinkedHashMap<>();
        clients.put("server", client);
        when(registry.getAllClientEntries()).thenReturn(clients);
        doNothing().when(registry).init(any());
        when(client.listTools()).thenReturn(
                new McpSchema.ListToolsResult(List.of(unnamed, named), null));

        // when
        registrar.onApplicationReady();

        // then: 只注册有名称的工具
        ArgumentCaptor<ToolDefinition> captor = ArgumentCaptor.forClass(ToolDefinition.class);
        verify(enhancedToolManager, times(1)).registerToolDefinition(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("mcp__server__valid");
    }

    @Test
    void noServersConfigured() {
        // given: 没有配置任何 MCP Server
        when(registry.getAllClientEntries()).thenReturn(new LinkedHashMap<>());

        // when
        registrar.onApplicationReady();

        // then
        verify(enhancedToolManager, never()).registerToolDefinition(any());
    }

    // ==================== 工具调用链路 ====================

    @Test
    void invokeToolReturnsTextContent() throws Exception {
        // given: 注册一个工具并捕获其 executor
        ToolDefinition toolDef = registerAndCapture("search", "server");

        // given: client.callTool 返回文本内容
        when(client.callTool(any())).thenReturn(
                new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("hello world")),
                        Boolean.FALSE));

        // when: 执行工具（参数是 JSON 字符串）
        Object result = toolDef.getExecutor().execute("{\"query\": \"test\"}");

        // then: 返回拼接的文本
        assertThat(result).isEqualTo("hello world");

        // then: callTool 用正确的工具名和参数调用
        ArgumentCaptor<McpSchema.CallToolRequest> reqCaptor =
                ArgumentCaptor.forClass(McpSchema.CallToolRequest.class);
        verify(client).callTool(reqCaptor.capture());
        assertThat(reqCaptor.getValue().name()).isEqualTo("search");
        assertThat(reqCaptor.getValue().arguments()).containsEntry("query", "test");
    }

    @Test
    void invokeToolWithErrorFlag() throws Exception {
        // given
        ToolDefinition toolDef = registerAndCapture("failing-tool", "server");

        when(client.callTool(any())).thenReturn(
                new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("tool crashed")),
                        Boolean.TRUE));  // isError = true

        // when
        Object result = toolDef.getExecutor().execute("{}");

        // then: 返回值包含错误前缀
        assertThat((String) result).contains("MCP tool error");
        assertThat((String) result).contains("tool crashed");
    }

    @Test
    void invokeToolWithEmptyParams() throws Exception {
        // given
        ToolDefinition toolDef = registerAndCapture("noop", "server");

        when(client.callTool(any())).thenReturn(
                new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("done")),
                        Boolean.FALSE));

        // when: 空参数 / {} 参数
        Object result1 = toolDef.getExecutor().execute("");
        Object result2 = toolDef.getExecutor().execute("{}");

        // then
        assertThat(result1).isEqualTo("done");
        assertThat(result2).isEqualTo("done");
    }

    @Test
    void invokeToolWithInvalidParamsThrows() throws Exception {
        // given
        ToolDefinition toolDef = registerAndCapture("tool", "server");

        // when: 参数不是合法 JSON
        // then: 抛 IllegalArgumentException
        try {
            toolDef.getExecutor().execute("not-json{");
            org.assertj.core.api.Assertions.fail("应抛出异常");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("参数解析失败");
        }
        // callTool 不应被调用
        verify(client, never()).callTool(any());
    }

    @Test
    void invokeToolCallToolFailureThrows() throws Exception {
        // given
        ToolDefinition toolDef = registerAndCapture("crash", "server");

        when(client.callTool(any())).thenThrow(new RuntimeException("network error"));

        // when + then
        try {
            toolDef.getExecutor().execute("{}");
            org.assertj.core.api.Assertions.fail("应抛出异常");
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).contains("调用失败");
            assertThat(e.getMessage()).contains("network error");
        }
    }

    @Test
    void multipleTextContentsConcatenated() throws Exception {
        // given
        ToolDefinition toolDef = registerAndCapture("multi", "server");

        when(client.callTool(any())).thenReturn(
                new McpSchema.CallToolResult(
                        List.of(
                                new McpSchema.TextContent("line1"),
                                new McpSchema.TextContent("line2"),
                                new McpSchema.TextContent("line3")),
                        Boolean.FALSE));

        // when
        Object result = toolDef.getExecutor().execute("{}");

        // then: 多段文本用换行拼接
        assertThat((String) result).contains("line1");
        assertThat((String) result).contains("line2");
        assertThat((String) result).contains("line3");
    }

    @Test
    void nullCallToolResultReturnsEmpty() throws Exception {
        // given
        ToolDefinition toolDef = registerAndCapture("null-tool", "server");

        when(client.callTool(any())).thenReturn(null);

        // when
        Object result = toolDef.getExecutor().execute("{}");

        // then
        assertThat(result).isEqualTo("");
    }

    @Test
    void inputSchemaSerializedToJsonSchema() {
        // given: 工具带 inputSchema
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
                "object",
                Map.of("query", Map.of("type", "string")),
                List.of("query"),
                true, null, null);
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("search")
                .description("搜索")
                .inputSchema(schema)
                .build();

        when(properties.getToolPrefix()).thenReturn("mcp__");
        Map<String, McpSyncClient> clients = new LinkedHashMap<>();
        clients.put("srv", client);
        when(registry.getAllClientEntries()).thenReturn(clients);
        doNothing().when(registry).init(any());
        when(client.listTools()).thenReturn(
                new McpSchema.ListToolsResult(List.of(tool), null));

        // when
        registrar.onApplicationReady();

        // then: 注册的 ToolDefinition 的 parameters 是合法 JSON Schema
        ArgumentCaptor<ToolDefinition> captor = ArgumentCaptor.forClass(ToolDefinition.class);
        verify(enhancedToolManager).registerToolDefinition(captor.capture());
        String params = captor.getValue().getParameters();
        assertThat(params).contains("\"type\":\"object\"");
        assertThat(params).contains("\"query\"");
        assertThat(params).contains("\"required\"");
    }

    // ==================== 辅助方法 ====================

    /**
     * 注册单个工具并返回捕获的 ToolDefinition，用于后续调用 executor
     */
    private ToolDefinition registerAndCapture(String toolName, String serverName) {
        when(properties.getToolPrefix()).thenReturn("mcp__");

        McpSchema.Tool tool = buildTool(toolName, "测试工具");
        Map<String, McpSyncClient> clients = new LinkedHashMap<>();
        clients.put(serverName, client);
        when(registry.getAllClientEntries()).thenReturn(clients);
        doNothing().when(registry).init(any());
        when(client.listTools()).thenReturn(
                new McpSchema.ListToolsResult(List.of(tool), null));

        registrar.onApplicationReady();

        ArgumentCaptor<ToolDefinition> captor = ArgumentCaptor.forClass(ToolDefinition.class);
        verify(enhancedToolManager).registerToolDefinition(captor.capture());
        return captor.getValue();
    }

    /**
     * 构建一个简单的 MCP Tool（无 inputSchema）
     */
    private McpSchema.Tool buildTool(String name, String description) {
        return McpSchema.Tool.builder()
                .name(name)
                .description(description)
                .build();
    }
}
