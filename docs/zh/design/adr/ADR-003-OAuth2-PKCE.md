# ADR-003：OAuth2 第三方登录默认启用 PKCE（S256）

- 状态：已采纳（v3.12.1）
- 关联代码：`OAuth2LoginServiceImpl` / `OAuth2Properties.ProviderConfig`
  （business-base）

## 背景

v3.12.0 的 OAuth2 授权码流程已有 state（Redis 单次有效，防 CSRF/重放），但
没有 PKCE。授权码在回调链路（浏览器 → redirect_uri）中被截获时，攻击者可
直接用 code 换 token——PKCE 让"只有发起方"能完成交换。GitHub 自 2025-07 起
官方支持 PKCE（OAuth Apps 与 GitHub Apps 均支持），主流 OIDC IdP 早已支持，
默认开启的生态障碍已消失。

## 决策

1. `buildAuthorizeUrl` 生成 code_verifier（64 随机字节 Base64URL，86 字符，
   合法区间 43~128），S256 挑战上送授权页，verifier 本体只存本方 Redis
   （`oauth2:pkce:{state}`，与 state 同 TTL、同单次有效语义）；
2. callback 校验 state 后取用 verifier，token 交换携带 `code_verifier`；
   启用方缺失 verifier 即拒绝（fail-closed）；
3. 按 Provider 可关（`lumina.oauth2.providers.<name>.pkce=false`），
   兜底个别不支持 PKCE 的老 IdP。

## 后果

- 正向：授权码截获不再单独构成账号接管；与 state 防护正交（state 防伪造
  回调，PKCE 防码截获）。
- 代价：一次额外 Redis 读写；不支持 PKCE 的 IdP 必须显式关闭（配置错误
  表现为 token 端点报错）。
- 验证：S256 实现以 RFC 7636 Appendix B 官方向量锁定；全流程 6 个新增
  单测覆盖挑战生成/verifier 传递/缺失拒绝/按 Provider 关闭。
