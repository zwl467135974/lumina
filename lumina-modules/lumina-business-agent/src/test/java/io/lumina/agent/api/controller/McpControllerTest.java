package io.lumina.agent.api.controller;

import io.lumina.agent.api.controller.McpController.McpStatusVO;
import io.lumina.agent.api.controller.McpController.McpServerVO;
import io.lumina.agent.api.controller.McpController.McpToolVO;
import io.lumina.agent.manager.EnhancedToolManager;
import io.lumina.agent.tool.ToolDefinition;
import io.lumina.agent.tool.mcp.McpClientRegistry;
import io.lumina.agent.tool.mcp.McpServerProperties;
import io.modelcontextprotocol.client.McpSyncClient;
import io.lumina.common.core.R;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * McpController 单元测试
 *
 * <p>验证 MCP 状态查询和工具列表接口在各种场景下的正确行为：
 * MCP 未启用、已启用无 Server、已启用有 Server 连接、工具列表过滤。
 *
 * @author Lumina Team
 * @since 3.2.0
 */
@ExtendWith(MockitoExtension.class)
class McpControllerTest {

    @Mock
    private McpClientRegistry clientRegistry;

    @Mock
    private McpServerProperties mcpProperties;

    @Mock
    private EnhancedToolManager toolManager;

    @InjectMocks
    private McpController controller;

    // ==================== servers 端点 ====================

    @Test
    void serversReturnsDisabledWhenMcpPropertiesNull() {
        // mcpProperties 默认为 null（@Autowired required=false）
        R<McpStatusVO> result = controller.servers();

        assertThat(result.getData().enabled()).isFalse();
        assertThat(result.getData().servers()).isEmpty();
    }

    @Test
    void serversReturnsDisabledWhenEnabledFalse() {
        when(mcpProperties.isEnabled()).thenReturn(false);

        R<McpStatusVO> result = controller.servers();

        assertThat(result.getData().enabled()).isFalse();
        assertThat(result.getData().servers()).isEmpty();
    }

    @Test
    void serversReturnsEnabledWithNoServers() {
        when(mcpProperties.isEnabled()).thenReturn(true);
        when(mcpProperties.getServers()).thenReturn(Collections.emptyList());
        when(clientRegistry.getAllClientEntries()).thenReturn(Collections.emptyMap());

        R<McpStatusVO> result = controller.servers();

        assertThat(result.getData().enabled()).isTrue();
        assertThat(result.getData().servers()).isEmpty();
    }

    @Test
    void serversReturnsConnectedServer() {
        McpServerProperties.McpServerConfig config = new McpServerProperties.McpServerConfig();
        config.setName("echo");
        config.setTransport("stdio");
        config.setCommand("python");

        when(mcpProperties.isEnabled()).thenReturn(true);
        when(mcpProperties.getServers()).thenReturn(List.of(config));
        when(clientRegistry.getAllClientEntries()).thenReturn(Map.of("echo", org.mockito.Mockito.mock(McpSyncClient.class)));
        when(toolManager.getToolsByCategory("mcp.echo")).thenReturn(Set.of("mcp__echo__echo", "mcp__echo__ping"));

        R<McpStatusVO> result = controller.servers();

        assertThat(result.getData().enabled()).isTrue();
        assertThat(result.getData().servers()).hasSize(1);
        McpServerVO server = result.getData().servers().get(0);
        assertThat(server.name()).isEqualTo("echo");
        assertThat(server.transport()).isEqualTo("stdio");
        assertThat(server.connected()).isTrue();
        assertThat(server.toolCount()).isEqualTo(2);
        assertThat(server.command()).isEqualTo("python");
    }

    @Test
    void serversShowsDisconnectedServer() {
        McpServerProperties.McpServerConfig config = new McpServerProperties.McpServerConfig();
        config.setName("broken");
        config.setTransport("http");
        config.setUrl("http://localhost:9999/sse");

        when(mcpProperties.isEnabled()).thenReturn(true);
        when(mcpProperties.getServers()).thenReturn(List.of(config));
        when(clientRegistry.getAllClientEntries()).thenReturn(Collections.emptyMap());

        R<McpStatusVO> result = controller.servers();

        McpServerVO server = result.getData().servers().get(0);
        assertThat(server.connected()).isFalse();
        assertThat(server.toolCount()).isEqualTo(0);
        assertThat(server.url()).isEqualTo("http://localhost:9999/sse");
    }

