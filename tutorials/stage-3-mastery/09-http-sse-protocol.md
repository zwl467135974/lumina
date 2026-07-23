# 09 — HTTP/SSE 协议层

> **前置要求**：已完成 [08-响应式](08-reactor-reactive.md)
> **预计阅读**：15 分钟
> **难度**：⭐⭐⭐⭐☆

---

## 面试题引入

> **"面试官：SSE 和 WebSocket 有什么区别？为什么 Lumina 的 SSE 需要 keep-alive？chunked transfer 是什么？"**

---

## 表层回答（60 分）

SSE 基于 HTTP 长连接（服务器单向推送），WebSocket 是双向全双工。chunked transfer 是 HTTP 分块传输。

---

## 深层原理（90 分）

### SSE 的 HTTP 协议

```
Response Headers:
  Content-Type: text/event-stream     ← 声明这是 SSE 流
  Cache-Control: no-cache             ← 不缓存
  Transfer-Encoding: chunked          ← 分块传输
  Connection: keep-alive              ← 长连接不关

Response Body（持续推送）:
  data: {"type":"REASONING","content":"思考中..."}\n\n
  data: {"type":"FINAL","content":"答案是..."}\n\n
  data: [DONE]\n\n
```

**关键**：每个事件用 `data: ` 前缀 + `\n\n`（两个换行）分隔。

---

## chunked transfer（分块传输）

### 问题

传统 HTTP 要先发 `Content-Length` 头告诉浏览器"总共多少字节"。但 SSE 不知道要推多少——生成多久不确定。

### 解决：chunked

```
Transfer-Encoding: chunked
```

每个 chunk 前面带长度：
```
1a\r\n                  ← 这个 chunk 26 字节
{"type":"FINAL"...}\r\n
0\r\n                   ← 长度 0 = 传输结束
\r\n
```

浏览器收到一个 chunk 就能处理，不用等全部传完。

---

## keep-alive 的作用

### Lumina 的 Vite 配置

```typescript
// 文件：vite.config.ts:24
agent: new http.Agent({ keepAlive: true })
// + configure 里强制设 Connection: keep-alive
```

### 为什么需要

Node http-proxy（Vite 代理用的）和 Reactor Netty（Spring 用的）有兼容问题：

```
没有 keep-alive:
  代理每次请求新建 TCP 连接 → Reactor Netty 发 Connection: close
  → SSE 流的后续数据被截断！

有 keep-alive:
  连接保持 → 数据持续推送 → 打字机效果正常
```

---

## SSE vs WebSocket vs 轮询

| | 轮询 | SSE | WebSocket |
|---|---|---|---|
| 方向 | 客户端→服务端 | 服务端→客户端 | 双向 |
| 协议 | HTTP | HTTP | WS（独立协议） |
| 复杂度 | 低 | 中 | 高 |
| Lumina 用 | ❌ | ✅ 流式输出 | ❌ |

**Lumina 选 SSE**：AI 回复只需"服务器→客户端"单向推送，SSE 够用且更简单。

---

## 常见追问

### Q：为什么不用原生 EventSource？

**A**：原生 EventSource 只支持 GET 且不能带 Header。Lumina 要 POST（传 task body）+ 带 Authorization Header，所以用 `@microsoft/fetch-event-source`。

### Q：SSE 连接超时怎么办？

**A**：浏览器/代理有默认超时（如 Nginx 60 秒）。需要配置 `proxy_read_timeout` 或发送心跳保持连接。

---

## 小结

| 知识点 | 一句话 |
|--------|--------|
| SSE | HTTP 长连接 + 服务器单向推送 |
| chunked | 分块传输，不用预知总长度 |
| keep-alive | 保持连接，防 SSE 被截断 |
| SSE vs WebSocket | SSE 单向简单，WebSocket 双向复杂 |

---

📝 **本篇撰写期间修正的代码**：无。
