# S03 — 从一次 DNS Rebinding 攻击学会 SSRF 防护

> **前置要求**：知道 HTTP 请求的大致过程（域名会先解析成 IP）
> **预计阅读**：25 分钟
> **难度**：⭐⭐⭐⭐☆
> **对应真实代码**：`A2aClientToolProvider` / `InetAddresses`（v3.12.1 修复，[ADR-001](../../docs/zh/design/adr/ADR-001-SSRF-连接时校验.md)）

---

## 这节解决什么问题

这是一个真实故事：Lumina 的 A2A 功能让 Agent 能调用任意外部 Agent，上线时明明配了"禁止访问内网"的检查——代码 review 没发现问题，测试也过了。但攻击者可以用一次 DNS 把整个检查变成摆设。

这节课讲清楚这个攻击（DNS rebinding），以及为什么正确的修复不是"再仔细检查一遍"，而是**把检查搬到攻击发生的位置**。这是安全工程最重要的思维方式：防线要放在和攻击同一个时空。

---

## 第一层：SSRF 是什么——先讲人话版

SSRF（Server-Side Request Forgery），服务器端请求伪造。说白了：**骗服务器替你访问它自己够得着、而你够不着的东西**。

攻击者够不着什么？内网。你的服务器够得着什么？内网。

Lumina 的场景：用户可以给 Agent 提供一个外部 A2A 地址，Agent 会向这个地址发请求。如果没有任何检查，攻击者填一个内网地址：

```
https://my-lumina-agent.com/...  ← 攻击者操作的地方
        │
        │  Agent 按用户提供的 URL 发请求
        ▼
http://169.254.169.254/latest/meta-data/   ← 云服务器的元数据接口
        │
        ▼
   拿到云账号临时凭证 → 接管你的云资源
```

`169.254.169.254` 是 AWS/阿里云等云厂商给每台服务器提供的元数据接口，里面可能有数据库密码、临时凭证。它**只对内网开放**——外部扫描不到，但你的服务器一伸手就够得着。

## 第二层：第一版防护——看起来无懈可击

v3.12.0 的实现，逻辑很顺：

```java
// 校验：解析域名，逐个检查 IP 是不是内网地址
for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
    if (address.isLoopbackAddress() || address.isSiteLocalAddress() /* ... */) {
        throw new IllegalArgumentException("目标为主机私有地址，默认禁止");
    }
}
// 通过校验 → 发起 HTTP 请求
httpClient.send(request, ...);
```

能想到的都做了：解析域名、检查所有返回的 IP、环回/私网/链路本地都拒。测试也过了（用 `http://127.0.0.1`、`http://192.168.1.10` 之类的地址全被拦截）。

## 第三层：攻击——两次解析之间的缝隙

问题藏在一个不起眼的细节里：**校验时解析了一次 DNS，HTTP 客户端连接时又解析了一次**。

DNS 是攻击者可以控制的东西（只要他拥有一个域名，比如 `evil.com`）。他把域名的 DNS 记录设置成：

- **第一次查询**（校验时）：返回 `1.2.3.4`（一个正常的公网 IP）→ 检查通过 ✅
- **第二次查询**（连接时）：返回 `169.254.169.254` → 服务器连上了内网 ❌

怎么做到两次返回不同结果？把 DNS 记录的 TTL 设成 0，然后在 DNS 服务器上切换应答——或者干脆一次返回两条记录，看客户端抓到哪条。这个手法叫 **DNS rebinding（重绑定）**，2007 年就有论文，今天依然是 SSRF 防护的头号杀手。

这类漏洞有个通用名字：**TOCTOU**（Time-of-check to time-of-use）——检查的时刻和使用的时刻之间，世界变了。检查"门锁好没锁"和"走出门"之间隔了三秒，小偷就赢三秒。

> 💡 找 TOCTOU 的口诀：**你的检查和你的使用，是不是同一次"读取"？** 校验用了一次 DNS 解析，连接用了另一次——两次读取之间世界可以变，检查就作废。

## 第四层：修复——把防线搬到攻击发生的位置

修复思路不是"校验更仔细一点"（你永远快不过 TTL=0 的 DNS），而是**让校验和使用变成同一次解析**。

v3.12.1 的做法（可以对照 ADR-001 读源码）：把 HTTP 传输层从 JDK HttpClient 换成 Apache HttpClient 5，挂一个自定义 DNS 解析器：

