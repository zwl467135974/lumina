# Frontend 前端服务 Dockerfile
# 多阶段构建：构建阶段 + Nginx 运行阶段

# 阶段1：构建前端
FROM node:20-alpine AS builder

WORKDIR /app

# 复制 package.json 和 package-lock.json
COPY package*.json ./

# 安装依赖
RUN npm ci --only=production

# 复制源代码
COPY . .

# 构建生产版本
RUN npm run build

# 阶段2：使用 Nginx 提供静态文件服务
FROM nginx:alpine

# 添加非root用户
RUN addgroup -S nginx && adduser -S nginx -G nginx

# 复制自定义 nginx 配置
COPY nginx.conf /etc/nginx/nginx.conf

# 从构建阶段复制构建产物
COPY --from=builder /app/dist /usr/share/nginx/html

# 修改文件权限
RUN chown -R nginx:nginx /usr/share/nginx/html

# 暴露端口
EXPOSE 80

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost/ || exit 1

# 使用非root用户运行
USER nginx

# 启动 nginx
CMD ["nginx", "-g", "daemon off;"]
