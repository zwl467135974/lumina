package io.lumina.agent.tool;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A2aClientToolProvider 单元测试（SSRF 防护与入参校验）
 *
 * @author Lumina Team
 * @since 3.12.0
 */
class A2aClientToolProviderTest {

    private final A2aClientToolProvider provider = new A2aClientToolProvider();

    @Test
    void callAgentRejectsPrivateHost() {
        String result = provider.callAgent("http://192.168.1.10/v1/a2a/agents/1", "任务", null);
        assertThat(result).startsWith("Error:").contains("私有地址");
    }

    @Test
    void callAgentRejectsLoopbackHost() {
        String result = provider.callAgent("http://127.0.0.1:8080/v1/a2a/agents/1", "任务", null);
        assertThat(result).startsWith("Error:").contains("私有地址");
    }

    @Test
    void callAgentRejectsNonHttpScheme() {
        String result = provider.callAgent("file:///etc/passwd", "任务", null);
        assertThat(result).startsWith("Error:");
    }

    @Test
    void callAgentRejectsBlankMessage() {
        String result = provider.callAgent("https://remote.example.com/v1/a2a/agents/1", "  ", null);
        assertThat(result).startsWith("Error:").contains("不能为空");
    }

    @Test
    void getAgentCardRejectsPrivateHost() {
        String result = provider.getAgentCard("http://10.0.0.5/v1/a2a/agents/1");
        assertThat(result).startsWith("Error:");
    }
}
