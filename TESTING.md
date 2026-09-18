# Lumina 测试指南

> **v3.12 基线**：自动化测试体系覆盖后端 981 @Test + 前端 106 用例
> （2026-09-18 全模块本地实测全绿：common 28 / framework 50 / gateway 24 /
> agent-core 389 / business-base 84 / business-agent 353 / notification 33）。
> 集成测试只需 MySQL + Redis 两个本地服务，无需 Nacos / RocketMQ / Docker。
> v3.12 新增覆盖：SKILL.md 开放标准（解析/体检/导入导出）、A2A 双端与 SSRF
> 连接时校验、语音、知识飞轮、OAuth2 PKCE、工具使用分析、大输入分治、
> 分享中心、任务对账实例隔离。

---

## 环境要求（最低要求）

### 必须运行的服务

| 服务 | 地址 | 默认密码 | 用途 |
|------|------|---------|------|
| **MySQL 8.0** | localhost:3306 | root / 123456 | 数据持久化（库名 `lumina_dev`） |
| **Redis** | localhost:6379 | 123456 | 缓存 / 权限快照 / 在线用户 |

### 不需要的服务（test profile 已禁用）

| 服务 | 状态 | 原因 |
|------|------|------|
| Nacos | 已禁用 | `spring.cloud.nacos.discovery.enabled=false` + `config.enabled=false` |
| RocketMQ | 已禁用 | `spring.autoconfigure.exclude` 排除 `RocketMQAutoConfiguration` |
| Qdrant | 不涉及 | RAG 相关测试未纳入集成测试范围 |
| Docker 服务 | 不涉及 | 无依赖 |

> Nacos 客户端的 gRPC 心跳线程可能仍尝试连接 localhost:9848 并打 ERROR 日志，
> 这是 Nacos SDK 的已知行为，**不影响测试结果**，可忽略。

### 数据库准备

首次运行需确保 `lumina_dev` 数据库存在（Flyway 自动建表）：

```sql
CREATE DATABASE IF NOT EXISTS lumina_dev DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

---

## 运行测试

### 单模块运行（推荐）

```bash
# 后端各模块独立运行（全绿，无跨模块冲突）
mvn test -pl lumina-common
mvn test -pl lumina-agent-core
mvn test -pl lumina-modules/lumina-business-base
mvn test -pl lumina-modules/lumina-business-agent
mvn test -pl lumina-modules/lumina-business-notification

# 前端
cd lumina-frontend && npx vitest run
```

### 前端类型检查

```bash
cd lumina-frontend && npx vue-tsc --noEmit
```

### CI 环境（多模块一起跑）

多模块一起跑时需先 install 依赖模块，避免 Maven 反应堆依赖顺序问题：

```bash
# 先 install 被依赖的模块
mvn install -pl lumina-modules/lumina-business-notification,lumina-modules/lumina-business-base -am -DskipTests

