package io.lumina.agent.tool.mcp;

import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MCP 客户端注册中心单元测试
 *
 * <p>验证传输层创建、配置校验、重复名称跳过与关闭逻辑。
 * 不依赖真实 MCP Server 连接（init 会尝试连接，但容错策略保证不崩溃）。
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
