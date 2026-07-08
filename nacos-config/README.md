# Nacos 配置目录

本目录下的 YAML 文件是需要导入到 Nacos 配置中心的服务配置。

## 架构说明

```
本地 application.yml（极简）          nacos-config/*.yaml（完整业务配置）
┌──────────────────────────┐         ┌──────────────────────────────┐
│ server.port              │         │ spring.datasource (MySQL)    │
│ spring.application.name  │         │ spring.data.redis (Redis)    │
│ spring.cloud.nacos       │ ──┐     │ spring.flyway                │
│ spring.config.import     │   │     │ mybatis-plus                 │
└──────────────────────────┘   │     │ lumina.* (JWT/LLM/RAG/...)   │
                               └────→│ spring.cloud.gateway.routes  │
                                     │ logging / management         │
                                     └──────────────────────────────┘
```

**本地 `application.yml` 只保留 Nacos 连接配置**，所有业务配置通过 `spring.config.import` 从 Nacos 拉取。

## 文件说明

| 文件 | Nacos Data ID | 服务 | 内容 |
|------|---------------|------|------|
| `lumina-gateway.yaml` | `lumina-gateway.yaml` | Gateway (8080) | 网关路由 + Redis + JWT + 限流 |
| `lumina-business-base.yaml` | `lumina-business-base.yaml` | Base (8082) | MySQL + Flyway + MyBatis + JWT |
| `lumina-agent-service.yaml` | `lumina-agent-service.yaml` | Agent (8081) | MySQL + RocketMQ + LLM + RAG + 存储 |

## Nacos 配置

- **Server**: `localhost:8848`
- **Namespace**: `dev`
- **Group**: `LUMINA_GROUP`
- **Data ID**: 文件名（如 `lumina-gateway.yaml`）
- **Format**: YAML

## 导入方式

1. 启动 Nacos
2. 在 Nacos 控制台创建 namespace `dev`
3. 在 `dev` namespace 下创建配置：
   - Group: `LUMINA_GROUP`
   - Data ID: `lumina-gateway.yaml` / `lumina-business-base.yaml` / `lumina-agent-service.yaml`
   - 格式: YAML
   - 内容: 复制本目录下对应文件的内容
4. 启动服务（自动从 Nacos 拉取配置）

## 环境变量

配置中使用 `${}` 占位符，可通过环境变量覆盖：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `NACOS_SERVER_ADDR` | `localhost:8848` | Nacos 地址 |
| `NACOS_NAMESPACE` | `dev` | Nacos namespace |
| `NACOS_GROUP` | `LUMINA_GROUP` | Nacos group |
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://localhost:3306/lumina_dev...` | MySQL 连接 |
| `SPRING_DATASOURCE_PASSWORD` | `123456` | MySQL 密码 |
| `SPRING_DATA_REDIS_HOST` | `localhost` | Redis 地址 |
| `SPRING_DATA_REDIS_PASSWORD` | (空) | Redis 密码 |
| `LLM_API_KEY` | (空) | LLM API Key |
| `LLM_TYPE` | `dashscope` | LLM 提供商 |
| `LLM_MODEL` | `qwen-plus` | LLM 模型 |
| `RAG_ENABLED` | `false` | 是否启用 RAG |
