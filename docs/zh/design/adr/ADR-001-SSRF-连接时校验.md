# ADR-001：A2A 客户端 SSRF 校验放在连接时（DnsResolver）

- 状态：已采纳（v3.12.1）
- 关联代码：`A2aClientToolProvider` / `InetAddresses`（agent-core）

## 背景

A2A 客户端（`a2a.callAgent` / `a2a.getAgentCard`）向模型提供的任意 URL 发起请求，
必须防 SSRF。v3.12.0 的实现是"请求前解析域名校验 + HTTP 客户端连接"两步——
校验与连接是**两次独立的 DNS 解析**，存在 TOCTOU 缝隙：攻击者控制权威 DNS 且
TTL=0 时，校验时返回公网 IP、连接时返回内网 IP（DNS rebinding），防护被整体绕过。
此外原判定未覆盖 IPv6 唯一本地地址（fc00::/7）与运营商级 NAT 段（100.64/10）。

## 决策

1. 引入 Apache HttpClient 5 作为该 Provider 的传输层，自定义
   `ValidatingDnsResolver` 挂在连接管理器上——**解析、校验、建连使用同一次
   解析结果**，rebinding 在构造上无窗口；
2. 网段判定收敛为 `InetAddresses.isPrivateOrLocal()` 单一谓词，请求前快速校验
   （fail-fast、可读错误）与连接时权威校验共用同一份逻辑——两处逻辑分裂
   等于没有连接时校验；
3. 重定向默认不跟随（`disableRedirectHandling`），消除"校验后 302 跳私网"路径；
4. `allow-private-hosts=true` 内网联调放行同时作用于两层。

## 后果

- 正向：TOCTOU 消除；ULA/CGNAT/IPv4-mapped IPv6 补齐；Agent Card 内容同样受
  出口安检约束（见 `ExternalContentSanitizer`）。
- 代价：agent-core 新增 httpclient5 依赖（Spring Boot BOM 管版本）；
  JDK HttpClient 不支持自定义 DNS 解析是换传输层的直接原因。
- 遗留：放行私网配置是全局开关，粒度到"实例"而非"目标"；如需按目标放行，
  未来应引入白名单注册制。
