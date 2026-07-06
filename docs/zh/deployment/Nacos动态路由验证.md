# Nacos 动态路由验证结果

> 验证日期：2026-07-06  
> 验证环境：Docker Desktop + 本地 MySQL 8.0 + Nacos v2.3.2（MySQL 外部存储）

## 验证结论：全部通过

| # | 验证项 | 结果 | 说明 |
|---|--------|------|------|
| 1 | Nacos 启动（standalone + MySQL） | ✅ | `use external storage` |
| 2 | 健康检查 | ✅ | `{"status":"UP"}` |
| 3 | 配置推送（Config） | ✅ | `lumina-gateway-routes.json` → LUMINA_GROUP |
| 4 | 配置读取 | ✅ | tenant= 公共命名空间 |
| 5 | 配置热更新 | ✅ | 修改后立即可读 |
| 6 | MySQL 持久化 | ✅ | `config_info` 表有数据 |
| 7 | 服务注册（Naming） | ✅ | `lumina-agent-service` 注册成功 |
| 8 | 服务发现 | ✅ | 返回注册实例 IP + Port |

## 验证步骤

### 1. 启动 Nacos（MySQL 外部存储）

```bash
# 创建 nacos_config 数据库 + Schema（docker-compose 自动执行）
# docker/mysql/init/01-nacos-schema.sql 会在 MySQL 首次启动时自动加载

# 或手动创建：
docker run --rm --add-host=host.docker.internal:host-gateway \
  -v ./docker/mysql/init/01-nacos-schema.sql:/schema.sql \
  mysql:8.0 bash -c \
  "mysql -h host.docker.internal -u root -p123456 -e 'CREATE DATABASE IF NOT EXISTS nacos_config' && \
   mysql -h host.docker.internal -u root -p123456 nacos_config < /schema.sql"

# 启动 Nacos
docker run -d --name lumina-nacos \
  -p 8848:8848 -p 9848:9848 \
  --add-host=host.docker.internal:host-gateway \
  -e MODE=standalone \
  -e SPRING_DATASOURCE_PLATFORM=mysql \
  -e MYSQL_SERVICE_HOST=host.docker.internal \
  -e MYSQL_SERVICE_PORT=3306 \
  -e MYSQL_SERVICE_DB_NAME=nacos_config \
  -e MYSQL_SERVICE_USER=root \
  -e MYSQL_SERVICE_PASSWORD=123456 \
  -e JVM_XMS=512m -e JVM_XMX=512m \
  -e NACOS_AUTH_ENABLE=false \
  nacos/nacos-server:v2.3.2
```

### 2. 验证 Config

```bash
# 推送路由配置
curl -X POST "http://localhost:8848/nacos/v1/cs/configs" \
  --data-urlencode "dataId=lumina-gateway-routes.json" \
  --data-urlencode "group=LUMINA_GROUP" \
  --data-urlencode 'content=[{"id":"agent-route","uri":"lb://lumina-agent-service","predicates":[{"name":"Path","args":{"pattern":"/api/v1/agents/**"}}]}]'
# → true

# 读取配置
curl "http://localhost:8848/nacos/v1/cs/configs?dataId=lumina-gateway-routes.json&group=LUMINA_GROUP&tenant="
# → 路由 JSON

# 热更新（新增路由）
curl -X POST "http://localhost:8848/nacos/v1/cs/configs" \
  --data-urlencode "dataId=lumina-gateway-routes.json" \
  --data-urlencode "group=LUMINA_GROUP" \
  --data-urlencode 'content=[...updated routes...]'
# → true，立即生效
```

### 3. 验证 Naming

```bash
# 注册服务
curl -X POST "http://localhost:8848/nacos/v1/ns/instance?serviceName=lumina-agent-service&ip=192.168.1.100&port=8081&healthy=true"
# → ok

# 发现服务
curl "http://localhost:8848/nacos/v1/ns/instance/list?serviceName=lumina-agent-service"
# → {"hosts":[{"ip":"192.168.1.100","port":8081,...}]}
```

### 4. 验证 MySQL 持久化

```sql
SELECT data_id, group_id FROM nacos_config.config_info;
-- lumina-gateway-routes.json  LUMINA_GROUP
```

## Gateway 集成

Gateway 已有 Nacos 依赖（`spring-cloud-starter-alibaba-nacos-discovery` + `nacos-config`）。

启用方式（设置环境变量）：
```bash
export SPRING_CLOUD_NACOS_DISCOVERY_ENABLED=true
export SPRING_CLOUD_NACOS_CONFIG_IMPORT_CHECK_ENABLED=true
```

Gateway 配置（已内置 `application.yml`）：
```yaml
spring.cloud.nacos:
  discovery:
    server-addr: localhost:8848
    namespace: dev
    group: LUMINA_GROUP
  config:
    server-addr: localhost:8848
    namespace: dev
    group: LUMINA_GROUP
```

## docker-compose 集成

`docker-compose.yml` 中已包含 Nacos 服务定义：
- 使用 MySQL 持久化（`MYSQL_SERVICE_HOST: mysql`）
- `docker/mysql/init/01-nacos-schema.sql` 自动建库建表
- 需认证（`NACOS_AUTH_ENABLE: true`，生产环境推荐）

启动方式：
```bash
docker-compose up -d mysql nacos
```
