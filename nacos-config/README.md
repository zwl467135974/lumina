# Nacos 配置文件

本目录存放各服务的 Nacos 配置 YAML 文件，按 Data ID = `${spring.application.name}.yaml` 命名。

## Nacos 配置参数

| 参数 | 值 |
|------|-----|
| Namespace | `dev` |
| Group | `LUMINA_GROUP` |
| File Extension | `yaml` |
| Server | `localhost:8848` |

## 配置项说明

### Gateway 专属
- 路由白名单、JWT 密钥、限流参数
- Redis 连接配置

### Business-Base 专属
- 数据库连接、Flyway 迁移、MyBatis-Plus
- JWT 密钥与过期时间

### Agent-Service 专属
- LLM 配置（模型类型、API Key、温度等）
- RAG 配置（向量存储、Embedding 提供商、Qdrant）
- 文件存储（本地/MinIO）
- RocketMQ 消息队列
- 内容审核开关

## 使用方式

### 1. 启用 Nacos 配置中心

在各个服务的 `application.yml` 中确保以下配置生效：

```yaml
spring:
  cloud:
    nacos:
      config:
        server-addr: localhost:8848
        namespace: dev
        group: LUMINA_GROUP
        file-extension: yaml
        enabled: true
```

### 2. 将配置文件导入 Nacos

通过 Nacos 控制台 `http://localhost:8848/nacos` 进入 **配置管理 → 配置列表**：

1. 选择命名空间 `dev`
2. 点击 `+` 新建配置
3. Data ID 分别填写：
   - `lumina-gateway.yaml`
   - `lumina-business-base.yaml`
   - `lumina-agent-service.yaml`
4. Group 选择 `LUMINA_GROUP`
5. 配置格式选择 `YAML`
6. 将对应文件内容粘贴到配置内容区
7. 发布

### 3. 本地开发模式

本地开发时可将 Nacos Config 关闭，改用本地 `application.yml`：

```yaml
spring:
  cloud:
    nacos:
      config:
        enabled: false
```