package io.lumina.agent.api.controller;

import io.lumina.common.annotation.RequirePermission;
import io.lumina.agent.manager.EnhancedToolManager;
import io.lumina.agent.tool.ToolDefinition;
import io.lumina.agent.tool.mcp.McpClientRegistry;
import io.lumina.agent.tool.mcp.McpServerProperties;
import io.lumina.common.core.R;
import io.lumina.framework.audit.annotation.Audit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MCP（Model Context Protocol）管理 Controller
 *
 * <p>提供 MCP Server 连接状态与工具列表的查询接口，以及运行时动态注册/注销/
 * 重连/探活的运维接口。静态配置仍通过 Nacos/YAML 管理（lumina.mcp.enabled +
 * lumina.mcp.servers），运行时注册的 server 重启后不保留。
 *
 * <p>MCP 未启用时（lumina.mcp.enabled=false），相关 Bean 不存在，
 * 查询接口返回空列表与 enabled=false 状态，写接口返回失败。
 *
 * @author Lumina Team
 * @since 3.2.0
 */
@Slf4j
@RestController
@RequirePermission("monitor:mcp")
@RequestMapping("/api/v1/mcp")
public class McpController {

    @Autowired(required = false)
    private McpClientRegistry clientRegistry;

    @Autowired(required = false)
    private McpServerProperties mcpProperties;

    @Autowired(required = false)
    private EnhancedToolManager toolManager;

    /**
     * 查询 MCP 全局状态与 Server 列表（含静态配置与运行时注册的）
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
        // 静态配置 + 运行时注册的 server 配置（LinkedHashMap 保序去重，运行时记录覆盖静态配置）
        Map<String, McpServerProperties.McpServerConfig> configs = new LinkedHashMap<>();
        mcpProperties.getServers().forEach(config -> configs.put(config.getName(), config));
        configs.putAll(clientRegistry.getServerConfigs());

        List<McpServerVO> servers = configs.values().stream()
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
     * 运行时注册一个新 MCP Server
     *
     * <p>注册仅对当前实例生效，重启后不保留；持久化配置请走 Nacos/YAML。
     *
     * @param config server 配置（name 必填；headers 可携带鉴权头）
     * @return 注册结果
     */
    @Audit(module = "mcp", action = "CREATE")
    @PostMapping("/servers")
    public R<Boolean> registerServer(@RequestBody McpServerProperties.McpServerConfig config) {
        if (clientRegistry == null) {
            return R.fail("MCP 未启用（lumina.mcp.enabled=false）");
        }
        if (config == null || config.getName() == null || config.getName().trim().isEmpty()) {
            return R.fail("server 名称不能为空");
        }
        boolean success = clientRegistry.registerServer(config);
        return success ? R.success(true) : R.fail("MCP Server [" + config.getName() + "] 注册失败，详见服务端日志");
    }

    /**
     * 注销 MCP Server（关闭连接并移除）
     *
     * @param name server 名称
     * @return 注销结果
     */
    @Audit(module = "mcp", action = "DELETE")
    @DeleteMapping("/servers/{name}")
    public R<Boolean> unregisterServer(@PathVariable String name) {
        if (clientRegistry == null) {
            return R.fail("MCP 未启用（lumina.mcp.enabled=false）");
        }
        boolean success = clientRegistry.unregisterServer(name);
        return success ? R.success(true) : R.fail("MCP Server [" + name + "] 注销失败或不存在");
    }

    /**
     * 手动触发指定 server 重连（指数退避，同步阻塞至重连结束）
     *
     * @param name server 名称
     * @return 重连结果
     */
    @Audit(module = "mcp", action = "UPDATE")
    @PostMapping("/servers/{name}/reconnect")
    public R<Boolean> reconnectServer(@PathVariable String name) {
        if (clientRegistry == null) {
            return R.fail("MCP 未启用（lumina.mcp.enabled=false）");
        }
        boolean success = clientRegistry.reconnect(name);
        return success ? R.success(true) : R.fail("MCP Server [" + name + "] 重连失败");
    }

    /**
     * 手动探活指定 server（listTools ping）
     *
     * @param name server 名称
     * @return true 存活；false 不存在或探活失败
     */
    @GetMapping("/servers/{name}/health")
    public R<Boolean> serverHealth(@PathVariable String name) {
        if (clientRegistry == null) {
            return R.fail("MCP 未启用（lumina.mcp.enabled=false）");
        }
        return R.success(clientRegistry.checkHealth(name));
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
