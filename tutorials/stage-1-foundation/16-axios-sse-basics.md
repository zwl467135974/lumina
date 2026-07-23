# 16 — Axios 封装 + SSE 流式通信

> **前置要求**：已完成 [15-TypeScript + Vite](15-typescript-vite-basics.md)
> **预计阅读**：25 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

前端要和后端通信——发 HTTP 请求拿数据。而且 Lumina 是 AI 平台，AI 回复用**流式输出**（打字机效果），这需要特殊处理。

这节讲清：
1. **Axios 封装**——统一处理 token 注入、错误提示、401 跳登录
2. **SSE 流式通信**——AI 打字机效果怎么实现

---

## Axios：HTTP 请求封装

### 类比：公司的收发室

你不直接去邮局寄信——交给公司的**收发室**，它统一帮你贴邮票、登记、分发。如果邮递员说"地址错了"，收发室统一通知你。

**Axios 拦截器就是收发室**：
- **请求拦截器**（发信前）：自动贴"邮票"（注入 Token）
- **响应拦截器**（收信后）：检查"有没有退信"（错误处理）

### 在 Lumina 里长啥样

```typescript
// 文件：lumina-frontend/src/api/request.ts

// 1. 创建 Axios 实例（配置基础信息）
const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,    // API 基础地址
  timeout: 30000,                                 // 超时 30 秒
  headers: { 'Content-Type': 'application/json;charset=UTF-8' }
})

// 2. 请求拦截器：发请求前自动加 Token
service.interceptors.request.use((config) => {
  const token = getToken()                        // 从 localStorage 取 token
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`   // ← 自动注入
  }
  return config
})

// 3. 响应拦截器：收到响应后统一处理
service.interceptors.response.use((response) => {
  const res = response.data                        // 剥离 AxiosResponse 外壳

  if (res.code === 200) {
    return res                                     // 成功，直接返回业务数据
  } else {
    ElMessage.error(res.msg || '请求失败')          // 业务错误，弹提示

    if (res.code === 401) {
      removeToken()                                // Token 过期，清除
      window.location.href = '/login'              // 跳登录
    }
    return Promise.reject(new Error(res.msg))
  }
}, (error) => {
  // HTTP 错误（非 200）
  switch (error.response?.status) {
    case 401: ElMessage.error('未授权，请重新登录'); break
    case 403: ElMessage.error('拒绝访问'); break
    case 500: ElMessage.error('服务器内部错误'); break
  }
})
```

### 好处

**所有请求自动获得**：
- ✅ Token 自动注入（不用每个请求手动加 header）
- ✅ 统一错误处理（不用每个请求 try-catch）
- ✅ 401 自动跳登录
- ✅ 剥离外层 AxiosResponse，直接返回业务数据

### 怎么用

```typescript
// 文件：lumina-frontend/src/api/modules/agent.ts
import { http } from '@/api/request'
import type { R, AgentVO, PageResult } from '@/types/api'

// 泛型化的请求方法
export function listAgents(params): Promise<R<PageResult<AgentVO>>> {
  return http.get('/api/v1/agents', { params })
}

export function createAgent(data): Promise<R<AgentVO>> {
  return http.post('/api/v1/agents', data)
}
```

在组件里：
```typescript
const res = await listAgents({ pageNum: 1, pageSize: 10 })
// res.data.list 就是 AgentVO[]，有类型提示
```

---

## SSE：AI 打字机效果

### 问题

AI 生成回答需要时间（几秒到几十秒）。如果等整段话生成完再返回，用户盯着空白页面干等——体验很差。

### 解决方案：SSE（Server-Sent Events）

**让服务器"边生成边推送"**——生成一个字就推一个字，前端逐字显示，就像打字机。

```
传统模式：
用户提问 → [等待 10 秒] → 整段回答一次性返回

SSE 模式：
用户提问 → "你"（0.5秒）→ "好"（0.5秒）→ "！"（0.5秒）→ ... 逐字显示
```

### 为什么不用 WebSocket？

| 技术 | 方向 | 适合场景 |
|------|------|----------|
| 普通 HTTP | 一问一答 | 普通接口 |
| WebSocket | 双向实时 | 聊天室、游戏 |
| **SSE** | **服务器→客户端单向** | **AI 流式回复、通知推送** |

SSE 更轻量——基于 HTTP，不需要额外协议，AI 回复只需要"服务器→用户"的单向推送，SSE 足够。

### 为什么不用原生 EventSource？

浏览器自带 `EventSource` API，但它有两个限制：
1. **只能 GET 请求**——Lumina 的 AI 对话是 POST（要传 body）
2. **不能自定义 Header**——无法带 `Authorization: Bearer xxx`

所以 Lumina 用 `@microsoft/fetch-event-source` 库，它支持 POST + 自定义 Header。

### 在 Lumina 里长啥样

```typescript
// 文件：lumina-frontend/src/api/modules/agent.ts（简化）
import { fetchEventSource } from '@microsoft/fetch-event-source'

