---
name: lumina_observability
description: Use this skill when adding logging, audit, or metrics instrumentation. Covers MDC traceId injection, @Audit annotation for declarative audit logging, and Micrometer Timer/Counter for Prometheus metrics.
---

# Lumina 可观测性规范

## 功能概述

本技能包用于规范 Lumina 框架的日志、审计和指标埋点，包括 MDC 链路追踪注入、`@Audit` 声明式审计日志、Micrometer 指标采集（Prometheus），确保系统运行状态可观测、可追溯。

## 日志规范（MDC）

### traceId 自动注入

`LogContextInterceptor` 拦截器在每个 HTTP 请求进入时自动注入 MDC 上下文：

| MDC Key | 来源 | 说明 |
|---------|------|------|
| `traceId` | 自动生成（UUID 短格式） | 链路追踪 ID，贯穿整个请求 |
| `tenantId` | 请求头 / Token | 当前租户 ID |
| `userId` | Token 解析 | 当前用户 ID |

### 日志 Pattern 配置

```xml
<pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level [%X{traceId:--}] %logger{36} - %msg%n</pattern>
```

- 使用 `%X{traceId:--}` 输出 traceId，无值时显示 `--`
- 所有日志自动携带 traceId，便于全链路问题排查
- 业务日志中无需手动输出 traceId

### 日志使用规范

```java
private static final Logger log = LoggerFactory.getLogger(UserService.class);

log.info("用户创建成功, userId={}, username={}", userId, username);
log.warn("租户配额即将用尽, tenantId={}, used={}/{}", tenantId, used, quota);
log.error("Agent 执行失败, agentId={}", agentId, e);
```

- 使用 SLF4J + 占位符（`{}`），禁止字符串拼接
- 关键操作记录 INFO，异常记录 ERROR（带异常堆栈）

## 审计日志（@Audit）

### 声明式审计标注

```java
@Audit(module = "用户管理", action = "CREATE", description = "创建用户")
@PostMapping("/users")
public Result<UserVO> create(@RequestBody @Valid CreateUserDTO dto) {
    // ...
}
```

- 在 Controller 方法上标注 `@Audit`，声明模块、操作类型和描述
- `AuditAspect` AOP 切面自动采集审计信息，无需手动编码

### 审计采集内容

| 字段 | 来源 | 说明 |
|------|------|------|
| operator | BaseContext.userId | 操作人 ID |
| module | @Audit.module | 功能模块 |
| action | @Audit.action | 操作类型（CREATE/UPDATE/DELETE） |
| description | @Audit.description | 操作描述 |
| httpMethod | 请求信息 | HTTP 方法 |
| requestUri | 请求信息 | 请求路径 |
| duration | AOP 计时 | 方法耗时（毫秒） |
| result | 返回值/异常 | 成功/失败 |

### 审计存储

- 审计事件通过事件驱动写入 `lumina_audit_log` 表
- 支持异步写入，不影响主流程性能
- 审计查询 API：`GET /api/v1/audit-logs`（支持分页、模块筛选）

## 指标规范（Micrometer）

### 指标采集

```java
@Autowired(required = false)
private MeterRegistry meterRegistry;

public void recordExecution(String type, long durationMs, boolean success) {
    if (meterRegistry == null) return;
    meterRegistry.timer("agent.execution.duration",
            "type", type,
            "result", success ? "success" : "failure")
        .record(Duration.ofMillis(durationMs));
}
```

- 使用 `@Autowired(required = false)` 注入 MeterRegistry，兼容无指标环境
- 使用 `Timer` 记录耗时分布，`Counter` 记录次数
- 指标注册前判空，避免启动失败

### 标准指标命名

| 指标名 | 类型 | Tags | 说明 |
|--------|------|-------|------|
| `agent.execution.duration` | Timer | type, result | Agent 执行耗时 |
| `agent.execution.total` | Counter | type, result | Agent 执行次数 |
| `tool.invocation.duration` | Timer | name, result | 工具调用耗时 |
| `tool.invocation.total` | Counter | name, result | 工具调用次数 |
| `tool.error.total` | Counter | name, errorType | 工具错误次数 |

### 指标命名规范

- 使用点号分隔的命名空间：`{domain}.{action}.{metric}`
- Tag 值使用小写字母 + 连字符，如 `success`、`failure`、`timeout`
- 禁止在 Tag 中放入高基数数据（如 userId、traceId）

## 监控端点

| 端点 | 用途 |
|------|------|
| `/actuator/health` | 健康检查 |
| `/actuator/prometheus` | Prometheus 指标抓取 |
| `/api/v1/tools/stats` | 工具调用统计查询 |
| `/api/v1/audit-logs` | 审计日志查询 |

## 最佳实践

1. **声明式优先**：使用 `@Audit` 注解，避免手动编写审计代码
2. **MDC 自动注入**：不手动设置 traceId，由拦截器统一处理
3. **指标判空**：MeterRegistry 注入用 `required = false`，注册前判空
4. **低基数 Tag**：Tag 值为有限枚举，禁止放入动态数据
5. **异步审计**：审计写入异步化，不影响主请求性能
