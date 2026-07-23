# 14 — Pinia 状态管理 + Vue Router 路由

> **前置要求**：已完成 [13-Element Plus](13-element-plus-basics.md)
> **预计阅读**：20 分钟
> **难度**：⭐⭐☆☆☆

---

## 这节解决什么问题

- **Pinia**：多个页面/组件之间怎么共享数据？（比如登录状态、用户信息、主题切换——全局共享）
- **Vue Router**：点击菜单怎么跳到对应页面？URL 和页面怎么对应？

---

## Pinia：全局状态管理

### 类比：公司公告板

每个组件有自己的数据（ref/reactive）——就像每个员工有自己的便签。但有些信息是**全员共享**的（当前登录用户、主题模式）——这些放在**公告板（Pinia store）**上。

### 怎么定义一个 Store

```typescript
// 文件：lumina-frontend/src/stores/modules/app.ts
import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAppStore = defineStore('app', () => {
  // === 状态（数据） ===
  const sidebarCollapsed = ref(false)               // 侧边栏折叠状态
  const theme = ref<'light' | 'dark'>('light')      // 主题

  // === 操作（修改数据的方法） ===
  const toggleSidebar = () => {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  const toggleTheme = () => {
    setTheme(theme.value === 'light' ? 'dark' : 'light')
  }

  const setTheme = (t: 'light' | 'dark') => {
    theme.value = t
    // 同步到 DOM（切换 <html> 的 class）
    document.documentElement.classList.toggle('dark', t === 'dark')
  }

  const initTheme = () => {
    document.documentElement.classList.toggle('dark', theme.value === 'dark')
  }

  // === 导出（外部能用的） ===
  return { sidebarCollapsed, theme, toggleSidebar, toggleTheme, setTheme, initTheme }
}, {
  // 持久化配置：刷新页面后状态不丢失
  persist: {
    key: 'lumina-app',
    storage: localStorage,                        // 存到浏览器 localStorage
    paths: ['sidebarCollapsed', 'theme']          // 只持久化这两个字段
  }
})
```

### 怎么在组件里用

```vue
<script setup lang="ts">
import { useAppStore } from '@/stores/modules/app'

const appStore = useAppStore()          // 拿到 store 实例

// 读状态（store 的属性直接用）
console.log(appStore.theme)              // 'light' 或 'dark'

// 调操作方法
appStore.toggleTheme()                   // 切换主题
appStore.toggleSidebar()                 // 折叠/展开侧边栏
</script>

<template>
  <el-button @click="appStore.toggleTheme()">
    {{ appStore.theme === 'dark' ? '切到亮色' : '切到暗色' }}
  </el-button>
</template>
```

### 持久化：刷新不丢

`persist` 配置让状态**自动存到 localStorage**。用户刷新页面，主题/侧边栏状态不丢失。

> 💡 这依赖 `pinia-plugin-persistedstate` 插件（`stores/index.ts` 里注册）。

---

## Vue Router：页面路由

### 类比：酒店房间号

URL（如 `/agent/list`）就像酒店房间号——路由器（Router）根据号码把你带到对应房间（页面组件）。

### 路由配置

```typescript
// 文件：lumina-frontend/src/router/modules/index.ts（简化）
export const agentRoutes = {
  path: '/agent',
  component: Layout,                 // 父级用 Layout（侧边栏+顶栏+内容区）
  redirect: '/agent/list',
  children: [
    {
      path: 'list',
      name: 'AgentList',
      component: () => import('@/views/agent/index.vue'),   // 懒加载
      meta: {
        title: 'Agent 管理',
        icon: 'Robot',
        requiresAuth: true,            // 需要登录
        permissions: ['agent:list']    // 需要的权限
      }
    },
    {
      path: 'detail/:id',
      name: 'AgentDetail',
      component: () => import('@/views/agent/detail.vue'),
      meta: { requiresAuth: true, permissions: ['agent:list'], keepAlive: true }
    }
  ]
}
```

### meta 元信息

每条路由的 `meta` 携带额外信息：
- `title`：页面标题（显示在标签页和面包屑）
- `requiresAuth`：是否需要登录
- `permissions`：需要什么权限（没权限不能访问）
- `keepAlive`：切走时是否缓存（回来不重新加载）

### 路由守卫：登录拦截

```typescript
// 文件：lumina-frontend/src/router/guards.ts（简化）
router.beforeEach((to, from) => {
  // 1. 开启进度条
  NProgress.start()

  // 2. 检查是否需要登录
  if (to.meta.requiresAuth && !userStore.isLoggedIn) {
    // 没登录，跳到登录页，带上原地址方便登录后跳回
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  // 3. 检查权限
  if (to.meta.permissions) {
    const hasPerm = to.meta.permissions.some(p => permissionStore.hasPermission(p))
    if (!hasPerm) return { path: '/403' }    // 无权限跳 403
  }
})

router.afterEach(() => {
  NProgress.done()    // 关闭进度条
})
```

**效果**：未登录用户访问任何页面都会被拦截到登录页；有权限要求的页面，无权限用户被拦截到 403 页。

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| Pinia Store | 全局共享状态（登录/主题/权限），刷新不丢（persist） |
| defineStore | `defineStore('id', () => { ... return {...} })` |
| Vue Router | URL ↔ 页面组件的映射 |
| 路由 meta | 携带 title/requiresAuth/permissions 等元信息 |
| 路由守卫 | beforeEach 里拦截，检查登录和权限 |

---

## 下一步

下一篇 [TypeScript + Vite](15-typescript-vite-basics.md)——类型系统和构建工具。

> 🚀 [15 — TypeScript + Vite →](15-typescript-vite-basics.md)

---

📝 **本篇撰写期间修正的代码**：无。`app.ts` store 代码规范良好，persist 配置清晰。