export async function streamExecuteAgent(
  agentId: number,
  task: string,
  callbacks: {
    onMessage: (chunk: StreamChunk) => void    // 每收到一块就回调
    onError: (err: Error) => void
    onClose: () => void
  }
) {
  const controller = new AbortController()       // 用于中断

  await fetchEventSource(`/api/v1/agents/${agentId}/execute/stream?task=${task}`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${getToken()}`,
      'Accept': 'text/event-stream'
    },
    signal: controller.signal,                   // 中断信号

    onmessage(ev) {
      // 每收到一个 SSE 事件（data: {...}），解析并回调
      const chunk = JSON.parse(ev.data) as StreamChunk
      callbacks.onMessage(chunk)
    },

    onerror(err) {
      callbacks.onError(err)
      throw err      // 抛出以停止重试
    },

    onclose() {
      callbacks.onClose()
    }
  })

  return controller    // 返回控制器，调用方可以 controller.abort() 中断
}
```

### 前端怎么渲染

```typescript
// 文件：lumina-frontend/src/components/agent/AgentChat.vue（简化）

// 三块文本区域：推理过程、工具调用、最终回复
const reasoningText = ref('')    // 推理过程
const actingText = ref('')       // 工具调用过程
const finalText = ref('')        // 最终回答

// 处理每个流块
function handleChunk(chunk: StreamChunk) {
  switch (chunk.type) {
    case 'REASONING_CHUNK':      // 推理片段 → 追加到推理区
      reasoningText.value += chunk.content
      break
    case 'ACTING_CHUNK':         // 行动片段 → 追加到行动区
      actingText.value += chunk.content
      break
    case 'FINAL':                // 最终结果 → 追加到回答区
      finalText.value += chunk.content
      break
    case 'RAG_SOURCES':          // RAG 引用来源
      // 显示引用折叠面板
      break
  }
}

// 发送流式请求
async function sendStream() {
  const controller = await streamExecuteAgent(agentId, userInput, {
    onMessage: handleChunk,      // ← 每块回调这里
    onClose: () => { loading.value = false }
  })
}

// 用户点"停止"
function abort() {
  controller?.abort()            // 中断流
}
```

**效果**：用户发消息后，界面实时显示"AI 正在思考..."→推理过程→工具调用→最终回答，逐段流式更新。

---

## Vite proxy 对 SSE 的特殊配置

```typescript
// vite.config.ts
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
      // ↓ SSE 需要这个配置，否则代理会缓冲导致流式效果失效
      agent: new http.Agent({ keepAlive: true })
    }
  }
}
```

> ⚠️ **坑**：如果不配 `keepAlive`，Vite 代理可能缓冲 SSE 流，导致"等很久一次性蹦出来"而不是"逐字显示"。Lumina 已经配好了。

---

## 动手试试

1. **打开 `api/request.ts`**：找到请求拦截器（注入 Token）和响应拦截器（错误处理）
2. **打开 `api/modules/agent.ts`**：找到 `streamExecuteAgent` 函数，理解 fetchEventSource 用法
3. **打开 `AgentChat.vue`**：找到 `handleChunk` 函数，看 switch-case 怎么分发不同类型的流块

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| Axios 拦截器 | 请求拦截器加 Token，响应拦截器处理错误 |
| 统一封装 | 一个 `http.get/post` 泛型方法，所有请求复用 |
| SSE | 服务器→客户端单向推送，AI 打字机效果 |
| fetch-event-source | 支持 POST + 自定义 Header 的 SSE 客户端 |
| StreamChunk | 每个流块有 type（推理/行动/最终），前端按 type 分流渲染 |

---

## 下一步

最后一篇 [跑起项目](17-run-the-project.md)——把整个项目在本地跑起来，完成第一阶！

> 🚀 [17 — 跑起项目 →](17-run-the-project.md)

---

## 自测题

1. **Axios 请求拦截器的作用是什么？**
   <details><summary>答案</summary>在请求发出前统一处理——Lumina 用它自动注入 Authorization: Bearer token，不用每个请求手动加。</details>

2. **为什么 AI 对话用 SSE 而不是普通 HTTP？**
   <details><summary>答案</summary>AI 生成需要时间。普通 HTTP 要等整段生成完才返回，用户干等。SSE 让服务器边生成边推送，前端逐字显示打字机效果，体验更好。</details>

3. **为什么用 `@microsoft/fetch-event-source` 而不是原生 EventSource？**
   <details><summary>答案</summary>原生 EventSource 只支持 GET 且不能自定义 Header。AI 对话要 POST（传 task body）且要带 Authorization Header，原生 API 做不到。</details>

4. **StreamChunk 的 type 有哪几种？各对应什么显示区域？**
   <details><summary>答案</summary>REASONING_CHUNK（推理过程）、ACTING_CHUNK（工具调用过程）、FINAL（最终回答）、RAG_SOURCES（引用来源）。前端按 type 分流到不同文本区域。</details>

---

📝 **本篇撰写期间修正的代码**：无。
