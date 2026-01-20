# Lumina Docker 部署指南

本文档介绍如何使用 Docker 和 Docker Compose 部署 Lumina 平台。

## 前置要求

- Docker 20.10+
- Docker Compose 2.0+
- 至少 4GB 可用内存
- 至少 10GB 可用磁盘空间

## 快速开始

### 1. 克隆项目

```bash
git clone https://github.com/zwl467135974/lumina.git
cd lumina
```

### 2. 配置环境变量

复制环境变量示例文件并根据需要修改：

```bash
cp .env.example .env
```

编辑 `.env` 文件，修改数据库密码、Redis密码等敏感信息。

### 3. 启动所有服务

```bash
# 构建并启动所有服务
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看服务日志
docker-compose logs -f
```

### 4. 初始化数据库

首次启动时，需要初始化数据库结构：

```bash
# 等待 MySQL 启动完成（约30秒）
sleep 30

# 执行数据库初始化脚本
docker-compose exec mysql mysql -ulumina -plumina_password lumina < docker/mysql/init/01-init.sql
```

### 5. 访问服务

- **前端界面**: http://localhost
- **API网关**: http://localhost:8080
- **Swagger文档**: http://localhost:8080/swagger-ui.html
- **Nacos控制台**: http://localhost:8848/nacos (用户名/密码: nacos/nacos)

## 服务说明

### 核心服务

| 服务名 | 容器名 | 端口 | 说明 |
|--------|--------|------|------|
| mysql | lumina-mysql | 3306 | MySQL数据库 |
| redis | lumina-redis | 6379 | Redis缓存 |
| nacos | lumina-nacos | 8848, 9848 | 服务注册与配置中心 |
| gateway | lumina-gateway | 8080 | API网关 |
| business-base | lumina-business-base | 8081 | 基础业务服务 |
| agent-service | lumina-agent-service | 8082 | Agent服务 |
| frontend | lumina-frontend | 80 | 前端界面 |

### 数据卷

| 卷名 | 说明 |
|------|------|
| mysql-data | MySQL数据持久化 |
| redis-data | Redis数据持久化 |
| nacos-data | Nacos配置持久化 |
| nacos-logs | Nacos日志 |

## 常用命令

### 启动服务

```bash
# 启动所有服务
docker-compose up -d

# 启动指定服务
docker-compose up -d gateway

# 重新构建并启动
docker-compose up -d --build
```

### 停止服务

```bash
# 停止所有服务
docker-compose down

# 停止并删除数据卷（谨慎使用）
docker-compose down -v
```

### 查看日志

```bash
# 查看所有服务日志
docker-compose logs -f

# 查看指定服务日志
docker-compose logs -f gateway

# 查看最近100行日志
docker-compose logs --tail=100 gateway
```

### 服务管理

```bash
# 重启服务
docker-compose restart gateway

# 扩展服务实例（最多3个）
docker-compose up -d --scale gateway=3

# 查看服务健康状态
docker-compose ps
```

### 进入容器

```bash
# 进入 Gateway 容器
docker-compose exec gateway sh

# 进入 MySQL 容器
docker-compose exec mysql bash

# 进入 MySQL 客户端
docker-compose exec mysql mysql -ulumina -plumina_password lumina
```

## 生产环境部署

### 1. 修改配置

编辑 `docker-compose.yml`，进行以下修改：

1. **修改端口映射**（避免使用默认端口）
2. **增加资源限制**
3. **配置外部数据卷**
4. **启用 TLS/SSL**

示例：

```yaml
services:
  gateway:
    ports:
      - "8080:8080"  # 修改为外部端口
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 2G
        reservations:
          cpus: '1'
          memory: 1G
```

### 2. 使用外部MySQL和Redis

如果使用外部数据库，修改服务配置：

```yaml
services:
  gateway:
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://external-mysql:3306/lumina
      SPRING_REDIS_HOST: external-redis
```

### 3. 配置健康检查

