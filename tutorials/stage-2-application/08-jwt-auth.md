# 08 — JWT 认证全链路

> **前置要求**：已完成 [07-多租户隔离](07-multi-tenancy.md)
> **预计阅读**：25 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

用户登录后，后续每个请求怎么证明"我是登录过的"？传统方式用 Session（服务器存），但 Session 不支持多实例部署。Lumina 用 **JWT**（JSON Web Token）——无状态认证，任何服务器都能验签。

这节讲清 JWT 从登录到校验的完整链路。

---

## JWT 是什么？先建立直觉

### 类比：游乐园手环

去游乐园买票后，工作人员给你戴上**手环**：
- 手环上有你的信息（姓名、票类型）
- 手环有**防伪标志**（不能自己造）
- 之后玩每个项目，只要出示手环就行——**不用每次回售票处验证**

**JWT 就是这个手环**：
- Token 里编码了用户信息（userId、tenantId、roles）
- Token 有**签名**防伪造（只有服务器能签发）
- 每个请求带上 Token，任何服务器都能验签——**不用查 Session**

---

## JWT 的三段结构

```
eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjF9.aBcDeF...
└────── Header ─────┘└──── Payload ────┘└─ Signature ─┘
```

| 段 | 内容 | 类比 |
|----|------|------|
| **Header** | 算法类型（HS256） | 手环的材质 |
| **Payload** | 用户信息（userId、tenantId、过期时间） | 手环上的文字 |
| **Signature** | 签名（防伪造） | 防伪标志 |

> ⚠️ Payload 是 **Base64 编码**（不是加密）——任何人都能解码看到内容。所以**不要放敏感信息**（如密码）。签名只防篡改，不防偷看。

---

## 登录：签发 Token

```java
// 简化示意（AuthController/AuthService 里）
public String login(String username, String password) {
    // 1. 查用户、验密码
    User user = userMapper.selectByUsername(username);
    if (!passwordEncoder.matches(password, user.getPassword())) {
        throw new BusinessException(ErrorCode.PASSWORD_ERROR);
    }

    // 2. 生成 JWT Token
    String token = Jwts.builder()
        .subject(String.valueOf(user.getUserId()))
        .claim("tenantId", user.getTenantId())
        .claim("roles", user.getRoles())
        .claim("permissions", user.getPermissions())
        .expiration(new Date(System.currentTimeMillis() + 7 * 24 * 3600 * 1000))  // 7 天过期
        .signWith(secretKey)         // ← 签名
        .compact();

    return token;
}
```

前端拿到 Token 后存到 localStorage：
```typescript
// 文件：lumina-frontend/src/utils/auth.ts
export function setToken(token: string) {
  localStorage.setItem('token', token)
}
```

---

## 每次请求：带上 Token

```typescript
// 文件：lumina-frontend/src/api/request.ts
service.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`  // ← 每次请求自动带
  }
  return config
})
```

---

## 后端校验：JWT 过滤器

### standalone 模式：StandaloneJwtFilter

```java
// 文件：lumina-standalone/.../filter/StandaloneJwtFilter.java
@Override
protected void doFilterInternal(HttpServletRequest request, ...) {
    // ① 剥离客户端伪造的身份头（安全！）
    IdentityHeaderRequestWrapper wrapped = new IdentityHeaderRequestWrapper(request);

    // ② 白名单放行（登录、健康检查等）
    if (isWhitelisted(request)) {
        chain.doFilter(wrapped, response);
        return;
    }

    // ③ 提取 Token
    String token = extractToken(request);    // 从 Authorization: Bearer xxx 提取
    if (token == null) {
        sendUnauthorized(response);
        return;
    }

    // ④ 校验签名 + 过期时间
    if (!jwtUtil.validateToken(token)) {
        sendUnauthorized(response);
        return;
    }

    // ⑤ 检查 Redis 黑名单（登出后的 Token）
    if (isTokenBlacklisted(token)) {
        sendUnauthorized(response);
        return;
    }

    // ⑥ 解析用户信息，注入可信头
    LoginUser loginUser = jwtUtil.parseTokenToLoginUser(token);
    wrapped.setIdentityHeader("X-User-Id", String.valueOf(loginUser.getUserId()));
    wrapped.setIdentityHeader("X-Tenant-Id", String.valueOf(loginUser.getTenantId()));
    wrapped.setIdentityHeader("X-Roles", loginUser.getRoles());
    wrapped.setIdentityHeader("X-Permissions", loginUser.getPermissions());

    chain.doFilter(wrapped, response);    // 放行
}
```

### 为什么先"剥离伪造头"？

```java
// ① 剥离伪造的身份头
IdentityHeaderRequestWrapper wrapped = new IdentityHeaderRequestWrapper(request);
```

如果没有这一步，恶意客户端可以自己在请求头里加 `X-User-Id: 1` 冒充管理员。**过滤器先删掉所有 X-* 头**，再注入自己校验过的可信值——保证身份信息**唯一来源**是 JWT。

---

## 登出：Token 黑名单

JWT 是无状态的——签发后 7 天内一直有效。用户登出后怎么让 Token 失效？

**答案**：Redis 黑名单。

```java
// 登出时
public void logout(String token) {
    // 把 Token 加入 Redis 黑名单，TTL = Token 剩余有效期
    redisCacheManager.addToBlacklist(token, remainingTime);
}

