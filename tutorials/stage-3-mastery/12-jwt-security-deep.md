# 12 — JWT 安全纵深

> **前置要求**：已完成 [11-分布式锁](11-distributed-lock-theory.md)
> **预计阅读**：15 分钟
> **难度**：⭐⭐⭐⭐☆

---

## 面试题引入

> **"面试官：JWT 和 Session 有什么区别？JWT 怎么防止伪造？登出后怎么让 Token 失效？"**

---

## 深层原理

### JWT 为什么防伪造

```
Payload: {"userId":1, "tenantId":0}     ← 任何人能读（Base64）
Signature: HMAC-SHA256(Payload, secret) ← 只有服务器知道 secret
```

篡改 Payload 后 Signature 不匹配——签名验证失败。**签名保证不可篡改**（不是不可读）。

### 安全要点

| 要点 | 说明 | Lumina 怎么做 |
|------|------|--------------|
| 不放敏感信息 | Payload 可解码 | 只放 userId/tenantId/roles |
| secret 足够长 | ≥256 bit | ≥32 字符 |
| 设置过期时间 | 防长期有效 | 7 天 |
| HTTPS 传输 | 防中间人 | 生产必须 HTTPS |
| 黑名单 | 登出失效 | Redis 黑名单 |
| 防伪造头 | 客户端不能伪造身份 | 过滤器剥离 X-* 头 |

### 防伪造头（Lumina 的安全设计）

```
恶意客户端: X-User-Id: 1（冒充 admin）
  ↓
JWT 过滤器: 先删除所有 X-* 头 ← 关键！
  ↓
重新注入: X-User-Id = JWT 解析出的真实 userId
```

---

## 常见追问

### Q：JWT 被偷了怎么办？

**A**：JWT 一旦签发，在过期前一直有效（无状态）。偷了就能用。防护：① HTTPS 防窃听 ② 短过期时间 ③ Redis 黑名单（登出失效）。

### Q：Refresh Token 是什么？

**A**：Access Token 短期（如 1 小时），Refresh Token 长期（如 7 天）。Access 过期后用 Refresh 换新的。好处：Access 泄露影响时间短。

---

## 小结

| 知识点 | 一句话 |
|--------|--------|
| 签名 | 防篡改（不是防读） |
| 不放敏感信息 | Payload 可解码 |
| 黑名单 | 解决登出失效 |
| 防伪造头 | 过滤器先剥离再注入 |

---

📝 **本篇撰写期间修正的代码**：无。
