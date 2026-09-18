package io.lumina.agent.util;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link InetAddresses} 内网/保留地址判定单测
 *
 * @author Lumina Team
 * @since 3.12.1
 */
class InetAddressesTest {

    @Test
    void rejectsLoopbackAndAnyLocal() throws Exception {
        assertThat(InetAddresses.isPrivateOrLocal(InetAddress.getByName("127.0.0.1"))).isTrue();
        assertThat(InetAddresses.isPrivateOrLocal(InetAddress.getByName("0.0.0.0"))).isTrue();
        assertThat(InetAddresses.isPrivateOrLocal(InetAddress.getByName("::1"))).isTrue();
    }

    @Test
    void rejectsIpv4PrivateAndLinkLocal() throws Exception {
        assertThat(InetAddresses.isPrivateOrLocal(InetAddress.getByName("10.1.2.3"))).isTrue();
        assertThat(InetAddresses.isPrivateOrLocal(InetAddress.getByName("172.16.0.1"))).isTrue();
        assertThat(InetAddresses.isPrivateOrLocal(InetAddress.getByName("192.168.1.10"))).isTrue();
        // 云元数据端点（SSRF 高价值目标）
        assertThat(InetAddresses.isPrivateOrLocal(InetAddress.getByName("169.254.169.254"))).isTrue();
    }

    @Test
    void rejectsCarrierGradeNat() throws Exception {
        // 100.64/10 边界内
        assertThat(InetAddresses.isPrivateOrLocal(InetAddress.getByName("100.64.0.1"))).isTrue();
        assertThat(InetAddresses.isPrivateOrLocal(InetAddress.getByName("100.127.255.255"))).isTrue();
        // 边界外（100.63 / 100.128 为公网段）
        assertThat(InetAddresses.isPrivateOrLocal(InetAddress.getByName("100.63.0.1"))).isFalse();
        assertThat(InetAddresses.isPrivateOrLocal(InetAddress.getByName("100.128.0.1"))).isFalse();
    }

    @Test
    void rejectsIpv6UniqueLocalAndLinkLocal() throws Exception {
        // ULA fc00::/7（fd00::/8 是其可分配半区）
        assertThat(InetAddresses.isPrivateOrLocal(InetAddress.getByName("fd00::1"))).isTrue();
        assertThat(InetAddresses.isPrivateOrLocal(InetAddress.getByName("fc00::1"))).isTrue();
        // 链路本地 fe80::/10
        assertThat(InetAddresses.isPrivateOrLocal(InetAddress.getByName("fe80::1"))).isTrue();
        // 全球单播地址放行
        assertThat(InetAddresses.isPrivateOrLocal(InetAddress.getByName("2606:4700::1111"))).isFalse();
    }

    @Test
    void rejectsIpv4MappedIpv6() throws Exception {
        // ::ffff:10.0.0.1（JDK 可能归一化为 Inet4Address，双路径都应拦截）
        assertThat(InetAddresses.isPrivateOrLocal(InetAddress.getByName("::ffff:10.0.0.1"))).isTrue();
        assertThat(InetAddresses.isPrivateOrLocal(InetAddress.getByName("::ffff:8.8.8.8"))).isFalse();
    }

    @Test
    void allowsPublicAddresses() throws Exception {
        assertThat(InetAddresses.isPrivateOrLocal(InetAddress.getByName("8.8.8.8"))).isFalse();
        assertThat(InetAddresses.isPrivateOrLocal(InetAddress.getByName("1.1.1.1"))).isFalse();
    }

    @Test
    void nullAddressTreatedAsPrivate() {
        assertThat(InetAddresses.isPrivateOrLocal(null)).isTrue();
    }
}