```java
// 文件：lumina-agent-core/.../tool/A2aClientToolProvider.java（节选）
class ValidatingDnsResolver implements DnsResolver {
    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        InetAddress[] addresses = SystemDefaultDnsResolver.INSTANCE.resolve(host);
        for (InetAddress address : addresses) {
            if (InetAddresses.isPrivateOrLocal(address)) {
                throw new UnknownHostException("目标解析到私有地址，已拒绝连接（SSRF 防护）");
            }
        }
        return addresses;   // 返回的就是接下来真正用来建连的地址
    }
}
```

关键在于**执行时机**：这个 `resolve()` 是 HTTP 客户端**建立连接那一刻**调用的，返回的地址就是**接下来真正 connect 的地址**。攻击者的域名就算 TTL=0 玩出花来，rebinding 也无缝可钻——因为"校验的解析"和"使用的解析"是同一次。

配套还有两个小补丁，同样来自这次修复：

- **重定向默认不跟随**（`disableRedirectHandling`）——否则公网 URL 302 跳到内网，校验又被绕过（校验只发生在最初那个 URL）；
- **网段清单补全**——IPv6 唯一本地地址（fc00::/7，IPv6 世界的"192.168"）和运营商级 NAT 段（100.64/10）此前不在 `isSiteLocalAddress()` 的覆盖范围内。

## 第五层：举一反三——同类漏洞长什么样

TOCTOU 是个家族，认脸比记名字有用：

| 场景 | 检查 | 使用 | 缝隙 |
|---|---|---|---|
| SSRF（本篇） | 解析域名验 IP | 再次解析后连接 | 两次解析 |
| 文件上传 | 检查文件名后缀 | 移动/打开文件 | 检查后文件被替换（符号链接） |
| 权限校验 | 读 token 验权限 | 业务读到的还是那条数据吗 | IDOR（横向越权） |
| 金额校验 | 前端/接口验金额 | 支付时再次取值 | 中间被改 |

共同解法都一样：**检查和使用收敛到同一次读取、同一个事务边界、同一份不可变引用**。

## 面试官会怎么追问

**Q：为什么不用"校验后把 IP 钉住、连接时直连 IP"的方案？**
A：那是另一条正确路线（校验后拿 IP 建连，Host 头/SNI 用原域名），但对 HTTPS 会碰到证书校验问题（证书是签给域名的，直连 IP 时端点校验会失败，需要自定义 TrustManager——自己写 TLS 校验代码本身就是风险）。挂 DnsResolver 是"不碰 TLS 语义"的等价方案。

**Q：你这套防护就无懈可击了吗？**
A：不是。`allow-private-hosts=true` 是全局放行（内网联调用），粒度是实例级不是目标级；SSRF 只是 A2A 外连风险之一，还有响应内容进上下文的注入面（见 S06）。诚实的说法：这套防护消除了 rebinding 这一类绕过，不是消灭 SSRF 这个风险类。

## 自测题

1. **用自己的话（30 秒）讲清 DNS rebinding 怎么绕过"校验私网"的检查。**
   <details><summary>答案</summary>校验和连接是两次独立 DNS 解析。攻击者控制域名，校验时返回公网 IP（过关），连接时返回内网 IP（直连内网）。TTL=0 或双记录让两次解析结果不同。</details>

2. **TOCTOU 的本质是什么？修复的通用思路是什么？**
   <details><summary>答案</summary>本质：检查的时刻和使用的时刻之间，被检查的对象可以变化。通用思路：让检查与使用收敛到同一次读取/同一份不可变数据/同一事务边界——即把防线搬到攻击实际发生的位置。</details>

3. **为什么修复选择"自定义 DnsResolver"而不是"校验后直连 IP"？**
   <details><summary>答案</summary>直连 IP 会破坏 HTTPS 的证书主机名校验（证书签给域名），补救要自定义 TrustManager——手写 TLS 校验极易出错。DnsResolver 在连接时校验，语义等价且不碰 TLS。</details>

4. **`disableRedirectHandling` 防的是哪条绕过路径？**
   <details><summary>答案</summary>重定向跟随：校验只发生在最初的 URL，公网地址 302 跳转到内网地址时，后续每一跳都不再过校验。</details>

---

**下一篇**：[S04 — 双实例上线那天，任务表被谁杀了](S04-instance-isolation.md)——又是一个真实事故：代码在单实例上完美运行，加了一台机器就出鬼了。
