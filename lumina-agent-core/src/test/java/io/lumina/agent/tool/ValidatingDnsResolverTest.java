package io.lumina.agent.tool;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link A2aClientToolProvider.ValidatingDnsResolver} 连接时 SSRF 校验单测
 *
 * <p>核心性质：解析与校验发生在同一次 resolve 内（连接时权威校验），
 * DNS rebinding 无法在校验与连接之间插入第二次解析。
 *
 * @author Lumina Team
 * @since 3.12.1
 */
class ValidatingDnsResolverTest {

    private final A2aClientToolProvider provider = new A2aClientToolProvider();

    @Test
    void resolveRejectsPrivateAddressAtConnectTime() {
        A2aClientToolProvider.ValidatingDnsResolver resolver = provider.new ValidatingDnsResolver();
        // localhost 解析到 127.0.0.1/::1，无网络依赖
        assertThatThrownBy(() -> resolver.resolve("localhost"))
                .isInstanceOf(java.net.UnknownHostException.class)
                .hasMessageContaining("SSRF");
    }

    @Test
    void resolveAllowsPrivateWhenExplicitlyEnabled() throws Exception {
        setAllowPrivateHosts(true);
        A2aClientToolProvider.ValidatingDnsResolver resolver = provider.new ValidatingDnsResolver();
        // 显式放行（内网联调）时退化为系统解析
        assertThat(resolver.resolve("localhost")).isNotEmpty();
    }

    @Test
    void preflightRejectsCarrierGradeNatEndpoint() {
        String result = provider.callAgent("http://100.64.0.1:8080/v1/a2a/agents/1", "任务", null);
        assertThat(result).startsWith("Error:").contains("私有地址");
    }

    @Test
    void preflightRejectsIpv6UniqueLocalEndpoint() {
        String result = provider.callAgent("http://[fd00::1]:8080/v1/a2a/agents/1", "任务", null);
        assertThat(result).startsWith("Error:").contains("私有地址");
    }

    private void setAllowPrivateHosts(boolean value) throws Exception {
        Field field = A2aClientToolProvider.class.getDeclaredField("allowPrivateHosts");
        field.setAccessible(true);
        field.set(provider, value);
    }
}
