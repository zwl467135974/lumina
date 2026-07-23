# 01 — 一个 HTTP 请求的旅程

> **前置要求**：已完成[第一阶全部](../stage-1-foundation/README.md)
> **预计阅读**：35 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

第一阶你学了后端 5 个技术栈 + 前端 6 个技术栈，但它们是**散的**——你知道 Spring Boot 是什么、Vue 是什么，但不知道它们怎么配合工作。

这节用一个真实场景把它们**全部串起来**：用户在前端点击"创建 Agent"按钮，直到数据库写入成功、界面显示成功提示——这中间到底发生了什么？

**这是第二阶最重要的一篇**，因为后面所有章节（分层架构、权限、多租户、JWT……）都是在解释这条链路里某个环节"为什么这么设计"。

---

## 场景：用户创建一个 Agent

用户在 Lumina 前端的 Agent 表单页填好名称、类型、LLM 配置，点击"保存"按钮。

下面我们跟着这个请求，一站一站走完它的旅程。

---

## 第 1 站：前端——用户点击"保存"

### 模板：按钮绑定事件

```vue
<!-- 文件：lumina-frontend/src/views/agent/form.vue:248-251 -->
<el-button type="primary" @click="handleSubmit" :loading="submitting">
  {{ isEdit ? t('agent.form.updateBtn') : t('agent.form.createBtn') }}
</el-button>
```

用户点击 → 触发 `handleSubmit` 函数。

### 逻辑：表单校验 + 组装数据 + 调 API

```typescript
// 文件：lumina-frontend/src/views/agent/form.vue:399-436（简化）
const handleSubmit = async () => {
  // 1. Element Plus 表单校验（必填项检查）
  await formRef.value.validate(async (valid) => {
    if (!valid) return    // 校验不过，不提交

    // 2. 组装要发送的数据
    const submitData = {
      agentName: formData.agentName,
      agentType: formData.agentType,
      description: formData.description,
      llmConfig: formData.llmConfig,
      tools: selectedTools.value,
      knowledgeBaseIds: selectedKbIds.value
    }

    // 3. 调用 API（发 HTTP 请求）
    await createAgent(submitData)

    // 4. 成功提示 + 跳回列表页
    ElMessage.success('创建成功')
    router.push('/agent')
  })
}
```

### API 封装

```typescript
// 文件：lumina-frontend/src/api/modules/agent.ts:48-53
export function createAgent(data: CreateAgentDTO) {
  return request.post<R<AgentVO>>('/api/v1/agents', data)
  //                    ↑ 返回类型     ↑ URL     ↑ 请求体
}
```

`request` 是 Axios 封装实例。在真正发出请求前，**请求拦截器**会自动做一件事：

```typescript
// 文件：lumina-frontend/src/api/request.ts:19-31（简化）
service.interceptors.request.use((config) => {
  const token = getToken()                              // 从 localStorage 取 token
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}` // ← 自动注入
  }
  return config
})
```

**效果**：发出的请求自动带了 `Authorization: Bearer eyJhbG...` 头。

---

## 第 2 站：Vite 代理——跨域转发

请求从浏览器的 3000 端口发出，但后端在 8080 端口。如果直接发会**跨域**（CORS）。

```typescript
// 文件：lumina-frontend/vite.config.ts:17-31（简化）
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:8080',    // 转发到后端
      changeOrigin: true
    }
  }
}
```

Vite 开发服务器拦截所有 `/api` 开头的请求，转发到 `localhost:8080`。浏览器以为请求是发给自己（3000），不触发跨域检查。

> 💡 详见[第一阶 15-TypeScript+Vite](../stage-1-foundation/15-typescript-vite-basics.md)。

---

## 第 3 站：JWT 过滤器——身份校验

请求到达后端 8080 端口。第一个迎接它的是 **JWT 过滤器**（standalone 模式下是 `StandaloneJwtFilter`）。

### 它做了什么（5 步）

```
请求进来
  ↓
① 剥离客户端伪造的身份头（防攻击）
  ↓
② 检查白名单（登录/健康检查等不需要 token）
  ↓
③ 校验 Authorization 头格式（Bearer xxx）
  ↓
④ 验证 JWT 签名 + 过期时间 + Redis 黑名单
  ↓
⑤ 解析出用户信息，注入可信身份头
  ↓
