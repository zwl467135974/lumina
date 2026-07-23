# J01 — Webhook 与企业微信机器人

> **前置要求**：已完成 [模块 I](README.md)
> **预计阅读**：10 分钟
> **难度**：⭐⭐☆☆☆

---

## 这节解决什么问题

Lumina 产生的通知（预算告警、任务完成、工作流审批）怎么推到外部系统？**Webhook** 和**企业微信机器人**是两个出口。

---

## Webhook 系统

### 类比：快递柜

你不在家时，快递放到快递柜——系统不直接通知你，而是"放到指定地址"。

Webhook 就是"指定地址"——你配置一个 URL，Lumina 有事件时 POST 到这个 URL。

### 特点

- **per-user / per-category 订阅**：每个用户订阅自己关心的类别
- **HMAC-SHA256 签名**：防伪造（接收方验签）
- **连续失败自动禁用**：URL 持续返回错误 → 自动暂停推送

---

## 企业微信机器人

```java
// 发送到企微群机器人
POST https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx
{
  "msgtype": "markdown",
  "markdown": { "content": "**预算告警**\n> 当前消耗: ¥950 / ¥1000" }
}
```

### 特点

- **markdown 着色**：告警红色、成功绿色
- **4096 字节分片**：超长消息自动拆分
- **限频**：每分钟最多 20 条

---

## 小结

| 出口 | 特点 |
|------|------|
| Webhook | 通用 HTTP 推送，HMAC 签名，失败自动禁用 |
| 企业微信 | 群机器人，markdown 渲染，分片限频 |

> 🚀 [J02 — Code Interpreter →](J02-code-interpreter.md)

---

📝 **本篇撰写期间修正的代码**：无。
