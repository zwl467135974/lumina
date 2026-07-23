# 05 — 参数校验与审计日志

> **前置要求**：已完成 [04-异常与错误码](04-exception-error-code.md)
> **预计阅读**：20 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

企业系统的两个硬性要求：
1. **数据合法性**——别让垃圾数据进数据库（参数校验）
2. **操作可追溯**——谁在什么时候做了什么（审计日志）

Lumina 用两个注解解决：`@Valid`（校验）和 `@Audit`（审计）。这节讲它们怎么工作。

---

## 参数校验：@Valid + Bean Validation

### 类比：机场安检

你进登机口前要过安检——检查身份证、检查行李。**不是上了飞机才查**，而是进门前就拦住。

`@Valid` 就是"安检"——**在进入 Controller 方法体之前**就检查参数合法性，不合法直接返回 400。

### 怎么用

```java
// Controller 方法参数加 @Valid
@PostMapping
public R<AgentVO> createAgent(@Valid @RequestBody CreateAgentDTO dto) {
    // ↑ @Valid 触发校验。如果不通过，不会进入这个方法体！
    // 直接返回 400 + 错误信息
}
```

### DTO 上标校验注解

```java
// 文件：CreateAgentDTO.java
public class CreateAgentDTO {

    @NotBlank(message = "Agent 名称不能为空")        // 不能 null / 空字符串 / 空白
    @Size(max = 100, message = "名称不能超过 100 字符") // 长度限制
    private String agentName;

    @NotBlank(message = "Agent 类型不能为空")
    private String agentType;

    @Size(max = 500, message = "描述不能超过 500 字符")
    private String description;

    @Min(0)        // 最小值
    private Integer rateLimit;

    @Max(10)       // 最大值
    private Integer maxConcurrent;
}
```

### 常用校验注解速查

| 注解 | 检查规则 | 示例 |
|------|----------|------|
| `@NotBlank` | 非 null 且非空白 | 名称必填 |
| `@NotNull` | 非 null | ID 必传 |
| `@Size(max=N)` | 字符串长度 ≤ N | 名称 ≤ 100 字 |
| `@Min(N)` | 数字 ≥ N | rateLimit ≥ 0 |
| `@Max(N)` | 数字 ≤ N | maxConcurrent ≤ 10 |
| `@Email` | 邮箱格式 | user@xxx.com |
| `@Pattern(regexp)` | 正则匹配 | 只允许字母数字 |

### 校验失败时

用户没填名称 → `@NotBlank` 校验失败 → Spring 抛出 `MethodArgumentNotValidException` → 被 `GlobalExceptionHandler` 捕获 → 返回：

```json
{
  "code": 400,
  "errCode": 400,
  "msg": "Agent 名称不能为空"
}
```

**Controller 方法体根本不会执行**——垃圾数据被挡在门外了。

---

## 审计日志：@Audit + AOP

### 类比：银行的监控摄像头

银行柜台上方有摄像头，**你办理业务时它自动录像**——你不用自己按下"开始录像"按钮。

`@Audit` 就是摄像头——**方法执行时自动记录审计日志**，你不用手写日志代码。

### 怎么用

```java
// 文件：AgentController.java:84
@Audit(module = "agent", action = "CREATE", description = "创建Agent")
@PostMapping
public R<AgentVO> createAgent(@Valid @RequestBody CreateAgentDTO dto) {
    // 你的业务代码……
    // @Audit 自动帮你记录了审计日志，你不用写任何日志代码
}
```

### 记录了什么

```json
{
  "tenantId": 1,
  "userId": 1,
  "username": "admin",
  "module": "agent",            // 你配的
  "action": "CREATE",           // 你配的
  "description": "创建Agent",   // 你配的
  "requestMethod": "POST",
  "requestUrl": "/api/v1/agents",
  "requestIp": "192.168.1.100",
  "status": 1,                  // 1=成功 0=失败
  "durationMs": 152,            // 耗时
  "createTime": "2026-07-23..."
}
```

