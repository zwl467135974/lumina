# Frontend 前端服务 Dockerfile
# 多阶段构建：pnpm 构建 + Nginx 运行

# 阶段1：构建前端
FROM node:20-alpine AS builder

# 安装 pnpm（钉住大版本，与 CI/本地一致；新版默认拦截依赖构建脚本）
RUN npm install -g pnpm@10

WORKDIR /app

# 复制 lock 文件利用缓存
COPY pnpm-lock.yaml package.json ./

# 安装全部依赖（含 devDependencies，build 需要 vite）
RUN pnpm install --frozen-lockfile

# 复制源代码
COPY . .

# 构建生产版本
RUN pnpm build

# 阶段2：使用 Nginx 提供静态文件服务
FROM nginx:alpine

# 复制自定义 nginx 配置
COPY nginx.conf /etc/nginx/nginx.conf

# 从构建阶段复制构建产物
COPY --from=builder /app/dist /usr/share/nginx/html

# 暴露端口
EXPOSE 80

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost/ || exit 1

# 启动 nginx
CMD ["nginx", "-g", "daemon off;"]
