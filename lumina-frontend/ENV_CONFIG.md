# 环境变量配置说明

## 配置文件

前端项目使用 Vite 的环境变量机制，支持以下配置文件：

- `.env.development` - 开发环境配置
- `.env.production` - 生产环境配置
- `.env.local` - 本地覆盖配置（会被 git 忽略）

## 必需的环境变量

### VITE_API_BASE_URL
API 基础地址

- 开发环境：`http://localhost:8080/api`
- 生产环境：`/api`（相对路径）

### VITE_APP_TITLE
应用标题

- 默认值：`Lumina AI Agent Platform`

## 配置示例

### 开发环境 (.env.development)
```bash
VITE_API_BASE_URL=http://localhost:8080/api
VITE_APP_TITLE=Lumina AI Agent Platform
```

### 生产环境 (.env.production)
```bash
VITE_API_BASE_URL=/api
VITE_APP_TITLE=Lumina AI Agent Platform
```

## 注意事项

1. 所有环境变量必须以 `VITE_` 开头，否则不会被 Vite 暴露给客户端代码
2. 修改环境变量后需要重启开发服务器
3. 生产环境变量会在构建时被替换，不会暴露给客户端
4. `.env.local` 文件会被 git 忽略，可用于本地特殊配置

## 使用方式

在代码中使用环境变量：

```typescript
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL
const appTitle = import.meta.env.VITE_APP_TITLE
```

