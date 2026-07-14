package io.lumina.agent.api.controller;

import io.lumina.agent.manager.EnhancedToolManager;
import io.lumina.agent.tool.ToolDefinition;
import io.lumina.agent.tool.mcp.McpClientRegistry;
import io.lumina.agent.tool.mcp.McpServerProperties;
import io.lumina.common.core.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MCP（Model Context Protocol）管理 Controller
 *
 * <p>提供 MCP Server 连接状态与工具列表的只读查询接口。
 * MCP 配置仍通过 Nacos/YAML 管理（lumina.mcp.enabled + lumina.mcp.servers），
 * 本接口仅展示运行时状态。
 *
 * <p>MCP 未启用时（lumina.mcp.enabled=false），相关 Bean 不存在，
 * 接口返回空列表与 enabled=false 状态。
 *
 * @author Lumina Team
 * @since 3.2.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/mcp")
public class McpController {

    @Autowired(required = false)
    private McpClientRegistry clientRegistry;

    @Autowired(required = false)
    private McpServerProperties mcpProperties;

    @Autowired(required = false)
    private EnhancedToolManager toolManager;

    /**
     * 查询 MCP 全局状态与已连接 Server 列表
     *
     * @return enabled 状态 + server 列表（含连接状态和工具数）
     */
    @GetMapping("/servers")
    public R<McpStatusVO> servers() {
        boolean enabled = mcpProperties != null && mcpProperties.isEnabled();

        if (!enabled || clientRegistry == null) {
            return R.success(new McpStatusVO(false, Collections.emptyList()));
        }

        // 已连接的 client 集合
        Map<String, ?> connectedClients = clientRegistry.getAllClientEntries();
        // 配置的 server 列表
        List<McpServerProperties.McpServerConfig> configs = mcpProperties.getServers();

        List<McpServerVO> servers = configs.stream()
                .map(config -> {
                    String name = config.getName();
                    boolean connected = connectedClients.containsKey(name);
                    int toolCount = countMcpTools(name);
                    String transport = config.getTransport() != null ? config.getTransport() : "stdio";
                    String command = config.getCommand();
                    String url = config.getUrl();
                    return new McpServerVO(name, transport, connected, toolCount, command, url);
                })
                .toList();

        return R.success(new McpStatusVO(true, servers));
    }

    /**
     * 查询所有已注册的 MCP 工具
     *
     * @return MCP 工具列表（category 以 "mcp." 开头的工具）
     */
    @GetMapping("/tools")
    public R<List<McpToolVO>> tools() {
        if (toolManager == null) {
            return R.success(Collections.emptyList());
        }

        return R.success(toolManager.getAllTools().stream()
                .filter(tool -> tool.getCategory() != null && tool.getCategory().startsWith("mcp."))
                .map(McpToolVO::from)
                .toList());
    }

    /**
     * 统计指定 MCP Server 注册的工具数
     */
    private int countMcpTools(String serverName) {
        if (toolManager == null) {
            return 0;
        }
        String category = "mcp." + serverName;
        Set<String> tools = toolManager.getToolsByCategory(category);
        return tools != null ? tools.size() : 0;
    }

    // ==================== VO 定义 ====================

    /**
     * MCP 全局状态
     */
    public record McpStatusVO(boolean enabled, List<McpServerVO> servers) {}

    /**
     * MCP Server 运行时状态
     */
    public record McpServerVO(
            String name,
            String transport,
            boolean connected,
            int toolCount,
            String command,
            String url
    ) {}

    /**
     * MCP 工具信息
     */
    public record McpToolVO(
            String name,
            String description,
            String category,
            String serverName
    ) {
        private static McpToolVO from(ToolDefinition definition) {
            String category = definition.getCategory();
            // category 格式 "mcp.{serverName}" → 提取 serverName
            String serverName = category != null && category.startsWith("mcp.")
                    ? category.substring(4)
                    : "";
            return new McpToolVO(
                    definition.getName(),
                    definition.getDescription(),
                    category,
                    serverName
            );
        }
    }
}
