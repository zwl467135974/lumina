# Lumina 项目演进路线图

**版本**: v1.0
**最后更新**: 2026-06-30
**当前阶段**: 核心功能已完成，进入质量增强与能力深化阶段

---

## 一、现状基线

Lumina 已具备完整的核心能力：多租户 RBAC、JWT 网关、AgentScope 执行引擎、动态工具注册、前端管理后台。在此基础上，本路线图梳理下一阶段的演进方向，按"架构健壮性 → 质量保障 → 能力深化 → 可观测性 → 工程化 → 体验/运维"的顺序推进。

---

## 二、架构健壮性（P0）

### 2.1 响应式上下文传递

**问题**: `BaseContext` 使用 `ThreadLocal` 存储租户/用户/角色/权限上下文（`lumina-common/.../core/BaseContext.java:19-39`），而 Agent 执行引擎在 Reactor 链路中切换到 `Schedulers.boundedElastic()`（`lumina-agent-core/.../engine/impl/DefaultAgentExecutionEngine.java:63-66`）。线程切换后 `ThreadLocal` 会丢失，导致 Agent 执行期间无法读取当前用户与租户，多租户隔离在异步场景下存在隐患。

**改进方案**:
- 引入 `Micrometer Context Propagation`，将 `BaseContext` 注册为 `ThreadLocalAccessor`
- 或在 `Mono` 链路通过 `contextWrite` / `deferContextual` 显式透传用户上下文
- 网关透传的 `X-Tenant-Id` 等 Header 在响应式下游统一用 `ServerWebExchange` 上下文承载

**验收标准**: Agent 异步执行期间可正确读取 `tenantId / userId`，集成测试覆盖线程切换场景。

### 2.2 敏感配置治理

**问题**: JWT 密钥、数据库密码等硬编码于 `application.yml` 与 `docker-compose.yml`，存在泄漏风险。

**改进方案**:
- 所有密钥改为环境变量占位（`${LUMINA_JWT_SECRET_KEY}`），本地用 `.env`，生产用 Vault / Nacos 加密配置
- 提供 `application-local.yml` 模板，移除真实密钥
- CI 中加入 secret 扫描（如 gitleaks）

### 2.3 统一错误码体系

**现状**: `R.fail(msg)` 以字符串描述错误，前端难以做精细化处理。

**改进方案**:
- 在 `lumina-common` 定义 `ErrorCode` 枚举（业务域 + 错误号），如 `USER_NOT_FOUND(10001)`、`TENANT_FORBIDDEN(20003)`
- `BusinessException` 携带 `ErrorCode`，全局异常处理器统一翻译为 `R` 响应
- 前端按错误码做定向提示与跳转

---

## 三、质量保障（P1）

### 3.1 单元测试与集成测试

**现状**: 全项目无 `src/test/`，测试覆盖率为 0。

**目标**: 核心模块覆盖率 ≥ 70%。

**落地清单**:
- **lumina-common**: `JwtUtil`、`PasswordUtil`、`BaseContext`（含上下文清理）、`R`、`PageResult`
- **lumina-business-base**: `UserServiceImpl`、`RoleServiceImpl`、`PermissionServiceImpl`、`TenantServiceImpl`、`AuthServiceImpl`，重点覆盖租户隔离、角色分配、权限校验
- **lumina-agent-core**: `ConfigLoader`、`PromptLoader`、`EnhancedToolManager`（参数解析/类型转换）、`MemoryManager`（Redis 持久化与降级）、`DefaultAgentExecutionEngine`（Mock AgentScope）
- **集成测试**: 使用 Testcontainers 启动 MySQL/Redis，验证 `TenantLineInterceptor` SQL 注入、Gateway JWT 过滤器端到端流程

### 3.2 CI/CD 流水线

- GitHub Actions：`mvn verify` + 前端 `pnpm lint && pnpm build` + 覆盖率上报
- PR 强制代码审查与 CI 通过
- 制品推送（Docker image）

---

## 四、Agent 能力深化（P1）

### 4.1 流式输出

**现状**: Agent 仅支持阻塞/`Mono` 整体返回（`AgentController.executeAgent` 返回 `R<String>`）。

**改进**:
- 后端基于 `Flux<ServerSentEvent>` 暴露 `/agents/{id}/execute/stream`
- AgentScope 流式 token 透传
- 前端实现打字机效果对话 UI

### 4.2 会话与记忆管理

- 以 `conversationId` 维度持久化多轮对话（复用 `MemoryManager` Redis 实现）
- 支持会话列表、历史回放、上下文窗口裁剪策略
- Token 用量统计与成本核算