放行到 Controller
```

### 关键代码

```java
// 文件：lumina-standalone/.../filter/StandaloneJwtFilter.java（简化）

// ⑤ 验证通过后，把用户身份注入 HTTP 头，传给下游
wrapped.setIdentityHeader("X-User-Id", String.valueOf(loginUser.getUserId()));
wrapped.setIdentityHeader("X-Tenant-Id", String.valueOf(loginUser.getTenantId()));
wrapped.setIdentityHeader("X-Roles", rolesStr);
wrapped.setIdentityHeader("X-Permissions", permissionsStr);
```

**为什么要注入这些头？** 因为下游的 Controller 和 Service 不直接解析 JWT——它们从 HTTP 头里读 `X-Tenant-Id` 等信息。JWT 过滤器是**唯一**解析 JWT 的地方，保证了身份信息的**单一可信来源**。

> 📖 JWT 的完整原理详见[第二阶 08-JWT 认证](08-jwt-auth.md)。

---

## 第 4 站：多租户拦截器——初始化上下文

Controller 执行之前，还有一个拦截器把"头里的身份信息"写进 `BaseContext`（ThreadLocal）：

```java
// 文件：lumina-business-base/.../interceptor/TenantIsolationInterceptor.java
@Override
public boolean preHandle(HttpServletRequest request, ...) {
    // 从 HTTP 头读身份信息 → 写进 BaseContext（ThreadLocal）
    BaseContext.initFromHeaders(
        request.getHeader("X-User-Id"),
        request.getHeader("X-Username"),
        request.getHeader("X-Tenant-Id"),
        request.getHeader("X-Roles"),
        request.getHeader("X-Permissions")
    );
    return true;
}

@Override
public void afterCompletion(...) {
    BaseContext.clear();    // ← 请求结束后必须清理，防 ThreadLocal 内存泄漏
}
```

从此刻起，整个请求处理链路的任何代码都能通过 `BaseContext.getTenantId()` 获取当前租户 ID——不用层层传参。

> 📖 详见[第二阶 07-多租户隔离](07-multi-tenancy.md)。

---

## 第 5 站：Controller——接收请求

终于到了你写的业务代码：

```java
// 文件：lumina-modules/lumina-business-agent/.../AgentController.java:84-108
@Audit(module = "agent", action = "CREATE", description = "创建Agent")  // ⑥ 审计
@Operation(summary = "创建 Agent")                                       // Swagger 文档
@PostMapping
public R<AgentVO> createAgent(@Valid @RequestBody CreateAgentDTO dto) {
    log.info("创建 Agent: {}", dto.getAgentName());

    Agent agent = toDomain(dto);                  // DTO → Domain
    Agent createdAgent = agentService.createAgent(agent);  // 委托 Service
    // ... 挂载知识库 ...

    AgentVO vo = toVO(createdAgent);              // Domain → VO（含 apiKey 脱敏）
    return R.success(vo);                          // 统一响应
}
```

### 注意三个注解

| 注解 | 作用 | 什么时候生效 |
|------|------|-------------|
| `@Valid` | 触发 DTO 字段校验 | 进入方法**前**（校验不过直接返回 400） |
| `@Audit` | 记录审计日志 | 方法**执行前后**（AOP 切面） |
| `@PostMapping` | 映射 POST 请求 | 路由匹配时 |

---

## 第 6 站：DTO 校验——挡住非法数据

`@Valid` 触发 `CreateAgentDTO` 上的校验注解：

```java
// 文件：lumina-modules/lumina-business-agent/.../dto/CreateAgentDTO.java
public class CreateAgentDTO {

    @NotBlank(message = "Agent 名称不能为空")              // 不能为空
    @Size(max = 100, message = "名称不能超过 100 字符")    // 最大长度
    private String agentName;

    @NotBlank(message = "Agent 类型不能为空")
    private String agentType;

    @Size(max = 500, message = "描述不能超过 500 字符")
    private String description;

