# 15 — TypeScript + Vite

> **前置要求**：已完成 [14-Pinia + Router](14-pinia-router-basics.md)
> **预计阅读**：20 分钟
> **难度**：⭐⭐☆☆☆

---

## 这节解决什么问题

- **TypeScript**：JavaScript 加了类型——编译时就能发现"变量拼错了""传错参数类型"等错误
- **Vite**：前端构建工具——开发时秒级热更新，打包时优化体积

---

## TypeScript：给 JavaScript 加类型

### 类比：快递单上的收件信息

JavaScript 像没填电话的快递单——快递员（运行时）才发现地址不对。TypeScript 像填了完整信息的快递单——寄件时（编译时）就能检查格式对不对。

### 基本类型

```typescript
// 变量声明带类型
let count: number = 0
let name: string = '张三'
let active: boolean = true
let ids: number[] = [1, 2, 3]
```

### interface：定义对象结构

Lumina 前端最核心的类型文件：

```typescript
// 文件：lumina-frontend/src/types/api.ts（简化）

// 统一响应结构（后端所有接口都返回这个格式）
interface R<T> {
  code: number
  message: string
  data: T
}

// 分页结果
interface PageResult<T> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
}

// Agent 视图对象（返回给前端的）
interface AgentVO {
  id: number
  agentName: string
  agentType: string
  description?: string          // ? 表示可选
  llmConfig?: string
  tools?: string[]
}

// 创建 Agent 的 DTO（前端传给后端的）
interface CreateAgentDTO {
  agentName: string             // 必填
  agentType: string
  description?: string          // 可选
  llmConfig?: object
  tools?: string[]
}
```

### 为什么要分 VO 和 DTO？

| 类型 | 含义 | 用途 |
|------|------|------|
| **VO**（View Object） | 后端返回给前端的 | 列表展示、详情页 |
| **DTO**（Data Transfer Object） | 前端传给后端的 | 表单提交 |
| **Query** | 查询参数 | 列表搜索条件 |

分开的原因：**安全**。VO 可以脱敏（不返回 apiKey），DTO 可以限制（只允许传特定字段）。详见[第二阶 03-DTO/VO 模式](../stage-2-application/03-dto-vo-domain-pattern.md)。

### 泛型：类型参数化

```typescript
// R<T> 里的 T 就是泛型——"什么类型由调用时决定"
const res: R<AgentVO> = await api.get('/agents/1')
// res.data 的类型是 AgentVO，有自动补全

const res2: R<PageResult<AgentVO>> = await api.get('/agents')
// res2.data.list 的类型是 AgentVO[]，有自动补全
```

---

## Vite：构建工具

### 类比：即时热水壶 vs 烧水锅

- **旧工具（webpack）**：烧水锅——启动慢（等几十秒），改一行代码要等几秒才看到效果
- **Vite**：即时热水壶——启动秒级，改代码浏览器**立刻刷新**（HMR 热更新）

### Lumina 的 Vite 配置

```typescript
// 文件：lumina-frontend/vite.config.ts（简化）
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  plugins: [vue()],

  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')    // @ → src 目录的别名
    }
  },

  server: {
    port: 3000,                               // 开发服务器端口
    proxy: {
      '/api': {
        target: 'http://localhost:8080',      // API 请求转发到后端
        changeOrigin: true
      }
    }
  },

  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          'vue-vendor': ['vue', 'vue-router', 'pinia'],   // Vue 全家桶单独打包
          'element-plus': ['element-plus']                 // EP 单独打包
        }
      }
    }
  }
})
```

### 关键配置说明

| 配置 | 作用 | 为什么需要 |
|------|------|-----------|
| `alias: { '@': 'src' }` | `@/xxx` 等价于 `src/xxx` | 避免写 `../../../` 相对路径 |
| `server.port: 3000` | 前端开发端口 | 访问 `http://localhost:3000` |
| `server.proxy` | `/api` 请求转发到 8080 | 前端 3000 → 后端 8080，避免跨域 |
| `manualChunks` | 第三方库分包打包 | 首屏加载更快（浏览器缓存） |

> 💡 **proxy 是前后端联调的关键**：前端在 3000 端口，后端在 8080。如果前端直接调 8080 会有跨域问题（CORS）。Vite 的 proxy 把 `/api` 开头的请求自动转发到 8080，浏览器以为是同源的，不报跨域。

---

## 动手试试

1. **打开 `types/api.ts`**：找 `R<T>`、`PageResult<T>`、`AgentVO` 的定义
2. **打开 `vite.config.ts`**：找到 proxy 配置，理解前端请求怎么到后端
3. **在任何 `.vue` 文件里看 import**：注意 `@/` 开头的路径，理解 alias

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| TypeScript | JS + 类型，编译时检查错误 |
| interface | 定义对象结构 |
| VO / DTO | 返回给前端的 / 前端传给后端的 |
| 泛型 `<T>` | 类型参数化，`R<AgentVO>` 的 T 用时决定 |
| Vite | 秒级热更新的构建工具 |
| proxy | 开发时把 `/api` 转发到后端，解决跨域 |

---

## 下一步

下一篇 [Axios + SSE](16-axios-sse-basics.md)——HTTP 请求封装 + AI 打字机流式效果。

> 🚀 [16 — Axios + SSE →](16-axios-sse-basics.md)

---

📝 **本篇撰写期间修正的代码**：无。
