# S05 — OAuth2 登录里的三个攻击者

> **前置要求**：用过"用 GitHub 登录"这类第三方登录（作为用户就行）
> **预计阅读**：25 分钟
> **难度**：⭐⭐⭐⭐☆
> **对应真实代码**：`OAuth2LoginServiceImpl`（v3.12.0 实现，v3.12.1 补 PKCE，[ADR-003](../../docs/zh/design/adr/ADR-003-OAuth2-PKCE.md)）

---

## 这节解决什么问题

"用 GitHub 登录"这五个字背后，是浏览器、你的服务器、GitHub 三方之间的四次跳转和两次密钥交换——每一段路都有攻击者埋伏。这节课用 Lumina 真实的 OAuth2 实现把整条路走一遍，认识三个攻击者，看每个参数挡的是谁。

看完你会发现：**协议里每一个"看不懂为什么存在"的参数，都是某次真实攻击的纪念碑**。

---

## 第一层：授权码流程——先把它当故事读

不用术语，先讲故事版：

1. 你点"用 GitHub 登录"；
2. 网站把你**丢到 GitHub** 的页面上（这一步离开了自己的网站！）；
3. 你在 GitHub 输入密码，点授权；
4. GitHub 把你**丢回来**，顺手在回程的 URL 上夹了一张**一次性兑换券**（authorization code）；
5. 你的服务器拿这张券**私下**找 GitHub 兑换真正的登录凭证（access token）；
6. 拿 token 取你的 GitHub 资料，登录完成。

对应到 Lumina 的代码（`OAuth2LoginServiceImpl`）：

```
buildAuthorizeUrl()          → 步骤 2：拼跳转地址（带 client_id、redirect_uri、state、code_challenge）
handleCallback(code, state)  → 步骤 4：接住兑换券
  ├─ 校验 state
  ├─ postForm(tokenUrl, code + code_verifier)  → 步骤 5：私下兑换
  └─ getBearer(userinfoUrl)                    → 步骤 6：取资料、建号/登录
```

一个问题先埋着：步骤 4 的"回程 URL"长这样：

```
https://your-site.com/oauth2/github/callback?code=xyz&state=abc
```

这张券是**明晃晃地在浏览器地址栏里旅行**的。谁都能看见。这是三个攻击者共同的舞台。

## 第二层：攻击者一号与 state——防"伪造的回程"

攻击者一号想干什么：**伪造一次回调**。他自己先发起一次 GitHub 授权，拿到一张合法的 code，然后把 `your-site.com/callback?code=那张券` 的链接发给受害者，诱导点击。如果网站不设防，会用攻击者的 GitHub 身份给受害者登录——账户绑定错乱（CSRF 攻击的变体，可用来做账户接管预埋）。

**state 的防法**：出发前（步骤 2），你的服务器生成随机 state 存进 Redis；回程时（步骤 4）校验"你带回来的 state 是不是我出发时给你的那张"。

Lumina 的实现细节值得学——**单次有效**：

```java
String stateProvider = redisCacheManager.get(stateKey);
redisCacheManager.delete(stateKey);              // 取出即删
if (stateProvider == null || !stateProvider.equals(provider)) {
    throw new BusinessException(UNAUTHORIZED, "OAuth2 state 无效或已过期");
}
```

取出即删（用一次就作废）+ 5 分钟过期。攻击者拿旧 state 重放？键已经没了。**state 是"这次出行的暗号"，防伪造**。

## 第三层：攻击者二号与 PKCE——防"被截获的券"

state 防住了伪造，防不住**截获**。回忆一下：code 在浏览器地址栏旅行，路过的地方包括——浏览器历史记录、服务器/反向代理的访问日志、任何能偷看 URL 的中间环节。攻击者二号截到了 code，抢在真正用户之前去找 GitHub 兑换 token。

一张券，凭什么只有你能兑？这就是 **PKCE**（Proof Key for Code Exchange，RFC 7636）解决的问题。戏法分三步：

1. **出发前**，你的服务器生成一个随机密语（code_verifier），**谁也不给**，自己存 Redis；
2. 出发的 URL 上带的不是密语本身，而是它的**单向哈希**（code_challenge = BASE64URL(SHA256(verifier))，即 S256 方式）；
3. **兑换时**，必须出示密语原文。GitHub 校验：SHA256(你出示的密语) == 出发时登记的挑战值？

攻击者二号截到了 code——但他从来没有密语。他去兑换，出示不出 verifier，GitHub 拒绝。**券被抢了没关系，兑奖口令在你手里**。

Lumina 的实现（v3.12.1）里有个容易忽略的 fail-closed 细节：