    private LlmConfigDTO llmConfig;
    private List<String> tools;
    private List<Long> knowledgeBaseIds;
}
```

如果用户没填名称 → `@NotBlank` 校验失败 → Spring 直接返回 400 + 错误信息 → **不会进入 Controller 方法体**。

> 📖 详见[第二阶 05-校验与审计](05-validation-and-audit.md)。

---

## 第 7 站：Service——业务逻辑

```java
// 文件：lumina-modules/lumina-business-agent/.../AgentServiceImpl.java:142-160
@Override
@Transactional(rollbackFor = Exception.class)    // ⑦ 事务：出异常自动回滚
public Agent createAgent(Agent agent) {
    agent.validateName();                        // 领域模型自校验
    agent.validateType();

    AgentDO agentDO = toDO(agent);               // Domain → DO
    agentMapper.insert(agentDO);                 // 写数据库

    agent.setAgentId(agentDO.getAgentId());      // 回填自增 ID
    return agent;
}
```

注意 `@Transactional(rollbackFor = Exception.class)`——如果 insert 之后的知识库挂载失败，insert 也会**自动回滚**。

---

## 第 8 站：Mapper + 多租户——写数据库

```java
// 文件：lumina-modules/lumina-business-agent/.../mapper/AgentMapper.java
@Mapper
public interface AgentMapper extends BaseMapper<AgentDO> {
    // 空的！insert 来自 BaseMapper
}
```

`agentMapper.insert(agentDO)` 执行时，你以为 SQL 是：
```sql
INSERT INTO lumina_agent (agent_name, agent_type, ...) VALUES (?, ?, ...)
```

但实际上 MyBatis-Plus 的**租户拦截器**自动改写成了：
```sql
INSERT INTO lumina_agent (agent_name, agent_type, ..., tenant_id) VALUES (?, ?, ..., 1)
                                                                          ↑ 自动加的！
```

它从 `BaseContext.getTenantId()` 取当前租户 ID，自动塞进 SQL。业务代码完全无感。

> 📖 详见[第二阶 07-多租户隔离](07-multi-tenancy.md)和[第一阶 07-MyBatis-Plus 在 Lumina](../stage-1-foundation/07-mybatis-plus-in-lumina.md)。

---

## 第 9 站：统一响应——返回前端

```java
// 文件：lumina-common/.../core/R.java
return R.success(vo);
// 实际返回的 JSON：
// {
//   "code": 200,
//   "msg": "success",
//   "data": { "agentId": 1, "agentName": "...", ... }
// }
```

所有接口都用 `R<T>` 统一包装。前端拦截器看到 `code === 200` 就剥离外壳，直接拿 `data`：

```typescript
// 文件：lumina-frontend/src/api/request.ts:39-41
if (res.code === 200) {
  return res    // 直接返回 R，前端拿 res.data 就是 AgentVO
}
```

> 📖 详见[第二阶 04-异常与错误码](04-exception-error-code.md)。

---

## 第 10 站：审计记录——后台异步

你回看 Controller 方法的 `@Audit` 注解——它是 AOP 切面，在方法执行后**异步**记录审计日志：

```java
// 文件：lumina-framework/.../audit/aspect/AuditAspect.java
@Around("@annotation(audit)")    // 拦截所有 @Audit 方法
public Object auditAround(ProceedingJoinPoint point, Audit audit) {
    long start = System.currentTimeMillis();
    try {
        Object result = point.proceed();     // 执行你的业务代码
        return result;
    } finally {
        // 无论成功失败都记录审计
        publishAuditEvent(point, audit, start);  // ← 异步发布事件
    }
}
```

审计事件**不阻塞主流程**——优先发到 RocketMQ（跨实例消费），MQ 不可用时降级为本地 Spring Event。最终异步写入 `lumina_audit_log` 表。

> 📖 详见[第二阶 05-校验与审计](05-validation-and-audit.md)。

---

## 全链路总结图

```
浏览器
  │ 用户点击"保存"
  ▼
form.vue handleSubmit()
  │ ① Element Plus 表单校验
  │ ② 组装 submitData
  │ ③ 调 createAgent(submitData)
  ▼
api/modules/agent.ts createAgent()
  │ request.post('/api/v1/agents', data)
  ▼
api/request.ts 请求拦截器
  │ 自动注入 Authorization: Bearer xxx
  ▼
Vite Proxy (vite.config.ts)
  │ /api → localhost:8080 转发（解决跨域）
  ▼
═══════════ 后端 8080 ═══════════
  ▼
StandaloneJwtFilter
  │ ④ 剥离伪造头
  │ ⑤ JWT 签名+过期+黑名单 校验
  │ ⑥ 注入 X-Tenant-Id 等可信头
  ▼
TenantIsolationInterceptor
  │ ⑦ 从头读身份 → 写入 BaseContext（ThreadLocal）
  ▼