    @Test
    void serversMultipleMixed() {
        McpServerProperties.McpServerConfig connected = new McpServerProperties.McpServerConfig();
        connected.setName("ok");
        connected.setTransport("stdio");
        connected.setCommand("npx");

        McpServerProperties.McpServerConfig disconnected = new McpServerProperties.McpServerConfig();
        disconnected.setName("fail");
        disconnected.setTransport("http");
        disconnected.setUrl("http://bad:1234");

        when(mcpProperties.isEnabled()).thenReturn(true);
        when(mcpProperties.getServers()).thenReturn(List.of(connected, disconnected));
        when(clientRegistry.getAllClientEntries()).thenReturn(Map.of("ok", org.mockito.Mockito.mock(McpSyncClient.class)));
        when(toolManager.getToolsByCategory("mcp.ok")).thenReturn(Set.of("tool1"));
        when(toolManager.getToolsByCategory("mcp.fail")).thenReturn(null);

        R<McpStatusVO> result = controller.servers();

        assertThat(result.getData().servers()).hasSize(2);
        McpServerVO s1 = result.getData().servers().get(0);
        McpServerVO s2 = result.getData().servers().get(1);
        assertThat(s1.connected()).isTrue();
        assertThat(s1.toolCount()).isEqualTo(1);
        assertThat(s2.connected()).isFalse();
        assertThat(s2.toolCount()).isEqualTo(0);
    }

    @Test
    void serversDefaultTransportIsStdio() {
        McpServerProperties.McpServerConfig config = new McpServerProperties.McpServerConfig();
        config.setName("default-transport");
        // transport 不设置（null）

        when(mcpProperties.isEnabled()).thenReturn(true);
        when(mcpProperties.getServers()).thenReturn(List.of(config));
        when(clientRegistry.getAllClientEntries()).thenReturn(Collections.emptyMap());

        R<McpStatusVO> result = controller.servers();

        assertThat(result.getData().servers().get(0).transport()).isEqualTo("stdio");
    }

    // ==================== tools 端点 ====================

    @Test
    void toolsReturnsEmptyWhenToolManagerNull() {
        // toolManager 默认为 null（@Autowired required=false，Mockito 注入了 mock 但模拟 null 场景）
        controller = new McpController();
        R<List<McpToolVO>> result = controller.tools();

        assertThat(result.getData()).isEmpty();
    }

    @Test
    void toolsReturnsOnlyMcpTools() {
        ToolDefinition mcpTool = ToolDefinition.create("mcp__echo__echo", "回显", "mcp.echo", p -> "ok");
        ToolDefinition utilTool = ToolDefinition.create("util.calculate", "计算", "util.math", p -> "0");

        when(toolManager.getAllTools()).thenReturn(List.of(mcpTool, utilTool));

        R<List<McpToolVO>> result = controller.tools();

        assertThat(result.getData()).hasSize(1);
        McpToolVO tool = result.getData().get(0);
        assertThat(tool.name()).isEqualTo("mcp__echo__echo");
        assertThat(tool.serverName()).isEqualTo("echo");
        assertThat(tool.category()).isEqualTo("mcp.echo");
    }

    @Test
    void toolsReturnsEmptyWhenNoMcpTools() {
        ToolDefinition utilTool = ToolDefinition.create("util.calculate", "计算", "util.math", p -> "0");

        when(toolManager.getAllTools()).thenReturn(List.of(utilTool));

        R<List<McpToolVO>> result = controller.tools();

        assertThat(result.getData()).isEmpty();
    }

    @Test
    void toolsExtractServerNameFromCategory() {
        ToolDefinition tool1 = ToolDefinition.create("mcp__fs__read", "读文件", "mcp.fs", p -> "");
        ToolDefinition tool2 = ToolDefinition.create("mcp__web__search", "搜索", "mcp.web", p -> "");

        when(toolManager.getAllTools()).thenReturn(List.of(tool1, tool2));

        R<List<McpToolVO>> result = controller.tools();

        assertThat(result.getData()).hasSize(2);
        assertThat(result.getData().get(0).serverName()).isEqualTo("fs");
        assertThat(result.getData().get(1).serverName()).isEqualTo("web");
    }
}
