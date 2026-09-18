package io.lumina.agent.util;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * 内网/保留地址判定（SSRF 防护共用谓词）
 *
 * <p>同一判定同时用于请求前的快速校验（fail-fast，给出可读错误）与
 * Apache HttpClient 的 {@code DnsResolver}（连接时校验，消除"校验后连接
 * 前再次解析"的 TOCTOU / DNS rebinding 缝隙）——两处必须用同一份逻辑，
 * 否则连接时校验会被绕过。
 *
 * <p>覆盖范围：
 * <ul>
 *   <li>环回（127/8、::1）与通配（0.0.0.0、::）地址</li>
 *   <li>IPv4 私网（10/8、172.16/12、192.168/16）与链路本地（169.254/16）</li>
 *   <li>IPv6 链路本地（fe80::/10）与唯一本地（fc00::/7，私网等价物）</li>
 *   <li>运营商级 NAT 段（100.64/10，云内网常见）</li>
 * </ul>
 *
 * @author Lumina Team
 * @since 3.12.1
 */
public final class InetAddresses {

    private InetAddresses() {
    }

    /**
     * 是否为不应作为 SSRF 目标的地址（环回/私网/链路本地/ULA/CGNAT/通配）
     */
    public static boolean isPrivateOrLocal(InetAddress address) {
        if (address == null) {
            return true;
        }
        if (address.isLoopbackAddress() || address.isAnyLocalAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        if (address instanceof Inet4Address) {
            return isCarrierGradeNat(address.getAddress());
        }
        if (address instanceof Inet6Address) {
            byte[] bytes = address.getAddress();
            // IPv4-mapped IPv6（::ffff:a.b.c.d）：JDK 通常已归一化为 Inet4Address，
            // 双保险显式判定，防止归一化差异
            if (isIpv4Mapped(bytes)) {
                return isCarrierGradeNat(java.util.Arrays.copyOfRange(bytes, 12, 16));
            }
            // 唯一本地地址 fc00::/7（ULA，IPv6 私网段）：isSiteLocalAddress 不覆盖
            return (bytes[0] & 0xfe) == 0xfc;
        }
        return false;
    }

    /** 100.64.0.0/10 运营商级 NAT（100.64~100.127） */
    private static boolean isCarrierGradeNat(byte[] ipv4) {
        return (ipv4[0] & 0xff) == 100 && (ipv4[1] & 0xff) >= 64 && (ipv4[1] & 0xff) <= 127;
    }

    private static boolean isIpv4Mapped(byte[] ipv6) {
        for (int i = 0; i < 10; i++) {
            if (ipv6[i] != 0) {
                return false;
            }
        }
        return (ipv6[10] & 0xff) == 0xff && (ipv6[11] & 0xff) == 0xff;
    }
}
