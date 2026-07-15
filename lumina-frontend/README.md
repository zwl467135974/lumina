# Lumina Frontend

<div align="center">

**Lumina AI Agent Platform Frontend**

基于 Vue 3 + TypeScript + Element Plus 的现代化前端应用

[![Vue](https://img.shields.io/badge/Vue-3.4+-brightgreen)](https://vuejs.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.3+-blue)](https://www.typescriptlang.org/)
[![Vite](https://img.shields.io/badge/Vite-5.0+-yellow)](https://vitejs.dev/)
[![Element Plus](https://img.shields.io/badge/Element_Plus-2.5+-409EFF)](https://element-plus.org/)

</div>

---

## 项目简介

Lumina 前端是基于 Vue 3 + TypeScript + Vite 构建的现代化企业级前端应用，与 Lumina 后端框架配合使用，提供完整的 AI Agent 管理平台功能。

### 核心特性

- **Vue 3 Composition API** - 使用最新的组合式 API 开发
- **TypeScript** - 全面类型安全，提升代码质量
- **Vite** - 快速的构建工具，优秀的开发体验
- **Element Plus** - 成熟的 Vue 3 UI 组件库
- **Pinia** - Vue 3 官方推荐的状态管理库
- **Vue Router** - 路由管理和导航守卫
- **Axios** - HTTP 请求封装，统一错误处理

---

## 技术栈

| 分类 | 技术 | 版本 | 说明 |
|------|------|------|------|
| **核心框架** | Vue | 3.4+ | 渐进式 JavaScript 框架 |
| | TypeScript | 5.3+ | JavaScript 类型超集 |
| | Vite | 5.0+ | 新一代前端构建工具 |
| **UI 组件库** | Element Plus | 2.5+ | Vue 3 组件库 |
| | @element-plus/icons-vue | 2.3+ | Element Plus 图标库 |
| **状态管理** | Pinia | 2.1+ | Vue 3 状态管理 |
| **路由管理** | Vue Router | 4.2+ | Vue 3 官方路由 |
| **HTTP 客户端** | Axios | 1.6+ | HTTP 请求库 |
| **工具库** | dayjs | 1.11+ | 轻量级日期处理 |
| | nprogress | 0.2+ | 页面加载进度条 |

---

## 项目结构

```
lumina-frontend/
├── .env.development            # 开发环境变量
├── .env.production             # 生产环境变量
├── index.html                  # HTML 入口
├── package.json                # 项目依赖
├── tsconfig.json               # TypeScript 配置
├── vite.config.ts              # Vite 配置
│
├── public/                     # 静态资源
│
├── src/                        # 源代码
│   ├── api/                    # API 接口
│   │   ├── modules/            # 按模块划分的 API
│   │   ├── request.ts          # Axios 封装
│   │   └── types.ts            # API 类型
│   │
│   ├── assets/                 # 静态资源
│   │   ├── images/             # 图片
│   │   ├── icons/              # 图标
│   │   └── styles/             # 全局样式
│   │
│   ├── components/             # 公共组件
│   │   ├── common/             # 通用组件
│   │   └── business/           # 业务组件
│   │
│   ├── composables/            # 组合式函数
│   │   └── useTable.ts         # 表格逻辑封装
│   │
│   ├── layouts/                # 布局组件
│   │   ├── DefaultLayout.vue   # 默认布局
│   │   └── components/         # 布局子组件
│   │
│   ├── router/                 # 路由配置
│   │   ├── modules/            # 路由模块
│   │   ├── guards.ts           # 路由守卫
│   │   └── index.ts            # 路由入口
│   │
│   ├── stores/                 # 状态管理
│   │   ├── modules/            # 状态模块
│   │   └── index.ts            # Store 入口
│   │
│   ├── types/                  # 类型定义
│   │   ├── api.ts              # API 类型
│   │   ├── common.ts           # 通用类型
│   │   └── router.ts           # 路由类型
│   │
│   ├── utils/                  # 工具函数
│   │   ├── auth.ts             # 认证工具
│   │   ├── format.ts           # 格式化工具
│   │   ├── storage.ts          # 存储工具
│   │   └── index.ts            # 工具入口
│   │
│   ├── views/                  # 页面组件
│   │   ├── agent/              # Agent 页面
│   │   ├── user/               # 用户页面
│   │   ├── system/             # 系统页面
│   │   └── login/              # 登录页面
│   │
│   ├── App.vue                 # 根组件
│   ├── main.ts                 # 应用入口
│   └── env.d.ts                # 环境变量类型
│
└── docs/                       # 项目文档（可选）
```

---

## 快速开始

### 环境要求

- **Node.js**: 20.0+ (推荐使用 LTS 版本)
- **包管理器**: pnpm 8.0+ (推荐) 或 npm 9.0+

### 安装依赖

```bash
# 使用 pnpm (推荐)
pnpm install

# 或使用 npm
npm install
```

### 开发

```bash
# 启动开发服务器
pnpm dev

# 访问 http://localhost:3000
```

### 构建

```bash
# 生产环境构建
pnpm build

# 预览构建结果
pnpm preview
```

### 代码检查

```bash
# ESLint 检查
pnpm lint

# Prettier 格式化
pnpm format

# Stylelint 检查
pnpm stylelint
```

---

## 核心功能

### 1. HTTP 请求封装

统一的 Axios 封装，包含：
- 请求拦截器（自动添加 Token）
- 响应拦截器（统一错误处理）
- 401 自动跳转登录

详见：`src/api/request.ts`

### 2. 路由管理

基于 Vue Router 的路由管理，包含：
- 路由懒加载
- 路由守卫（认证检查）
- 动态路由
- 面包屑导航

详见：`src/router/index.ts`

### 3. 状态管理

基于 Pinia 的状态管理，包含：
- 用户状态（登录、用户信息）
- 应用状态（侧边栏、主题）

详见：`src/stores/modules/`

### 4. 组合式函数

可复用的组合式函数，包含：
- `useTable` - 表格数据加载和分页
- `useForm` - 表单验证和提交
- `usePagination` - 分页逻辑

详见：`src/composables/`

---

## 开发规范

### 组件开发

1. **使用 `<script setup>` 语法**
2. **使用 TypeScript 定义 Props 和 Emits**
3. **组件文件使用 PascalCase 命名**
4. **使用 `<style scoped>` 避免样式污染**

### API 调用

1. **所有 API 调用统一通过 `api/` 目录**
2. **使用 TypeScript 定义请求和响应类型**
3. **统一错误处理在 request.ts 中完成**

### 命名规范

- **组件文件**: PascalCase，如 `UserProfile.vue`
- **工具文件**: camelCase，如 `formatDate.ts`
- **变量/函数**: camelCase，如 `userName`、`getUserInfo`
- **常量**: UPPER_SNAKE_CASE，如 `API_BASE_URL`

---

## 浏览器支持

- Chrome >= 90
- Firefox >= 88
- Edge >= 90
- Safari >= 14

---

## 常见问题

### 1. 依赖安装失败

**问题**: pnpm install 失败
**解决方案**:
```bash
# 清除缓存后重新安装
pnpm store prune
pnpm install
```

### 2. 开发服务器启动失败

**问题**: 端口被占用
**解决方案**:
```bash
# 修改 vite.config.ts 中的端口配置
server: {
  port: 3001  # 修改为其他端口
}
```

### 3. 类型错误

**问题**: TypeScript 类型检查错误
**解决方案**:
- 确保所有类型定义正确
- 使用 `type` 或 `interface` 定义类型
- 检查 `tsconfig.json` 配置

---

## 相关文档

- [前端架构设计](../docs/zh/architecture/前端架构设计.md)
- [前端开发指南](../docs/zh/guides/前端开发指南.md)

---

## 已实现功能

- ✅ 单元测试（Vitest，103 个用例）
- ✅ 国际化支持（Vue I18n，350+ key，中英文切换）
- ✅ 主题切换（Luminous 暗色主题，130 CSS 变量）
- ✅ 路由懒加载 + 组件懒加载
- ✅ SSE 流式对话（@microsoft/fetch-event-source）
- ✅ 多模态上传（LumUploader 组件，支持图片/PDF/Word）
- ✅ 工作流可视化设计器（@vue-flow）
- ✅ MCP 监控页面（Server 连接状态 + 工具列表）
- ✅ Dashboard 首页 + 审计日志页

---

## 许可证

本项目采用 Apache License 2.0 许可证。详见 [LICENSE](LICENSE) 文件。

---

**Lumina Frontend** - 让 AI Agent 管理更简单 🚀
