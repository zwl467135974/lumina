package io.lumina.agent.tool.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MCP 客户端注册中心单元测试
 *
 * <p>验证传输层创建、headers 注入、配置校验、重复名称跳过、重连退避、
 * 健康检查、运行时动态注册/注销与关闭逻辑。
 * 通过 {@link TestableRegistry} 覆写 {@code buildClient} 注入 Mock client，
 * 不产生真实网络/进程 IO。
 *
 * @author Lumina Team
 * @since 3.2.0
 */
class McpClientRegistryTest {

    private McpClientRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new McpClientRegistry();
    }

    /**
     * 可测试版 Registry：覆写 buildClient 返回预置的 Mock client
     *
     * <p>默认返回全新 Mock（initialize/listTools 均无异常）；
     * 可通过 {@link #enqueueClient} 预置特定行为的 client（按 FIFO 消费）。
     */
    private static class TestableRegistry extends McpClientRegistry {

        private final Deque<McpSyncClient> pendingClients = new ArrayDeque<>();
        private final AtomicInteger buildCount = new AtomicInteger();

        void enqueueClient(McpSyncClient client) {
            pendingClients.addLast(client);
        }

        @Override
        McpSyncClient buildClient(McpClientTransport transport) {
            buildCount.incrementAndGet();
            McpSyncClient next = pendingClients.pollFirst();
            return next != null ? next : mock(McpSyncClient.class);
        }
    }

    /**
     * 构造一个 stdio 配置（buildClient 被 Mock 后不会真正拉起子进程）
     */
    private static McpServerProperties.McpServerConfig stdioConfig(String name) {
        McpServerProperties.McpServerConfig config = new McpServerProperties.McpServerConfig();
        config.setName(name);
        config.setTransport("stdio");
        config.setCommand("echo");
        // 测试用极小退避，避免拖慢用例
        config.getReconnect().setBackoffMs(1);
        config.getReconnect().setBackoffMultiplier(1.0);
        return config;
    }

    /**
     * 反射提取传输层内部的请求模板 Builder，构造出 HttpRequest 以断言 headers
     */
    private static HttpRequest templateRequest(McpClientTransport transport) throws Exception {
        Field field = transport.getClass().getDeclaredField("requestBuilder");
        field.setAccessible(true);
        HttpRequest.Builder requestBuilder = (HttpRequest.Builder) field.get(transport);
        return requestBuilder.copy().uri(URI.create("http://localhost/probe")).GET().build();
    }

    // ==================== init 容错 ====================

    @Test
    void initWithNullServersDoesNothing() {
        // given: servers 为 null
        McpServerProperties props = new McpServerProperties();
        props.setServers(null);

        // when
        registry.init(props);

        // then: 无 client 注册
        assertThat(registry.getAllClients()).isEmpty();
    }

    @Test
    void initWithEmptyServersDoesNothing() {
        // given
        McpServerProperties props = new McpServerProperties();

        // when
        registry.init(props);

        // then
        assertThat(registry.getAllClients()).isEmpty();
    }

    @Test
    void initWithInvalidCommandSkipsServer() {
        // given: stdio 传输但 command 为空
        McpServerProperties props = new McpServerProperties();
        McpServerProperties.McpServerConfig server = new McpServerProperties.McpServerConfig();
        server.setName("bad-server");
        server.setTransport("stdio");
        server.setCommand("");  // 空 command
        props.setServers(List.of(server));

        // when: init 会尝试创建 transport 失败，容错跳过
        registry.init(props);

        // then: client 未注册（不崩溃）
        assertThat(registry.getAllClients()).isEmpty();
    }

    @Test
    void initWithInvalidTransportTypeSkipsServer() {
        // given: 不支持的传输类型
        McpServerProperties props = new McpServerProperties();
        McpServerProperties.McpServerConfig server = new McpServerProperties.McpServerConfig();
        server.setName("bad-transport");
        server.setTransport("grpc");  // 不支持
        props.setServers(List.of(server));

        // when
        registry.init(props);

        // then
        assertThat(registry.getAllClients()).isEmpty();
    }

    @Test
    void initWithEmptyNameSkipped() {
        // given: server 名称为空
        McpServerProperties props = new McpServerProperties();
        McpServerProperties.McpServerConfig server = new McpServerProperties.McpServerConfig();
        server.setName("");  // 空名称
        props.setServers(List.of(server));

        // when
        registry.init(props);

        // then
        assertThat(registry.getAllClients()).isEmpty();
    }

    // ==================== 传输层创建与 headers 注入 ====================

    @Test
    void headersInjectedIntoHttpTransport() throws Exception {
        // given: http 传输 + 鉴权 headers
        McpServerProperties.McpServerConfig server = new McpServerProperties.McpServerConfig();
        server.setName("remote-http");
        server.setTransport("http");
        server.setUrl("http://localhost:18080");
        server.setHeaders(Map.of("Authorization", "Bearer test-token", "X-API-Key", "test-key"));

        // when
        McpClientTransport transport = registry.createTransport(server);

        // then: 传输层类型正确，headers 已写入请求模板
        assertThat(transport).isInstanceOf(HttpClientSseClientTransport.class);
        HttpRequest request = templateRequest(transport);
        assertThat(request.headers().firstValue("Authorization")).contains("Bearer test-token");
        assertThat(request.headers().firstValue("X-API-Key")).contains("test-key");
    }

    @Test
    void headersInjectedIntoStreamableHttpTransport() throws Exception {
        // given: streamable-http 传输 + 鉴权 headers
        McpServerProperties.McpServerConfig server = new McpServerProperties.McpServerConfig();
        server.setName("remote-streamable");
        server.setTransport("streamable-http");
        server.setUrl("http://localhost:18080/mcp");
        server.setHeaders(Map.of("Authorization", "Bearer stream-token"));

        // when
        McpClientTransport transport = registry.createTransport(server);

        // then
        assertThat(transport).isInstanceOf(HttpClientStreamableHttpTransport.class);
        HttpRequest request = templateRequest(transport);
        assertThat(request.headers().firstValue("Authorization")).contains("Bearer stream-token");
    }

    @Test
    void streamableHttpTransportWithoutUrlThrows() {
        // given: streamable-http 传输但缺 url
        McpServerProperties props = new McpServerProperties();
        McpServerProperties.McpServerConfig server = new McpServerProperties.McpServerConfig();
        server.setName("no-url");
        server.setTransport("streamable-http");
        props.setServers(List.of(server));

        // when: init 容错跳过
        registry.init(props);

        // then
        assertThat(registry.getAllClients()).isEmpty();
    }

    @Test
    void stdioTransportCreatedWithoutHeaders() {
        // given: stdio 传输（headers 不适用）
        McpServerProperties.McpServerConfig server = stdioConfig("local-stdio");

        // when
        McpClientTransport transport = registry.createTransport(server);

        // then
        assertThat(transport).isInstanceOf(StdioClientTransport.class);
    }

    // ==================== 运行时动态注册/注销 ====================

    @Test
    void registerServerAtRuntimeWorks() {
        // given
        TestableRegistry testable = new TestableRegistry();

        // when
        boolean success = testable.registerServer(stdioConfig("runtime-server"));

        // then: client 与配置均已登记
        assertThat(success).isTrue();
        assertThat(testable.getClient("runtime-server")).isNotNull();
        assertThat(testable.getServerConfigs()).containsKey("runtime-server");
    }

    @Test
    void registerServerWithDuplicateNameFails() {
        // given: 已注册同名 server
        TestableRegistry testable = new TestableRegistry();
        testable.registerServer(stdioConfig("dup"));

        // when
        boolean success = testable.registerServer(stdioConfig("dup"));

        // then
        assertThat(success).isFalse();
    }

    @Test
    void registerServerWithBlankNameFails() {
        // when + then
        assertThat(registry.registerServer(stdioConfig("  "))).isFalse();
        assertThat(registry.registerServer(null)).isFalse();
    }

    @Test
    void unregisterServerClosesClient() {
        // given: 注册一个 Mock client
        TestableRegistry testable = new TestableRegistry();
        McpSyncClient client = mock(McpSyncClient.class);
        testable.enqueueClient(client);
        testable.registerServer(stdioConfig("to-remove"));

        // when
        boolean success = testable.unregisterServer("to-remove");

        // then: close 被调用，注册表与配置表均清空
        assertThat(success).isTrue();
        verify(client).close();
        assertThat(testable.getClient("to-remove")).isNull();
        assertThat(testable.getServerConfigs()).doesNotContainKey("to-remove");
    }

    @Test
    void unregisterUnknownServerReturnsFalse() {
        assertThat(registry.unregisterServer("nonexistent")).isFalse();
    }

    // ==================== 重连 ====================

    @Test
    void reconnectRetriesOnFailure() {
        // given: 注册成功后，后续 buildClient 返回 initialize 必失败的 client
        TestableRegistry testable = new TestableRegistry();
        McpServerProperties.McpServerConfig config = stdioConfig("flaky");
        config.getReconnect().setMaxAttempts(3);
        testable.registerServer(config);
        int buildsAfterRegister = testable.buildCount.get();
        for (int i = 0; i < 3; i++) {
            McpSyncClient failing = mock(McpSyncClient.class);
            when(failing.initialize()).thenThrow(new RuntimeException("connect refused"));
            testable.enqueueClient(failing);
        }

        // when
        boolean success = testable.reconnect("flaky");

        // then: 尝试 maxAttempts 次后失败
        assertThat(success).isFalse();
        assertThat(testable.buildCount.get() - buildsAfterRegister).isEqualTo(3);
        assertThat(testable.getClient("flaky")).isNull();
    }

    @Test
    void reconnectSucceedsAndReplacesClient() {
        // given
        TestableRegistry testable = new TestableRegistry();
        McpSyncClient oldClient = mock(McpSyncClient.class);
        testable.enqueueClient(oldClient);
        testable.registerServer(stdioConfig("healthy"));

        // when: 重连（新 client 默认 initialize 成功）
        boolean success = testable.reconnect("healthy");

        // then: 旧 client 被关闭，新 client 上位
        assertThat(success).isTrue();
        verify(oldClient).close();
        assertThat(testable.getClient("healthy")).isNotNull().isNotSameAs(oldClient);
    }

    @Test
    void reconnectDisabledReturnsFalse() {
        // given: 关闭自动重连
        TestableRegistry testable = new TestableRegistry();
        McpServerProperties.McpServerConfig config = stdioConfig("no-reconnect");
        config.getReconnect().setEnabled(false);
        testable.registerServer(config);
        int buildsAfterRegister = testable.buildCount.get();

        // when
        boolean success = testable.reconnect("no-reconnect");

        // then: 不做任何尝试
        assertThat(success).isFalse();
        assertThat(testable.buildCount.get()).isEqualTo(buildsAfterRegister);
    }

    @Test
    void reconnectUnknownServerReturnsFalse() {
        assertThat(registry.reconnect("nonexistent")).isFalse();
    }

    // ==================== 健康检查 ====================

    @Test
    void healthCheckMarksDeadServerAndReconnects() {
        // given: 已连接 client 的 listTools 抛异常（探活失败）
        TestableRegistry testable = new TestableRegistry();
        McpSyncClient deadClient = mock(McpSyncClient.class);
        when(deadClient.listTools()).thenThrow(new RuntimeException("connection reset"));
        testable.enqueueClient(deadClient);
        testable.registerServer(stdioConfig("dying"));

        // when: 定时健康检查触发
        testable.scheduledHealthCheck();

        // then: 探活失败触发重连，旧 client 被关闭并替换
        verify(deadClient).listTools();
        verify(deadClient).close();
        assertThat(testable.getClient("dying")).isNotNull().isNotSameAs(deadClient);
    }

    @Test
    void healthCheckPassesForHealthyServer() {
        // given: listTools 正常
        TestableRegistry testable = new TestableRegistry();
        McpSyncClient healthyClient = mock(McpSyncClient.class);
        testable.enqueueClient(healthyClient);
        testable.registerServer(stdioConfig("healthy"));

        // when
        testable.scheduledHealthCheck();

        // then: 不触发重连，client 不变
        verify(healthyClient, times(1)).listTools();
        assertThat(testable.getClient("healthy")).isSameAs(healthyClient);
    }

    @Test
    void healthCheckSkipsServerWithHealthCheckDisabled() {
        // given: server 级关闭健康检查
        TestableRegistry testable = new TestableRegistry();
        McpSyncClient client = mock(McpSyncClient.class);
        doThrow(new RuntimeException("should not be probed")).when(client).listTools();
        testable.enqueueClient(client);
        McpServerProperties.McpServerConfig config = stdioConfig("opt-out");
        config.getHealthCheck().setEnabled(false);
        testable.registerServer(config);

        // when
        testable.scheduledHealthCheck();

        // then: 未探测、未重连
        verify(client, times(0)).listTools();
        assertThat(testable.getClient("opt-out")).isSameAs(client);
    }

    @Test
    void checkHealthReturnsFalseForUnknownServer() {
        assertThat(registry.checkHealth("nonexistent")).isFalse();
    }

    // ==================== 注册表查询 ====================

    @Test
    void getClientReturnsNullForUnknownName() {
        assertThat(registry.getClient("nonexistent")).isNull();
    }

    @Test
    void getAllClientsReturnsImmutableCollection() {
        // given: 空 registry
        // when
        var clients = registry.getAllClients();

        // then: 返回的是不可变集合（修改会抛异常）
        assertThat(clients).isEmpty();
    }

    @Test
    void getAllClientEntriesReturnsImmutableMap() {
        // when
        var entries = registry.getAllClientEntries();

        // then
        assertThat(entries).isEmpty();
    }

    // ==================== close 容错 ====================

    @Test
    void closeWithEmptyRegistryDoesNothing() {
        // given: 空 registry
        // when + then: 不崩溃
        registry.close();
    }

    @Test
    void closeCanBeCalledMultipleTimes() {
        // given: 空 registry
        // when: 多次调用 close
        registry.close();
        registry.close();
        // then: 不崩溃（无异常抛出即通过）
    }
}
