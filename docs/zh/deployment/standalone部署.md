# Standalone 单体模式部署指南

> 5 分钟快速启动。standalone 模式将 base + notification + agent 三个业务模块合并为**一个进程**，
> 外部依赖只有 **MySQL + Redis 两件套**——无需 Nacos、RocketMQ、独立 Gateway 进程。

## 与微服务模式的差异

| | standalone 单体模式 | 微服务模式 |
|---|---|---|
| 进程数 | 1（lumina-standalone） | 3+（gateway / base / agent） |
| 服务发现/配置中心 | 不需要（本地 application.yml） | Nacos |
| 消息队列 | 不需要（Spring ApplicationEvent 本地降级） | RocketMQ |
| JWT 认证 | 内置 WebMVC Filter（StandaloneJwtFilter） | Gateway WebFlux Filter |
| 外部依赖 | MySQL + Redis | MySQL + Redis + Nacos + RocketMQ |
| 适用场景 | 本地体验、小规模部署、POC | 生产环境、水平扩容 |

API 路径与微服务模式完全一致（Gateway 路由均为 `StripPrefix=0` 透传），前端无需感知部署形态。

## 方式一：Docker Compose 一键启动（推荐）

### 前置条件

- Docker + Docker Compose
- 一个 LLM API Key（阿里 DashScope / 智谱 GLM / OpenAI 兼容均可）

### 启动

```bash
git clone https://github.com/zwl467135974/lumina.git
cd lumina

# 必填：LLM API Key（不设置 compose 会直接报错提示）
export LLM_API_KEY=your-api-key

# 一条命令拉起 MySQL + Redis + Lumina
docker compose -f docker-compose-standalone.yml up -d
```

首次启动会执行 Maven 构建（约几分钟），Flyway 自动建表并写入种子数据。

### 验证

```bash
# 1. 健康检查（约 60~90 秒后返回 UP）
curl http://localhost:8080/actuator/health

# 2. 登录（默认账号 admin / admin123）
curl -X POST http://localhost:8080/api/v1/base/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# 3. 用返回的 token 调业务接口
curl http://localhost:8080/api/v1/agents \
  -H "Authorization: Bearer <上一步返回的 token>"
```

### 可选环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| `LLM_API_KEY` | （必填） | LLM API Key |
| `LLM_TYPE` | `dashscope` | LLM 类型（dashscope/openai/claude/gemini/ollama 等） |
| `LLM_MODEL` | `qwen-plus` | 模型名 |
| `MYSQL_ROOT_PASSWORD` | `123456` | MySQL root 密码 |
| `REDIS_PASSWORD` | `123456` | Redis 密码 |
| `LUMINA_JWT_SECRET` | 内置开发密钥 | **生产环境必须改**（至少 32 字符） |
| `RAG_ENABLED` | `false` | RAG 知识库（需另行部署 Qdrant） |
| `MCP_ENABLED` | `false` | MCP 外部工具接入 |
| `LUMINA_TRIGGER_ENABLED` | `true` | Cron 触发器开关（v3.5+，关闭则不轮询定时任务） |
| `LUMINA_TRIGGER_POLL_MS` | `30000` | Cron 触发器轮询间隔（毫秒） |

## 方式二：本机 jar 运行

### 前置条件

- JDK 21+、Maven 3.9+
- 本机 MySQL 8.0+（建库 `lumina_dev`，utf8mb4）与 Redis 7.0+（密码 `123456`，或用环境变量覆盖）

### 构建与启动

```bash
# 构建可执行 jar
mvn -pl lumina-standalone -am package -DskipTests

# 启动（数据库/Redis 连接可用 SPRING_DATASOURCE_URL 等环境变量覆盖）
LLM_API_KEY=your-api-key java -jar lumina-standalone/target/lumina-standalone-1.0.0-SNAPSHOT.jar
```

## 前端对接

前端开发模式直接把 API 代理指到 `http://localhost:8080` 即可（与指向 Gateway 时一致）：

