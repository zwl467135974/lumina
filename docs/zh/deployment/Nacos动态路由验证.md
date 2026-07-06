# Nacos 动态路由验证指南

> 本文档描述如何验证 Lumina Gateway 的 Nacos 动态路由和服务发现能力。

## 前置条件

- Docker + docker-compose
- Nacos 镜像（`nacos/nacos-server:v2.3.2`）
- Lumina Gateway 可构建运行

## 1. 启动 Nacos

```bash
# 拉取镜像（网络较慢时可配置 Docker 镜像加速）
docker pull nacos/nacos-server:v2.3.2

# 通过 docker-compose 启动（使用内嵌数据库模式）
docker-compose up -d nacos

# 验证 Nacos 健康
curl http://localhost:8848/nacos/v1/console/health/liveness
# 预期返回 {"status":"UP"}

# 访问控制台
# http://localhost:8848/nacos （默认账号密码：nacos/nacos）
```

## 2. 配置 Gateway 连接 Nacos

修改 `lumina-gateway/src/main/resources/application.yml`：

```yaml
spring:
  cloud:
    nacos:
      discovery:
        enabled: true
        server-addr: ${NACOS_SERVER:localhost:8848}
      config:
        enabled: true
        server-addr: ${NACOS_SERVER:localhost:8848}
        import-check:
          enabled: false
```

或在启动时设置环境变量：
```bash
export NACOS_SERVER=localhost:8848
export SPRING_CLOUD_NACOS_DISCOVERY_ENABLED=true
export SPRING_CLOUD_NACOS_CONFIG_ENABLED=true
```

## 3. 推送动态路由配置

在 Nacos 控制台创建配置：
- **Data ID**: `lumina-gateway-routes.json`
- **Group**: `DEFAULT_GROUP`
- **格式**: JSON

配置内容示例：
```json
[
  {
    "id": "lumina-agent-custom-route",
    "uri": "lb://lumina-agent-service",
    "predicates": [
      { "name": "Path", "args": { "pattern": "/api/v1/custom/**" } }
    ],
    "filters": [
      { "name": "StripPrefix", "args": { "_genkey_0": "0" } }
    ]
  }
]
```

或通过 API 推送：
```bash
curl -X POST "http://localhost:8848/nacos/v1/cs/configs" \
  -d "dataId=lumina-gateway-routes.json&group=DEFAULT_GROUP&content=$(cat routes.json | jq -c . | jq -s -r .)"
```

## 4. 验证动态路由热更新

1. 启动 Gateway（连接到 Nacos）
2. 访问已有路由（如 `/api/v1/agents`），确认正常
3. 在 Nacos 控制台修改路由配置（如新增 `/api/v1/custom/**` 路由）
4. 等待 5-10 秒（Nacos 配置推送延迟）
5. 访问新路由 `/api/v1/custom/test`，确认 Gateway 已生效（无需重启）
6. 在 Gateway 日志中应看到 `RouteDefinition` 刷新记录

## 5. 验证服务发现

1. 启动 business-agent 服务（它会注册到 Nacos）
2. 在 Nacos 控制台 → 服务管理 → 服务列表 中应看到 `lumina-agent-service`
3. 启动第二个 business-agent 实例（不同端口）
4. Nacos 应显示 2 个实例
5. 通过 Gateway 访问 `/api/v1/agents`，多次请求应负载均衡到不同实例

## 6. 验证清单

| 验证项 | 预期结果 | 状态 |
|--------|---------|------|
| Nacos 启动 | http://localhost:8848/nacos 可访问 | ☐ |
| Gateway 连接 Nacos | 日志无连接错误 | ☐ |
| 配置推送 | Nacos 控制台可见配置 | ☐ |
| 路由热更新 | 新路由 10 秒内生效 | ☐ |
| 服务注册 | 服务列表可见注册实例 | ☐ |
| 负载均衡 | 多实例轮询 | ☐ |

## 注意事项

- Nacos 2.x 需要 9848 端口（gRPC）+ 8848 端口（HTTP）
- 生产环境建议使用集群模式 + MySQL 持久化
- Gateway 需要添加 `spring-cloud-starter-alibaba-nacos-config` 和 `spring-cloud-starter-alibaba-nacos-discovery` 依赖