### 它怎么做到的（AOP 切面）

```java
// 文件：lumina-framework/.../audit/aspect/AuditAspect.java
@Aspect                          // ← AOP 切面
@Component
public class AuditAspect {

    @Around("@annotation(audit)")    // ← 拦截所有 @Audit 方法
    public Object auditAround(ProceedingJoinPoint point, Audit audit) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            Object result = point.proceed();    // 执行你的业务代码
            return result;
        } finally {
            // 无论成功失败，都记录审计
            long duration = System.currentTimeMillis() - start;
            publishAuditEvent(point, audit, duration);    // ← 异步发布事件
        }
    }
}
```

**关键**：`@Around("@annotation(audit)")` 意思是"拦截所有标了 @Audit 注解的方法"。Spring 用**动态代理**在方法前后插入审计逻辑——详见[第三阶 03-AOP 代理](../stage-3-mastery/03-spring-aop-proxy.md)。

### 异步发布（不阻塞业务）

审计日志**不阻塞主流程**——`publishAuditEvent` 优先发到 RocketMQ，MQ 不可用时降级为本地 Spring Event。最终异步写入 `lumina_audit_log` 表。

> 💡 这意味着用户创建 Agent 时**不会因为"写审计日志慢"而等待**。

---

## @Audit 的四个属性

```java
@Audit(
    module = "agent",                              // 业务模块（小写）
    action = "CREATE",                             // 操作类型（标准枚举）
    description = "创建Agent",                      // 人类可读描述
    targetIdParam = "id"                           // 从哪个参数提取目标 ID（可选）
)
```

### 标准 action 枚举

| action | 含义 | 示例 |
|--------|------|------|
| `CREATE` | 创建 | 创建 Agent |
| `UPDATE` | 更新 | 编辑 Agent |
| `DELETE` | 删除 | 删除 Agent |
| `EXECUTE` | 执行 | 执行 Agent / 工作流 |
| `TEST` | 测试 | 测试 LLM 连通性 |

> 📖 完整的 @Audit 规范见 `.agents/skills/lumina_observability/SKILL.md`。

---

## 动手试试

1. **打开 `CreateAgentDTO.java`**：数数有几个校验注解
2. **在 `AgentController.java` 里搜索 `@Audit`**：看看哪些操作被审计了
3. **启动项目后创建一个 Agent**：然后去审计日志页看记录
4. **故意不填名称提交**：看前端弹什么提示（校验在前后端都做了）

---

## 小结

| 机制 | 注解 | 什么时候生效 | 一句话记忆 |
|------|------|-------------|-----------|
| 参数校验 | `@Valid` + DTO 注解 | 进入方法**前** | 安检：不合法数据不让进 |
| 审计日志 | `@Audit` | 方法执行**前后** | 监控：自动记录谁做了什么 |

---

## 下一步

下一篇 [权限 RBAC](06-permission-rbac.md)——@RequirePermission 怎么拦截，RBAC 五表怎么设计。

> 🚀 [06 — 权限 RBAC →](06-permission-rbac.md)

---

## 自测题

1. **`@Valid` 校验不通过时，Controller 方法体会执行吗？**
   <details><summary>答案</summary>不会。Spring 在方法参数绑定阶段就校验，不通过直接抛 MethodArgumentNotValidException，被 GlobalExceptionHandler 捕获返回 400。方法体根本不执行。</details>

2. **`@Audit` 的审计日志是同步还是异步写的？为什么？**
   <details><summary>答案</summary>异步（MQ 或 @Async Event）。审计日志不能阻塞业务——用户创建 Agent 不应该因为写日志慢而等待。</details>

3. **`@Audit` 是怎么做到"不改业务代码就记录日志"的？**
   <details><summary>答案</summary>AOP 动态代理。Spring 给标了 @Audit 的方法生成代理对象，在方法执行前后自动插入审计逻辑。</details>

---

📝 **本篇撰写期间修正的代码**：无。
