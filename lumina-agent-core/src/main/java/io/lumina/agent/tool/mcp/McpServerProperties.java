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
     * 全局健康检查配置
     *
     * <p>驱动 {@code McpClientRegistry#scheduledHealthCheck()} 的定时探测：
     * {@code enabled} 为总开关，{@code intervalSeconds} 为探测间隔。
     * 单个 server 可通过自身的 {@code health-check.enabled=false} 单独退出探测。
     */
    private HealthCheckConfig healthCheck = new HealthCheckConfig();

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
         * 传输类型：stdio | http | streamable-http（默认：stdio）
         *
         * <p>stdio：通过子进程方式启动 MCP Server；
         * http：通过 HTTP/SSE 连接远程 MCP Server；
         * streamable-http：通过 Streamable HTTP 连接远程 MCP Server（MCP 新版传输协议）。
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
         * HTTP/SSE/streamable-http 传输的 Server URL
         */
        private String url;

        /**
         * HTTP/SSE/streamable-http 传输的请求头（如 Authorization、X-API-Key）
         *
         * <p>value 可能含敏感凭证，日志中只允许输出 key，禁止输出 value。
         */
        private Map<String, String> headers;

        /**
         * 重连配置（仅对 http / streamable-http 传输生效）
         */
        private ReconnectConfig reconnect = new ReconnectConfig();

        /**
         * 健康检查配置（server 级开关，探测间隔由全局 {@code lumina.mcp.health-check} 控制）
         */
        private HealthCheckConfig healthCheck = new HealthCheckConfig();
    }

    /**
     * MCP Server 重连配置
     *
     * <p>连接断开（健康检查失败）后按指数退避重连：
     * 第 n 次尝试前等待 {@code backoffMs * backoffMultiplier^(n-1)} 毫秒。
     *
     * @author Lumina Team
     * @since 3.4.0
     */
    @Data
    public static class ReconnectConfig {

        /**
         * 是否启用自动重连（默认：true）
         */
        private boolean enabled = true;

        /**
         * 单次重连最大尝试次数（默认：3）
         */
        private int maxAttempts = 3;

        /**
         * 初始退避时间，毫秒（默认：2000）
         */
        private long backoffMs = 2000;

        /**
         * 退避倍数（默认：2.0）
         */
        private double backoffMultiplier = 2.0;
    }

    /**
     * MCP Server 健康检查配置
     *
     * @author Lumina Team
     * @since 3.4.0
     */
    @Data
    public static class HealthCheckConfig {

        /**
         * 是否启用健康检查（默认：true）
         */
        private boolean enabled = true;

        /**
         * 探测间隔，秒（默认：60；仅全局配置生效，server 级配置忽略此字段）
         */
        private int intervalSeconds = 60;
    }
}