// JWT 过滤器校验时（第 ⑤ 步）
if (isTokenBlacklisted(token)) {
    sendUnauthorized(response);    // 在黑名单里 → 拒绝
    return;
}
```

---

## JWT 的全链路总结

```
用户登录
  ↓
验证账号密码
  ↓
签发 JWT Token（含 userId/tenantId/roles，签名保护）
  ↓
前端存到 localStorage
  ↓ ↓ ↓ 后续每个请求 ↓ ↓ ↓
  ↓
前端 Axios 拦截器自动加 Authorization: Bearer xxx
  ↓
后端 JWT 过滤器：
  ① 剥离伪造头
  ② 白名单检查
  ③ Token 格式校验
  ④ 签名 + 过期验证
  ⑤ Redis 黑名单检查
  ⑥ 注入可信身份头（X-Tenant-Id 等）
  ↓
租户拦截器：从头读身份 → 写入 BaseContext
  ↓
Controller → Service → Mapper（全程从 BaseContext 取身份）
```

---

## 为什么不用 Session？

| 特性 | Session | JWT |
|------|---------|-----|
| 存储位置 | 服务器内存 | 客户端（Token 自带信息） |
| 多实例 | ❌ 需要共享 Session（复杂） | ✅ 任何服务器都能验签 |
| 扩展性 | 差（内存限制） | 好（无状态） |
| 登出 | 删 Session 即可 | 需要 Redis 黑名单 |

Lumina 支持多实例部署（standalone 或微服务），所以用 JWT。

---

## 动手试试

1. **打开 `StandaloneJwtFilter.java`**：找到"剥离伪造头"和"注入可信头"的代码
2. **在浏览器 F12 → Network**：看任意请求的 Request Headers，找到 `Authorization: Bearer xxx`
3. **去 jwt.io 网站**：把 Token 粘进去解码，看 Payload 里的 userId/tenantId
4. **登出后重新用旧 Token 请求**：应该返回 401（黑名单生效）

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| JWT | 无状态 Token，自带用户信息 + 签名防伪造 |
| 三段结构 | Header.Payload.Signature |
| 登录签发 | 验密码 → 生成 Token（含身份 + 过期 + 签名） |
| 请求校验 | 过滤器校验签名/过期/黑名单 → 注入身份头 |
| 防伪造头 | 过滤器先剥离 X-* 头，再注入可信值 |
| 登出黑名单 | Redis 存登出的 Token，TTL = 剩余有效期 |

---

## 下一步

后 8 篇（01-08）讲完了 Lumina 的核心理念。接下来进入**最有价值的实战篇**：

下一篇 [实战：后端模块开发](09-build-a-feature-backend.md)——从零实现一个完整后端模块。

> 🚀 [09 — 实战：后端开发 →](09-build-a-feature-backend.md)

---

## 自测题

1. **JWT 的 Payload 是加密的吗？能放密码吗？**
   <details><summary>答案</summary>不是加密，是 Base64 编码——任何人都能解码。绝不能放密码等敏感信息。签名只防篡改，不防偷看。</details>

2. **为什么 JWT 过滤器要"先剥离客户端伪造的 X-* 头"？**
   <details><summary>答案</summary>如果不剥离，恶意客户端可以自己加 X-User-Id: 1 冒充管理员。过滤器先删除所有身份头，再注入自己 JWT 校验过的可信值，保证身份信息唯一来源。</details>

3. **用户登出后，JWT Token 还在有效期，怎么让它失效？**
   <details><summary>答案</summary>加入 Redis 黑名单。JWT 是无状态的（签发后一直有效），登出时把 Token 存入 Redis 黑名单，过滤器校验时检查黑名单。</details>

4. **JWT 和 Session 比，最大的优势是什么？**
   <details><summary>答案</summary>无状态——Token 自带用户信息，任何服务器都能验签，天然支持多实例部署。Session 需要服务器共享状态，多实例很复杂。</details>

---

📝 **本篇撰写期间修正的代码**：无。