# 再跑测试
mvn test -pl lumina-modules/lumina-business-agent
```

CI 可通过环境变量覆盖数据库连接：

```bash
export SPRING_DATASOURCE_URL=jdbc:mysql://ci-mysql:3306/lumina_dev
export SPRING_DATASOURCE_USERNAME=ci_user
export SPRING_DATASOURCE_PASSWORD=ci_pass
```

### 本地环境常见坑（v3.11 实测）

**坑 1：改了 agent-core 后单模块测试报类缺失（`FileNotFoundException: ...xxx.class cannot be opened`）**

`mvn test -pl lumina-modules/lumina-business-agent` 从**本地 Maven 仓库**解析 agent-core 依赖，
不会自动用工作区最新代码。agent-core 有改动时必须先刷新：

```bash
mvn install -pl lumina-agent-core -DskipTests
mvn test -pl lumina-modules/lumina-business-agent
```

**坑 2：本地 Redis 无密码，上下文启动报 `ERR Client sent AUTH, but no password is set`**

application-test.yml 默认 Redis 密码 123456；本地 Redis 若未设密码，用空值覆盖：

```bash
SPRING_DATA_REDIS_PASSWORD="" mvn test -pl lumina-modules/lumina-business-agent
```

**坑 3（Windows）：6379 端口可能同时有原生 Redis 服务与 Docker 容器，`127.0.0.1` 优先命中原生服务**

本机若同时存在（1）原生 Windows `redis-server.exe` 服务（监听 `127.0.0.1:6379`）与
（2）Docker 容器的端口映射（监听 `0.0.0.0:6379`），应用连 `localhost:6379` 会**优先走
原生服务**（回环地址比通配地址更精确）。排查要点：

- `docker exec <容器> redis-cli ping` 测的是**容器内部**，不代表宿主端口连通性；
- 用 `netstat -ano | findstr :6379` 确认实际监听进程（`redis-server.exe` vs
  `com.docker.backend.exe`），两边密码可能不同；
- 原生服务通常配了 `requirepass 123456`（与 test 配置默认一致，直接跑即可）；
  指向无密码实例时才需要坑 2 的空值覆盖。

**坑 4：单模块测试用本地仓库 jar 解析依赖模块，改了被依赖模块必须先 install**

business-agent 的 Flyway 迁移来自本地仓库中 business-base 的 jar。新增迁移
（如 V59）后必须先刷新，否则上下文启动即 `Unknown column`：

```bash
mvn install -pl lumina-modules/lumina-business-base -am -DskipTests
mvn test -pl lumina-modules/lumina-business-agent
```

---

## 测试体系概览

### 后端测试（981 @Test，v3.12 实测）

| 模块 | 用例总数（含集成） | 说明 |
|------|------------------|------|
| lumina-common | 28 | 工具类、异常、上下文 |
| lumina-framework | 50 | 框架配置、拦截器 |
| lumina-gateway | 24 | 网关过滤器、白名单 |
| lumina-agent-core | 389 | Agent 引擎、工具管理、MCP、工作流、上下文工程、A2A 客户端（SSRF 连接时校验） |
| lumina-business-base | 84 | 用户/角色/权限/租户/OAuth2（PKCE） |
| lumina-business-agent | 353 | Agent/对话/知识库/工作流/评估/成本/技能/A2A 服务端/知识飞轮/分治/分享中心/任务对账实例隔离 |
| lumina-business-notification | 33 | 通知 CRUD/已读/租户隔离 |

### 前端测试（106 用例）

覆盖 utils / stores / composables / api / views / directives / router，
`cd lumina-frontend && npx vitest run` 运行。

---

## 测试规范

遵循 `.agents/skills/lumina_testing/SKILL.md`：

### 单元测试
- `@ExtendWith(MockitoExtension.class)` + `@Mock` + `@InjectMocks`
- `@BeforeEach` 设置 `BaseContext.setTenantId()` / `setUserId()`
- `@AfterEach` 调用 `BaseContext.clear()` 清理 ThreadLocal
- 断言用 AssertJ（`assertThat` / `assertThatThrownBy`）

### 集成测试
- 继承 `BaseIntegrationTest`（`@SpringBootTest(NONE)` + `@ActiveProfiles("test")`）
- 每个方法 `@Transactional` 自动回滚
- 测试数据自包含，不依赖执行顺序
- 必须覆盖：正常路径 + 异常路径 + 租户隔离

---

## 常见问题

### Flyway 校验失败

```
Schema `lumina_dev` contains a failed migration to version XX
```

原因：某次迁移中途失败，Flyway 在 `flyway_schema_history` 留了 `success=0` 记录。

解决：删除 failed 记录，手动标记成功（表已存在的情况）：

```sql
-- 查看 failed 记录
SELECT * FROM flyway_schema_history WHERE success = 0;

-- 删除 failed 记录
DELETE FROM flyway_schema_history WHERE success = 0;

-- 如果表已手动创建，插入成功记录
INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success)
SELECT COALESCE(MAX(installed_rank),0)+1, 'XX', 'description', 'SQL', 'VXX__xxx.sql', NULL, 'manual-fix', NOW(), 0, 1
FROM flyway_schema_history;
```

### Nacos gRPC 连接报错

```
ERROR c.a.n.c.r.client.grpc.GrpcClient - Server check fail, port 9848
```

**可忽略**。test profile 已禁用 Nacos discovery/config，这是 SDK 心跳线程的残留日志。

### RocketMQ 连接报错

```
connect to null failed
```

**已修复**。test profile 已排除 `RocketMQAutoConfiguration`。如果仍出现，检查 `application-test.yml` 的 `spring.autoconfigure.exclude` 配置。

### 多模块并行测试失败

单独跑各模块全绿，多模块一起跑报错。

原因：Maven 反应堆依赖顺序 + 共享数据库 Flyway 并发。

解决：先 `mvn install` 依赖模块，再单独跑测试模块。

---

## 测试文件分布

```
lumina-common/src/test/                    5 文件
lumina-agent-core/src/test/               14 文件
lumina-framework/src/test/                 5 文件
lumina-gateway/src/test/                   3 文件
lumina-modules/lumina-business-base/src/test/      25 文件
lumina-modules/lumina-business-agent/src/test/     25 文件
lumina-modules/lumina-business-notification/src/test/  4 文件

lumina-frontend/src/**/__tests__/         15 文件
```

---

**只需 MySQL + Redis 开着，`mvn test` 即可验证全部自动化测试。**