```java
// 启用了 PKCE 的 Provider，回调时密语缺失/过期 → 直接拒绝，绝不降级
if (requireProvider(provider).isPkce() && (verifier == null || verifier.isBlank())) {
    throw new BusinessException(UNAUTHORIZED, "OAuth2 code_verifier 无效或已过期");
}
```

顺手一个工程事实：**GitHub 直到 2025 年 7 月才官方支持 PKCE**。在那之前给 GitHub 配 PKCE 会直接失败——所以 Lumina 的配置里 PKCE 是按 Provider 开关的（默认开，老 IdP 可关）。协议支持度是现实的约束，不是所有"标准"都落地了。

## 第四层：攻击者三号与 client_secret——为什么兑换要私下进行

注意步骤 5 是**服务器对服务器**的私下请求，不在浏览器里。因为兑换请求里带着 `client_secret`（应用级密钥，等于你家门的钥匙）——它绝不能出现在任何 URL、任何前端代码里。

三个攻击者对号入座：

| 攻击者 | 手段 | 防线 | 防线本质 |
|---|---|---|---|
| 一号：伪造回调 | 拿自己的合法 code 钓你 | state（单次有效） | 我出发时埋的暗号 |
| 二号：截获券 | 偷 URL 里的 code | PKCE（S256） | 兑奖口令不在路上走 |
| 三号：冒充应用 | 假装是你来兑换 | client_secret（私下交换） | 只有我知道的钥匙 |

三道防线互不替代：state 防"假回程"，PKCE 防"真券被抢"，secret 防"假商户"。

## 第五层：一个灵魂拷问——服务器端应用还需要 PKCE 吗？

教科书说 PKCE 是给"无法保密的公开客户端"（手机 App、SPA）设计的。你的服务器明明有 client_secret（保密客户端），为什么还要 PKCE？

因为**两把锁开的是两扇门**。client_secret 防的是"别的应用拿你的 code 兑换"（攻击者自己的服务器来兑，没有你的 secret，失败）；PKCE 防的是"**截获者拿真实的 code 和真实的你的服务器竞争**"——secret 挡不住这个场景，因为兑换请求是你的服务器自己发的，secret 是对的，只是**券的来源渠道脏了**。深度防御（defense in depth）从来不是重复上锁，是给不同的攻击路径各配一把锁。

## 面试官会怎么追问

**Q：为什么 challenge 用 S256 而不是明文（plain 方式）？**
A：RFC 7636 允许 plain（verifier 原文直接当 challenge），但那要求通道全程防窃听才有意义；S256 只暴露哈希，路上任何环节偷看也还原不出 verifier。既然做，就按更强的方式做。

**Q：code 和 token 都在 URL/_HEADER 里，为什么 code 走 URL 可以接受？**
A：因为 code 是**一次性、短时效、兑换还需口令**的中间凭证——被看到也兑不了（PKCE）、兑一次就作废。token 不一样：拿到即权限。所以 code 可以走明路，token 必须只走私下通道。凭证的"可暴露性"取决于它被截后的可用性。

**Q：redirect_uri 要不要校验？**
A：要，且必须是**精确白名单**（配置里的回调地址），不能前缀匹配——否则攻击者把回调指到自己的页面截 code。Lumina 的 redirect_uri 从服务端配置构造，不接受回调参数里的外来值，这是防开放重定向的正确姿势。

## 自测题

1. **state 和 PKCE 各防什么？只装一个会怎样？**
   <details><summary>答案</summary>state 防伪造回调（CSRF 变体），PKCE 防授权码截获。只有 state：真 code 被截后攻击者可直接兑换。只有 PKCE：攻击者仍可用自己的 code+verifier 伪造回调绑定你的会话。两道防线正交。</details>

2. **PKCE 的 verifier 为什么不能放在前端生成？**
   <details><summary>答案</summary>SPA 场景 verifier 其实就在前端生成——因为公开客户端无处可藏。但 Lumina 是服务器端会话（verifier 存服务端 Redis），前端永远接触不到它，防的是 XSS 等前端失守场景。verifier 的信任级别应该和谁能完成兑换一致。</details>

3. **"取出即删"的 state 为什么比"校验后保留"安全？**
   <details><summary>答案</summary>单次有效消灭重放窗口：同一个 state 第二次出现（攻击者重放）必然失败。保留到过期的 state 给了攻击者整个 TTL 窗口做重放。</details>

4. **有 client_secret 的保密客户端为什么还要 PKCE？（一句话）**
   <details><summary>答案</summary>secret 防的是"假应用来兑换"，PKCE 防的是"真 code 在脏渠道被抢后抢先兑换"——两把锁开两扇门，深度防御不是重复上锁。</details>

---

**下一篇**：[S06 — 提示注入：不信任任何进上下文的内容](S06-injection-defense-in-depth.md)——攻击者不黑你的服务器，他给你的模型"递纸条"。
