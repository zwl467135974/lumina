# Lumina 测试指南

> **v3.2 更新**：自动化测试体系已覆盖后端 413+ @Test + 前端 103 用例。
> 集成测试只需 MySQL + Redis 两个本地服务，无需 Nacos / RocketMQ / Docker。

---

## 环境要求（最低要求）

### 必须运行的服务

| 服务 | 地址 | 默认密码 | 用途 |
|------|------|---------|------|
| **MySQL 8.0** | localhost:3306 | root / 123456 | 数据持久化（库名 `lumina_dev`） |
| **Redis** | localhost:6379 | 无密码 | 缓存 / 权限快照 / 在线用户 |

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

---

## 测试体系概览

### 后端测试（413+ @Test）

| 模块 | 单元测试 | 集成测试 | 说明 |
|------|---------|---------|------|
| lumina-common | 28 | - | 工具类、异常、上下文 |
| lumina-agent-core | 110 | - | Agent 引擎、工具管理、工作流 |
| lumina-framework | 50 | - | 框架配置、拦截器 |
| lumina-gateway | 24 | - | 网关过滤器、白名单 |
| lumina-business-base | 68 | 36 | 用户/角色/权限/租户/字典/审计 CRUD + 租户隔离 |
| lumina-business-agent | 92 | 41 | Agent/对话/知识库/工作流/评估/成本/Prompt/LlmProvider |
| lumina-business-notification | - | 6 | 通知 CRUD/已读/租户隔离 |

### 前端测试（103 用例 / 15 文件）

| 类型 | 文件数 | 用例数 | 覆盖 |
|------|--------|--------|------|
| utils | 3 | 25 | auth / format / storage |
| stores | 3 | 31 | permission / user / notification |
| composables | 1 | 5 | useTable |
| api | 2 | 12 | request / stream-events |
| views | 2 | 7 | agent form / agent list / prompt |
| directives | 2 | 10 | v-permission / v-role |
| router | 1 | 6 | guards（鉴权跳转） |

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