```bash
cd lumina-frontend
pnpm install
VITE_API_BASE_URL=http://localhost:8080 pnpm dev
```

## 实现说明（维护者向）

- **模块**：`lumina-standalone`，依赖 `lumina-business-base` + `lumina-business-agent`（notification 经 base 传递）
- **认证**：`StandaloneJwtFilter`（WebMVC OncePerRequestFilter）移植自 Gateway 的 JWT 过滤器：
  剥离伪造身份头 → 白名单放行 → JWT 校验 → Redis 黑名单 → 注入 `X-User-Id` 等可信头，
  由 base 模块的 `TenantIsolationInterceptor` 初始化 `BaseContext`
- **通知降级**：`NotificationEventPublisher` 优先发 RocketMQ，MQ 不可用时降级 Spring
  ApplicationEvent，由 `NotificationEventListener` 本地持久化 + SSE 推送
- **禁用组件**：Nacos（`spring.cloud.nacos.*.enabled=false`）、
  RocketMQ（`spring.autoconfigure.exclude` + 全部 consumer `enabled=false`）
- **已知限制**：
  - `/api/v1/auth/**` → `/api/v1/base/auth/**` 的 Gateway 重写路由在 standalone 下不存在，请使用完整路径 `/api/v1/base/auth/login`
  - 知识库文档解析在 standalone 下走同步链路（无 MQ 异步）；RAG 需自行部署 Qdrant 并设 `RAG_ENABLED=true`

## 常见问题

**Q: 启动报 "JWT 密钥未配置或仍为默认开发密钥"？**
A: `lumina.jwt.secret-key` 不能等于微服务模式的默认开发密钥。standalone 自带独立的开发默认值，
如果你显式设置了 `LUMINA_JWT_SECRET`，确保其至少 32 字符且不是默认值。

**Q: 启动日志出现 RocketMQ / Nacos 字样？**
A: 只要是 "disabled" / "excluded" 语义即为正常；standalone 不会连接二者。

**Q: 预算告警等站内通知还能收到吗？**
A: 能。无 MQ 时通知自动降级为进程内事件，站内通知与 SSE 实时推送不受影响。

## 方式三：操作脚本（本地开发推荐）

适合反复启动/停止/调试的场景。脚本路径：`scripts/standalone.sh`。

### 1. 配置环境

```bash
cp .env.standalone.example .env.standalone
# 编辑 .env.standalone，至少填 LLM_API_KEY
```

`.env.standalone` 是本地实际配置（含 key，已 gitignore，不提交）。模板见 `.env.standalone.example`。

### 2. 常用命令

```bash
./scripts/standalone.sh build     # 构建 jar（首次必跑）
./scripts/standalone.sh start     # 后台启动（日志 → logs/standalone.log）
./scripts/standalone.sh status    # 查状态 + health
./scripts/standalone.sh logs      # 实时查日志（tail -f，Ctrl+C 退出）
./scripts/standalone.sh stop      # 停止（优雅退出 + 强杀兜底）
./scripts/standalone.sh restart   # 重启
./scripts/standalone.sh clean     # 清理日志和临时文件
```

### 3. 前置依赖

| 服务 | 端口 | 必需性 | 启动命令 |
|---|---|---|---|
| MySQL 8 | 3306 | 必需 | `docker run -d -p 3306:3306 -e MYSQL_ROOT_PASSWORD=123456 -e MYSQL_DATABASE=lumina_dev mysql:8.0` |
| Redis 7 | 6379 | 必需 | `docker run -d -p 6379:6379 redis:7-alpine redis-server --requirepass 123456` |
| Qdrant | 6333 | 可选（RAG 用） | `docker run -d -p 6333:6333 qdrant/qdrant:v1.12.4` |

注意：MySQL 首次启动后要建库 `lumina_dev`（standalone 首次启动 Flyway 会自动建表，但库要预先存在）。