所有服务都已配置健康检查，可以通过以下命令查看：

```bash
docker-compose ps
```

### 4. 日志管理

日志文件存储在项目目录下的 `./logs` 文件夹中：

```
./logs/
├── gateway/
├── business-base/
├── agent-service/
└── ...
```

建议配置日志轮转：

```bash
# 创建 logrotate 配置
sudo vim /etc/logrotate.d/lumina

# 内容
/path/to/lumina/logs/*/*.log {
    daily
    rotate 7
    compress
    delaycompress
    missingok
    notifempty
    create 0644 www-data www-data
}
```

## 故障排查

### 服务无法启动

1. **检查端口占用**

```bash
# 检查端口是否被占用
netstat -tulpn | grep -E '3306|6379|8848|8080|8081|8082|80'

# 修改 docker-compose.yml 中的端口映射
```

2. **检查内存不足**

```bash
# 查看系统资源
docker stats

# 减少服务内存分配
```

3. **查看服务日志**

```bash
docker-compose logs -f [service-name]
```

### 数据库连接失败

1. **确认MySQL已启动**

```bash
docker-compose ps mysql
docker-compose logs mysql
```

2. **测试连接**

```bash
docker-compose exec mysql mysql -ulumina -plumina_password -e "SELECT 1"
```

3. **检查网络连接**

```bash
docker-compose exec gateway ping mysql
```

### Nacos 无法访问

1. **等待Nacos完全启动**

Nacos启动需要30-60秒，请耐心等待。

2. **检查Nacos日志**

```bash
docker-compose logs -f nacos
```

3. **重置Nacos**

```bash
docker-compose down -v
docker-compose up -d nacos
```

## 性能优化

### 1. JVM 参数调优

根据服务器资源调整 JVM 参数：

```yaml
environment:
  JAVA_OPTS: >-
    -Xms1g
    -Xmx1g
    -XX:+UseG1GC
    -XX:MaxGCPauseMillis=200
    -XX:+HeapDumpOnOutOfMemoryError
    -XX:HeapDumpPath=/app/logs/heapdump.hprof
```

### 2. 数据库优化

编辑 MySQL 配置：

```yaml
mysql:
  command:
    - --character-set-server=utf8mb4
    - --collation-server=utf8mb4_unicode_ci
    - --max-connections=500
    - --innodb_buffer_pool_size=1G
```

### 3. Redis 优化

```yaml
redis:
  command: >-
    redis-server
    --appendonly yes
    --requirepass lumina_redis_password
    --maxmemory 512mb
    --maxmemory-policy allkeys-lru
```

## 备份与恢复

### 数据备份

```bash
# 备份 MySQL
docker-compose exec mysql mysqldump -ulumina -plumina_password lumina > backup-$(date +%Y%m%d).sql

# 备份 Redis
docker-compose exec redis redis-cli --rdb /data/dump-$(date +%Y%m%d).rdb
```

### 数据恢复

```bash
# 恢复 MySQL
docker-compose exec -T mysql mysql -ulumina -plumina_password lumina < backup-20250120.sql
```

## 安全建议

1. **修改默认密码**
   - 修改 `.env` 文件中的所有密码
   - 使用强密码（至少16位，包含大小写字母、数字、特殊字符）

2. **限制网络访问**
   - 使用防火墙限制端口访问
   - 只暴露必要的端口（80/443）

3. **启用 TLS**
   - 配置 Nginx SSL 证书
   - 强制使用 HTTPS

4. **定期更新**
   - 定期更新 Docker 镜像
   - 更新系统安全补丁

5. **监控告警**
   - 配置 Prometheus + Grafana
   - 设置告警规则

## 参考资源

- [Docker 官方文档](https://docs.docker.com/)
- [Docker Compose 文档](https://docs.docker.com/compose/)
- [Lumina 项目文档](https://github.com/zwl467135974/lumina)

## 支持

如有问题，请提交 Issue 或联系 Lumina 团队。