### 4.3 RAG 与知识库

- 引入向量存储（Milvus/PgVector）与 Embedding 模型
- 文档入库 → 切片 → 向量化 → 检索增强 Prompt
- 作为 `lumina-agent-core` 的可选依赖，保持框架轻量

### 4.4 多模型适配

**现状**: 仅集成 DashScope（`DefaultAgentExecutionEngine.createReActAgent` 硬编码 `DashScopeChatModel`）。

**改进**: 通过 `ModelType` 工厂创建模型（OpenAI / Claude / Gemini / Ollama），Agent 配置中声明，运行时按类型路由。

### 4.5 工具调用可观测

- 记录每次工具调用的入参、出参、耗时、成功率
- 工具调用链路追踪（与 5.1 联动）
- 异常工具自动降级/熔断

---

## 五、可观测性（P2）

### 5.1 日志与链路追踪

- 统一日志规范：MDC 注入 `traceId / tenantId / userId`，输出 JSON 结构化日志
- 集成 OpenTelemetry，贯穿 Gateway → 业务服务 → Agent 执行 → 工具调用
- 采样策略与脱敏（Prompt/Key）

### 5.2 监控指标（Micrometer）

- Agent 维度：执行次数、耗时分布、失败率、Token 消耗
- 工具维度：调用 QPS、P99 耗时、错误率
- 业务维度：租户活跃度、API QPS
- 暴露给 Prometheus + Grafana 面板

### 5.3 审计日志

- 用户/角色/权限/租户的变更操作落审计表
- Agent 执行记录留痕（含 Prompt 摘要），支持合规追溯

---

## 六、工程化规范（P2）

### 6.1 API 版本策略

- 明确 `/api/v1` 版本演进规则（v2 共存期、弃用策略）
- Header 中标注废弃接口，前端联调告警

### 6.2 数据库迁移管理

**现状**: 手工 SQL 脚本（`sql/01_create_tables.sql` 等），环境间易漂移。

**改进**: 引入 Flyway/Liquibase，版本化迁移，应用启动自动同步 schema。

### 6.3 限流熔断

- Gateway 接入 Sentinel：按路由/租户/IP 限流
- Agent 执行降级（LLM 超时 → 缓存/默认回复）
- 工具调用熔断（连续失败触发短路）

---

## 七、前端体验（P2）

- 流式对话窗口（SSE 渲染、可中断、重试）
- 基于权限的动态菜单与路由（后端下发）
- Agent 调试面板：展示 ReAct 思考链、工具调用明细、Token 用量
- 暗色主题、国际化（i18n）

---

## 八、运维与部署（P3）

- 灰度发布（基于租户/权重）
- Helm Chart / Kubernetes 清单
- 备份策略（MySQL/Redis/Nacos）
- 蓝绿/金丝雀发布脚本

---

## 九、优先级矩阵

| 优先级 | 方向 | 预计工作量 | 价值 |
|--------|------|-----------|------|
| **P0** | 响应式上下文传递 | 1-2 天 | 修复多租户异步隔离隐患 |
| **P0** | 敏感配置治理 | 0.5 天 | 安全基线 |
| **P1** | 单元/集成测试 | 5-7 天 | 质量保障 |
| **P1** | 流式输出 + 会话管理 | 4-5 天 | 核心体验 |
| **P1** | 多模型适配 | 2-3 天 | 扩展性 |
| **P2** | 统一错误码 | 1 天 | 前后端协作 |
| **P2** | 日志/追踪/指标 | 3-4 天 | 可观测性 |
| **P2** | Flyway + 限流熔断 | 2-3 天 | 工程化 |
| **P2** | 前端对话 UI | 3-4 天 | 体验 |
| **P3** | RAG / 知识库 | 5-7 天 | 能力扩展 |
| **P3** | K8s / 灰度 | 3-4 天 | 运维 |

---

## 十、里程碑建议

- **M1（稳定性）**: P0 完成，响应式上下文与配置治理就绪
- **M2（质量基线）**: 测试覆盖率达标，CI 流水线运行
- **M3（体验跃升）**: 流式对话 + 会话管理 + 多模型上线
- **M4（生产可观测）**: 日志/追踪/指标/审计全链路打通
- **M5（能力扩展）**: RAG 知识库、灰度发布、K8s 部署

---

## 附：参考文档

- [项目状态分析](PROJECT_STATUS_ANALYSIS.md)
- [剩余任务清单](REMAINING_TASKS.md)
- [架构模式分析与建议](architecture/架构模式分析与建议.md)
- [Agent 执行引擎设计](architecture/Agent执行引擎设计.md)
