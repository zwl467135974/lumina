package io.lumina.agent.tool.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 客户端注册中心
 *
 * <p>管理所有已连接的 {@link McpSyncClient} 实例，负责按 {@link McpServerProperties}
 * 配置创建传输层、握手并维护 client 注册表。
 *
 * <p>支持两种传输类型：
 * <ul>
 *   <li>{@code stdio}：通过子进程启动 MCP Server（默认，主要场景）</li>
 *   <li>{@code http}：通过 HTTP/SSE 连接远程 MCP Server</li>
 * </ul>
 *
 * <p>容错策略：单个 server 的连接/握手失败只 {@code log.warn} 跳过，不影响其它 server。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class McpClientRegistry {

    /**
     * client 注册表：serverName -> McpSyncClient
     */
    private final Map<String, McpSyncClient> clients = new ConcurrentHashMap<>();

    /**
     * stdio 传输类型标识
     */
    private static final String TRANSPORT_STDIO = "stdio";

    /**
     * http 传输类型标识
     */
    private static final String TRANSPORT_HTTP = "http";

    /**
     * 初始化所有配置的 MCP 客户端
     *
     * <p>遍历 {@code props.servers()}，依次创建传输层、构造 {@link McpSyncClient}、
     * 执行 {@code initialize()} 握手。单个 server 失败不影响其它。
     *
     * @param props MCP 配置
     */
    public void init(McpServerProperties props) {
        List<McpServerProperties.McpServerConfig> servers = props.getServers();
        if (servers == null || servers.isEmpty()) {
            log.info("未配置 MCP Server，跳过客户端初始化");
            return;
        }

        log.info("开始初始化 MCP 客户端，共 {} 个 server", servers.size());
        for (McpServerProperties.McpServerConfig server : servers) {
            initOne(server);
        }
        log.info("MCP 客户端初始化完成，成功连接 {}/{} 个 server", clients.size(), servers.size());
    }

    /**
     * 初始化单个 MCP Server 客户端
     */
    private void initOne(McpServerProperties.McpServerConfig server) {
        String name = server.getName();
        if (name == null || name.trim().isEmpty()) {
            log.warn("跳过配置：MCP Server 名称为空");
            return;
        }
        if (clients.containsKey(name)) {
            log.warn("跳过重复的 MCP Server 名称: {}", name);
            return;
        }

        try {
            McpClientTransport transport = createTransport(server);
            McpSyncClient client = McpClient.sync(transport).build();
            client.initialize();
            clients.put(name, client);
            log.info("MCP Server [{}] 初始化成功", name);
        } catch (Throwable e) {
            // catch Throwable：包括 NoClassDefFoundError 等 SDK 加载/链接失败
            log.warn("MCP Server [{}] 初始化失败，已跳过: {}", name, e.getMessage(), e);
        }
    }

    /**
     * 根据配置创建传输层
     */
    private McpClientTransport createTransport(McpServerProperties.McpServerConfig server) {
        String transport = server.getTransport() == null ? TRANSPORT_STDIO : server.getTransport().toLowerCase();
        switch (transport) {
            case TRANSPORT_STDIO:
                return createStdioTransport(server);
            case TRANSPORT_HTTP:
                return createHttpTransport(server);
            default:
                throw new IllegalArgumentException("不支持的 MCP 传输类型: " + server.getTransport()
                        + "（仅支持 stdio / http）");
        }
    }

    /**
     * 创建 stdio 传输层
     */
    private McpClientTransport createStdioTransport(McpServerProperties.McpServerConfig server) {
        String command = server.getCommand();
        if (command == null || command.trim().isEmpty()) {
            throw new IllegalArgumentException("stdio 传输必须配置 command，server=" + server.getName());
        }
        ServerParameters.Builder builder = ServerParameters.builder(command);
        List<String> args = server.getArgs();
        if (args != null && !args.isEmpty()) {
            builder.args(args);
        }
        Map<String, String> env = server.getEnv();
        if (env != null && !env.isEmpty()) {
            builder.env(env);
        }
        return new StdioClientTransport(builder.build());
    }

    /**
     * 创建 HTTP/SSE 传输层
     */
    private McpClientTransport createHttpTransport(McpServerProperties.McpServerConfig server) {
        String url = server.getUrl();
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("http 传输必须配置 url，server=" + server.getName());
        }
        // 使用 Builder 构造（直接构造函数 HttpClientSseClientTransport(String) 已被标记 @Deprecated）
        // headers 暂不在基础构造中注入（SDK 的 Builder 支持 request 定制，可在后续按需扩展）
        return HttpClientSseClientTransport.builder(url).build();
    }

    /**
     * 获取指定名称的 client
     *
     * @param name server 名称
     * @return client，不存在返回 null
     */
    public McpSyncClient getClient(String name) {
        return clients.get(name);
    }

    /**
     * 获取所有已连接的 client（按注册顺序）
     *
     * @return 不可变 client 集合
     */
    public Collection<McpSyncClient> getAllClients() {
        return Collections.unmodifiableCollection(new LinkedHashMap<>(clients).values());
    }

    /**
     * 获取所有已连接的 client 及其 server 名称
     *
     * @return 不可变 entry 集合
     */
    public Map<String, McpSyncClient> getAllClientEntries() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(clients));
    }

    /**
     * 关闭所有 client
     *
     * <p>stdio 传输会留下子进程，必须显式关闭。单个 client 关闭失败不影响其它。
     */
    @PreDestroy
    public void close() {
        if (clients.isEmpty()) {
            return;
        }
        log.info("正在关闭 {} 个 MCP 客户端", clients.size());
        for (Map.Entry<String, McpSyncClient> entry : clients.entrySet()) {
            String name = entry.getKey();
            McpSyncClient client = entry.getValue();
            try {
                client.close();
                log.info("MCP Server [{}] 客户端已关闭", name);
            } catch (Throwable e) {
                log.warn("关闭 MCP Server [{}] 客户端失败: {}", name, e.getMessage(), e);
            }
        }
        clients.clear();
    }
}
