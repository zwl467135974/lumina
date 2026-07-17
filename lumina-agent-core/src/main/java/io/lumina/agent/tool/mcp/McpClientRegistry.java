package io.lumina.agent.tool.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.http.HttpRequest;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * MCP 客户端注册中心
 *
 * <p>管理所有已连接的 {@link McpSyncClient} 实例，负责按 {@link McpServerProperties}
 * 配置创建传输层、握手并维护 client 注册表。
 *
 * <p>支持三种传输类型：
 * <ul>
 *   <li>{@code stdio}：通过子进程启动 MCP Server（默认，主要场景）</li>
 *   <li>{@code http}：通过 HTTP/SSE 连接远程 MCP Server</li>
 *   <li>{@code streamable-http}：通过 Streamable HTTP 连接远程 MCP Server</li>
 * </ul>
 *
 * <p>生产运维能力：
 * <ul>
 *   <li>headers 注入：http / streamable-http 支持配置鉴权请求头（如 Authorization）</li>
 *   <li>自动重连：{@link #reconnect(String)} 指数退避重连，健康检查失败时自动触发</li>
 *   <li>健康检查：{@link #scheduledHealthCheck()} 定时 listTools 探活</li>
 *   <li>动态注册：{@link #registerServer(McpServerProperties.McpServerConfig)} /
 *       {@link #unregisterServer(String)} 运行时增删 server</li>
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
     * server 配置表：serverName -> 配置（用于 reconnect / 健康检查回查）
     *
     * <p>连接失败的 server 也会保留配置，以便后续手动 reconnect 拉起。
     */
    private final Map<String, McpServerProperties.McpServerConfig> serverConfigs = new ConcurrentHashMap<>();

    /**
     * stdio 传输类型标识
     */
    private static final String TRANSPORT_STDIO = "stdio";

    /**
     * http 传输类型标识
     */
    private static final String TRANSPORT_HTTP = "http";

    /**
     * streamable-http 传输类型标识
     */
    private static final String TRANSPORT_STREAMABLE_HTTP = "streamable-http";

    /**
     * 健康检查全局开关（默认：true；MCP 未启用时 clients 为空，探测自动空转）
     */
    @Value("${lumina.mcp.health-check.enabled:true}")
    private boolean healthCheckEnabled = true;

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
     * 初始化单个 MCP Server 客户端（容错版：失败只告警跳过）
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
            connect(server);
            log.info("MCP Server [{}] 初始化成功", name);
        } catch (Throwable e) {
            // catch Throwable：包括 NoClassDefFoundError 等 SDK 加载/链接失败
            log.warn("MCP Server [{}] 初始化失败，已跳过: {}", name, e.getMessage(), e);
        }
    }

    /**
     * 连接单个 MCP Server（严格版：失败抛异常，供 reconnect / registerServer 感知结果）
     *
     * <p>无论连接成功与否，配置都会先写入 {@link #serverConfigs}，
     * 使启动时连接失败的 server 后续仍可通过 {@link #reconnect(String)} 拉起。
     */
    private void connect(McpServerProperties.McpServerConfig server) {
        String name = server.getName();
        serverConfigs.put(name, server);
        McpClientTransport transport = createTransport(server);
        McpSyncClient client = buildClient(transport);
        client.initialize();
        clients.put(name, client);
    }

    /**
     * 由传输层构造 McpSyncClient（package-private，便于单测替换为 Mock client）
     */
    McpSyncClient buildClient(McpClientTransport transport) {
        return McpClient.sync(transport).build();
    }

    /**
     * 根据配置创建传输层（package-private，便于单测直接校验传输层构造）
     */
    McpClientTransport createTransport(McpServerProperties.McpServerConfig server) {
        String transport = server.getTransport() == null ? TRANSPORT_STDIO : server.getTransport().toLowerCase();
        switch (transport) {
            case TRANSPORT_STDIO:
                return createStdioTransport(server);
            case TRANSPORT_HTTP:
                return createHttpTransport(server);
            case TRANSPORT_STREAMABLE_HTTP:
                return createStreamableHttpTransport(server);
            default:
                throw new IllegalArgumentException("不支持的 MCP 传输类型: " + server.getTransport()
                        + "（仅支持 stdio / http / streamable-http）");
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
     *
     * <p>使用 Builder 构造（直接构造函数 HttpClientSseClientTransport(String) 已被标记 @Deprecated），
     * 配置的 headers 通过 {@code customizeRequest} 注入到所有请求（如 Authorization 鉴权头）。
     */
    private McpClientTransport createHttpTransport(McpServerProperties.McpServerConfig server) {
        String url = server.getUrl();
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("http 传输必须配置 url，server=" + server.getName());
        }
        HttpClientSseClientTransport.Builder builder = HttpClientSseClientTransport.builder(url);
        applyHeaders(builder::customizeRequest, server);
        return builder.build();
    }

    /**
     * 创建 Streamable HTTP 传输层
     *
     * <p>SDK 0.11.3 类名：{@link HttpClientStreamableHttpTransport}，
     * headers 注入方式与 HTTP/SSE 一致。
     */
    private McpClientTransport createStreamableHttpTransport(McpServerProperties.McpServerConfig server) {
        String url = server.getUrl();
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("streamable-http 传输必须配置 url，server=" + server.getName());
        }
        HttpClientStreamableHttpTransport.Builder builder = HttpClientStreamableHttpTransport.builder(url);
        applyHeaders(builder::customizeRequest, server);
        return builder.build();
    }

    /**
     * 把配置的 headers 注入到 HTTP 请求模板
     *
     * <p>headers 可能含敏感凭证（如 Bearer token），日志只输出 key，禁止输出 value。
     *
     * @param customizer 传输层 Builder 的 customizeRequest 方法引用
     * @param server     server 配置
     */
    private void applyHeaders(Consumer<Consumer<HttpRequest.Builder>> customizer,
                              McpServerProperties.McpServerConfig server) {
        Map<String, String> headers = server.getHeaders();
        if (headers == null || headers.isEmpty()) {
            return;
        }
        customizer.accept(request -> headers.forEach(request::header));
        log.info("MCP Server [{}] 已注入 {} 个请求头: {}", server.getName(), headers.size(), headers.keySet());
    }

    /**
     * 重连指定 server（指数退避，最多 maxAttempts 次）
     *
     * <p>先关闭并移除旧 client，再按 {@link McpServerProperties.ReconnectConfig} 的
     * 退避策略重新连接。方法内部使用 {@code Thread.sleep} 阻塞退避，
     * 只能在 @Scheduled 等阻塞线程中调用，禁止在 reactor 线程里调用。
     *
     * @param serverName server 名称
     * @return true 重连成功
     */
    public boolean reconnect(String serverName) {
        McpSyncClient old = clients.remove(serverName);
        if (old != null) {
            try {
                old.close();
            } catch (Throwable ignored) {
                // 旧连接已不可用，关闭失败可忽略
            }
        }
        McpServerProperties.McpServerConfig config = serverConfigs.get(serverName);
        if (config == null) {
            log.warn("MCP Server [{}] 无配置记录，无法重连", serverName);
            return false;
        }

        McpServerProperties.ReconnectConfig reconnect = config.getReconnect();
        if (reconnect == null || !reconnect.isEnabled()) {
            log.info("MCP Server [{}] 未启用自动重连，跳过", serverName);
            return false;
        }

        long backoff = reconnect.getBackoffMs();
        for (int attempt = 1; attempt <= reconnect.getMaxAttempts(); attempt++) {
            try {
                Thread.sleep(backoff);
                connect(config);
                log.info("MCP Server [{}] 第 {} 次重连成功", serverName, attempt);
                return true;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("MCP Server [{}] 重连被中断", serverName);
                return false;
            } catch (Throwable e) {
                log.warn("MCP Server [{}] 第 {} 次重连失败: {}", serverName, attempt, e.getMessage());
                backoff = (long) (backoff * reconnect.getBackoffMultiplier());
            }
        }
        log.error("MCP Server [{}] 重连失败，已达最大尝试次数 {}", serverName, reconnect.getMaxAttempts());
        return false;
    }

    /**
     * 定时健康检查：对所有已连接 server 执行 listTools 探活，失败自动重连
     *
     * <p>listTools 是轻量 RPC，适合做存活探针。探测间隔由
     * {@code lumina.mcp.health-check.interval-seconds} 控制（默认 60s，避免对远程 server 压力）。
     * server 级可通过 {@code health-check.enabled=false} 单独退出探测。
     *
     * <p>依赖启动类 @EnableScheduling 激活。
     */
    @Scheduled(fixedDelayString = "${lumina.mcp.health-check.interval-seconds:60}000")
    public void scheduledHealthCheck() {
        if (!healthCheckEnabled || clients.isEmpty()) {
            return;
        }
        for (Map.Entry<String, McpSyncClient> entry : new LinkedHashMap<>(clients).entrySet()) {
            String name = entry.getKey();
            McpServerProperties.McpServerConfig config = serverConfigs.get(name);
            if (config != null && config.getHealthCheck() != null && !config.getHealthCheck().isEnabled()) {
                continue;
            }
            if (checkHealth(name)) {
                log.debug("MCP Server [{}] 健康检查通过", name);
            } else {
                log.warn("MCP Server [{}] 健康检查失败，尝试重连", name);
                reconnect(name);
            }
        }
    }

    /**
     * 对指定 server 执行一次探活（listTools ping）
     *
     * @param name server 名称
     * @return true 探活成功；client 不存在或 RPC 失败返回 false
     */
    public boolean checkHealth(String name) {
        McpSyncClient client = clients.get(name);
        if (client == null) {
            return false;
        }
        try {
            client.listTools();
            return true;
        } catch (Throwable e) {
            log.warn("MCP Server [{}] 探活失败: {}", name, e.getMessage());
            return false;
        }
    }

    /**
     * 运行时注册一个新 MCP Server（不影响已注册的）
     *
     * @param config server 配置
     * @return true 注册成功；名称为空/重复或连接失败返回 false
     */
    public boolean registerServer(McpServerProperties.McpServerConfig config) {
        if (config == null || config.getName() == null || config.getName().trim().isEmpty()) {
            log.warn("运行时注册 MCP Server 失败：名称为空");
            return false;
        }
        String name = config.getName();
        if (clients.containsKey(name)) {
            log.warn("运行时注册 MCP Server [{}] 失败：名称已存在", name);
            return false;
        }
        try {
            connect(config);
            log.info("运行时注册 MCP Server [{}] 成功", name);
            return true;
        } catch (Throwable e) {
            log.error("运行时注册 MCP Server [{}] 失败: {}", name, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 注销 MCP Server（关闭连接并移除注册表/配置表记录）
     *
     * @param name server 名称
     * @return true 注销成功；server 不存在或关闭失败返回 false
     */
    public boolean unregisterServer(String name) {
        McpSyncClient client = clients.remove(name);
        serverConfigs.remove(name);
        if (client == null) {
            log.warn("注销 MCP Server [{}] 失败：不存在", name);
            return false;
        }
        try {
            client.close();
            log.info("MCP Server [{}] 已注销", name);
            return true;
        } catch (Throwable e) {
            log.warn("关闭 MCP Server [{}] 失败: {}", name, e.getMessage());
            return false;
        }
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
     * 获取所有已记录的 server 配置（含启动时连接失败的）
     *
     * @return 不可变配置表：serverName -> 配置
     */
    public Map<String, McpServerProperties.McpServerConfig> getServerConfigs() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(serverConfigs));
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
        serverConfigs.clear();
    }
}
