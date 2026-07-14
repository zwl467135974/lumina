package io.lumina.agent.tool.mcp;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MCP (Model Context Protocol) 配置属性
 *
 * <p>从配置文件 {@code lumina.mcp.*} 读取 MCP Server 接入配置。
 * 默认不启用，需显式设置 {@code lumina.mcp.enabled=true}。
 *
 * <p>示例配置：
 * <pre>{@code
 * lumina:
 *   mcp:
 *     enabled: true
 *     tool-prefix: "mcp__"
 *     servers:
 *       - name: filesystem
 *         transport: stdio
 *         command: npx
 *         args:
 *           - "-y"
 *           - "@modelcontextprotocol/server-filesystem"
 *           - "/tmp"
 * }</pre>
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "lumina.mcp")
public class McpServerProperties {

    /**
     * 是否启用 MCP 接入（默认：false）
     *
     * <p>启用后会在应用就绪时连接所有配置的 MCP Server 并注册其工具。
     */
    private boolean enabled = false;

    /**
     * MCP Server 配置列表
     */
    private List<McpServerConfig> servers = new ArrayList<>();

    /**
     * 注册到工具管理器时的工具名前缀（默认：mcp__）
     *
     * <p>最终工具名格式：{@code {toolPrefix}{serverName}__{mcpToolName}}，
     * 如 {@code mcp__filesystem__read_file}。
     */
    private String toolPrefix = "mcp__";

    /**
     * 单个 MCP Server 的连接配置
     *
     * @author Lumina Team
     * @since 1.0.0
     */
    @Data
    public static class McpServerConfig {

        /**
         * Server 标识名称（必填）
         *
         * <p>用于工具名拼接、日志输出与客户端查找。需唯一。
         */
        private String name;

        /**
         * 传输类型：stdio | http（默认：stdio）
         *
         * <p>stdio：通过子进程方式启动 MCP Server；
         * http：通过 HTTP/SSE 连接远程 MCP Server（当前实现以 stdio 为主）。
         */
        private String transport = "stdio";

        /**
         * stdio 传输的启动命令（如 npx / node / python）
         */
        private String command;

        /**
         * stdio 传输的命令参数列表
         */
        private List<String> args;

        /**
         * 子进程环境变量（stdio 传输）
         */
        private Map<String, String> env;

        /**
         * HTTP 传输的 Server URL
         */
        private String url;

        /**
         * HTTP 传输的请求头
         */
        private Map<String, String> headers;
    }
}