AgentController.createAgent()
  │ ⑧ @Valid 触发 DTO 校验（不过直接 400）
  │ ⑨ @Audit AOP 准备记录审计
  ▼
AgentServiceImpl.createAgent()
  │ ⑩ @Transactional 开启事务
  │ ⑪ Domain 自校验
  │ ⑫ DTO → Domain → DO 转换
  ▼
AgentMapper.insert()
  │ ⑬ MyBatis-Plus 自动加 tenant_id
  │ ⑭ 执行 INSERT SQL
  ▼
MySQL 数据库
  │ 写入 lumina_agent 表
  ▼
返回 R.success(vo)
  │ ⑮ 统一 JSON 响应
  ▼
═══════════ 回到前端 ═══════════
  ▼
响应拦截器
  │ code === 200 → 剥离外壳，返回 data
  ▼
form.vue
  │ ⑯ ElMessage.success('创建成功')
  │ ⑰ router.push('/agent') 跳回列表
  ▼
用户看到"创建成功"提示

（同时，后台 @Audit 切面异步写入审计日志）
```

---

## 动手试试

1. **打开 `AgentController.java` 的 `createAgent` 方法**：数数它上面有几个注解
2. **打开 `AgentServiceImpl.java` 的 `createAgent` 方法**：找到 `@Transactional` 和 `agentMapper.insert`
3. **在 IDEA 里给 `AgentController.createAgent` 打断点**，启动项目后从前端创建一个 Agent，用 Debug 模式一步步走完整条链路
4. **创建成功后查看审计日志页**：你应该能看到 `@Audit` 自动记录的"创建Agent"操作

---

## 小结

一个"创建 Agent"的请求，经过了 **17 个步骤**，跨越前端 3 个文件 + 后端 6 个文件 + 数据库。每一站都有明确的职责：

| 站点 | 职责 | 涉及技术（第一阶学的） |
|------|------|----------------------|
| 前端表单 | 用户交互 | Vue 3 + Element Plus |
| Axios 拦截器 | 自动注入 Token | Axios + TypeScript |
| Vite Proxy | 跨域转发 | Vite |
| JWT 过滤器 | 身份校验 | Spring Boot Filter |
| 租户拦截器 | 初始化上下文 | Spring MVC Interceptor |
| Controller | 接收+校验+委派 | Spring Boot REST |
| DTO 校验 | 挡住非法数据 | Bean Validation |
| Service | 业务逻辑+事务 | Spring Boot + @Transactional |
| Mapper | 写数据库 | MyBatis-Plus |
| 多租户拦截 | 自动加 tenant_id | MyBatis-Plus Interceptor |
| 统一响应 | 标准化返回格式 | R<T> 包装 |
| 审计切面 | 异步记录操作 | Spring AOP |

**后面 14 篇**就是逐个深入这条链路里的每个环节，讲清"为什么这么设计"。

---

## 下一步

下一篇 [为什么这么分层](02-layered-architecture.md)——Controller/Service/Domain/Infrastructure 四层架构的设计理由。

> 🚀 [02 — 分层架构 →](02-layered-architecture.md)

---

## 自测题

1. **JWT 过滤器为什么要"剥离客户端伪造的身份头"？**
   <details><summary>答案</summary>如果不剥离，恶意客户端可以自己在请求头里伪造 X-User-Id: 1 冒充管理员。过滤器先删掉所有 X-* 头，再注入自己校验过的可信值，保证身份信息单一来源。</details>

2. **`@Transactional` 如果不写 `rollbackFor = Exception.class` 会怎样？**
   <details><summary>答案</summary>默认只回滚 RuntimeException 和 Error。如果是受检异常（Checked Exception）则不回滚——可能导致数据不一致。加 rollbackFor = Exception.class 保证所有异常都回滚。</details>

3. **请求处理完成后，`BaseContext`（ThreadLocal）为什么要 `clear()`？**
   <details><summary>答案</summary>Tomcat 用线程池，线程会复用。如果不 clear，下一个请求可能读到上一个请求的身份信息——严重安全漏洞。</details>

4. **审计日志（@Audit）是同步还是异步？为什么这么设计？**
   <details><summary>答案</summary>异步（MQ 或 @Async Event）。审计日志不阻塞主业务流程——用户创建 Agent 不应该因为"写审计日志慢"而等待。</details>

---

📝 **本篇撰写期间修正的代码**：无。
