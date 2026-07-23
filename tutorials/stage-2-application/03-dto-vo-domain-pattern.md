# 03 — DTO / VO / Domain / DO 为什么要分这么多

> **前置要求**：已完成 [02-分层架构](02-layered-architecture.md)
> **预计阅读**：20 分钟
> **难度**：⭐⭐☆☆☆

---

## 这节解决什么问题

你在 Lumina 代码里会看到同一个"Agent"有四种表示：`CreateAgentDTO`、`AgentVO`、`Agent`（Domain）、`AgentDO`。**不都是 Agent 吗？为什么要分这么多种对象？** 用一个 `Map<String, Object>` 不行吗？

这节回答"为什么"——每层用不同对象的核心原因是**安全**和**解耦**。

---

## 四种对象各是什么

```
前端                    Controller                Service                 数据库
 │                         │                        │                       │
 │  JSON 请求体             │                        │                       │
 │ ──────────────────────► │                        │                       │
 │     （CreateAgentDTO）   │                        │                       │
 │                         │  toDomain()            │                       │
 │                         │ ─────────────────────► │                       │
 │                         │      （Agent 领域模型）  │                       │
 │                         │                        │  toDO()               │
 │                         │                        │ ────────────────────► │
 │                         │                        │     （AgentDO）        │
 │                         │                        │                       │
 │  JSON 响应体             │  ◄───────────────────── │  ◄────────────────── │
 │  （AgentVO）             │     toVO(Domain)        │    Domain ← DO       │
```

| 对象 | 全称 | 所在层 | 职责 |
|------|------|--------|------|
| **DTO** | Data Transfer Object | API 层 | 接收前端传来的数据，带校验注解 |
| **Domain** | 领域模型 | Domain 层 | 业务规则、核心逻辑 |
| **DO** | Data Object | Infrastructure 层 | 和数据库表一一对应 |
| **VO** | View Object | API 层 | 返回给前端的数据，可脱敏 |

---

## 为什么不能用一个对象？

### 场景 1：安全问题（API Key 脱敏）

```java
// DO 包含 API Key 明文（数据库存的）
public class AgentDO {
    private String agentName;
    private String llmConfig;    // llmConfig 里含 apiKey 明文
    private Integer deleted;     // 逻辑删除标记
    private Long tenantId;       // 租户 ID
}

// 如果直接返回 DO → 前端能看到 apiKey 明文！
return R.success(agentDO);    // ❌ 安全漏洞
```

**Lumina 的处理**：返回 VO 时**脱敏**：

```java
// 文件：AgentController.java 的 toVO 方法
private AgentVO toVO(Agent agent) {
    AgentVO vo = new AgentVO();
    BeanUtils.copyProperties(agent, vo);
    maskApiKeyInLlmConfig(vo);    // ← 把 llmConfig 里的 apiKey 设成 null
    return vo;
}
```

### 场景 2：字段不对等（前端不需要某些字段）

```java
// DO 有 deleted、tenant_id 等内部字段
// VO 不需要返回这些
public class AgentVO {
    private Long agentId;
    private String agentName;
    private String agentType;
    private String description;
    // 没有 deleted、tenant_id —— 前端不需要知道
}
```

### 场景 3：校验只对输入有效

```java
// DTO 有校验注解（输入时检查）
public class CreateAgentDTO {
    @NotBlank(message = "名称不能为空")
    private String agentName;
}

// VO 没有校验注解（输出不需要校验）
public class AgentVO {
    private String agentName;    // 返回时不用校验
}
```

如果用同一个对象，`@NotBlank` 会同时作用于输入和输出——语义混乱。

---

## 转换怎么做的

Lumina 用 Spring 的 `BeanUtils.copyProperties` 做对象间转换：

```java
// 同名字段自动复制
BeanUtils.copyProperties(source, target);
// source.getAgentName() → target.setAgentName()
// source.getAgentType() → target.setAgentType()
```

### Controller 里的转换

```java
// 文件：AgentController.java
@PostMapping
public R<AgentVO> createAgent(@Valid @RequestBody CreateAgentDTO dto) {
    Agent agent = toDomain(dto);         // DTO → Domain（Controller 层转）
    Agent created = agentService.createAgent(agent);
    return R.success(toVO(created));     // Domain → VO（Controller 层转）
}

// DTO → Domain
private Agent toDomain(CreateAgentDTO dto) {
    Agent agent = new Agent();
    agent.setAgentName(dto.getAgentName());
    agent.setAgentType(dto.getAgentType());
    // ... 特殊处理 tools（List<String> → 逗号分隔 String）
    return agent;
}

// Domain → VO（含脱敏）
private AgentVO toVO(Agent agent) {
    AgentVO vo = new AgentVO();
    BeanUtils.copyProperties(agent, vo);
    maskApiKeyInLlmConfig(vo);
    return vo;
}
```

### Service 里的转换

```java
// 文件：AgentServiceImpl.java
public Agent createAgent(Agent agent) {
    AgentDO agentDO = toDO(agent);       // Domain → DO
    agentMapper.insert(agentDO);
    agent.setAgentId(agentDO.getAgentId());  // 回填自增 ID
    return agent;
}

private AgentDO toDO(Agent agent) {
    AgentDO agentDO = new AgentDO();
    BeanUtils.copyProperties(agent, agentDO);
    // 兜底租户 ID
    if (agentDO.getTenantId() == null) {
        agentDO.setTenantId(BaseContext.getTenantId() != null ? BaseContext.getTenantId() : 0L);
    }
    return agentDO;
}
```

---

## 为什么 Controller 负责转 DTO↔Domain，Service 负责转 Domain↔DO？

**分界线在 Domain**——Domain 是核心，Controller 和 Service 都围绕它转：

```
DTO ──(Controller 转)──► Domain ◄──(Service 转)── DO
       输入侧转换            ▲          输出侧转换
                            │
                        核心业务对象
```

- **Controller** 负责"外部格式 ↔ 内部模型"（DTO ↔ Domain）
- **Service** 负责"内部模型 ↔ 存储格式"（Domain ↔ DO）

这样 Domain 不依赖任何外部格式——改数据库不影响 Controller，改 API 格式不影响 Service。

---

## 前端的 DTO/VO 对应

前端 TypeScript 也有对应的类型定义：

```typescript
// 文件：lumina-frontend/src/types/api.ts

// 对应后端 CreateAgentDTO
interface CreateAgentDTO {
  agentName: string
  agentType: string
  description?: string
  llmConfig?: object
  tools?: string[]
}

// 对应后端 AgentVO
interface AgentVO {
  id: number
  agentName: string
  agentType: string
  description?: string
  llmConfig?: string
}
```

> 💡 前后端类型要保持一致——这也是为什么要用 TypeScript 而不是裸 JavaScript。

---

## 动手试试

1. **找到这四个类**：
   - `CreateAgentDTO`（api/dto/）
   - `Agent`（domain/model/）
   - `AgentDO`（infrastructure/entity/）
   - `AgentVO`（api/vo/）
2. **对比它们的字段**：DTO 有校验注解、DO 有 `@TableName`/`@TableLogic`、VO 有脱敏处理
3. **找到 `AgentController.toVO()` 方法**：看 `maskApiKeyInLlmConfig` 怎么脱敏

---

## 小结

| 对象 | 层 | 核心特征 | 为什么单独存在 |
|------|----|---------|---------------|
| DTO | API | 带校验注解、只含允许输入的字段 | 安全：控制能传什么 |
| Domain | 领域 | 含业务规则方法 | 解耦：业务核心不依赖框架 |
| DO | 基础设施 | 和表一一对应、有 ORM 注解 | 隔离：数据库结构不外泄 |
| VO | API | 可脱敏、不含内部字段 | 安全：控制能看到什么 |

**核心原则**：一个对象只服务一个方向（输入/存储/输出），混用会导致安全和耦合问题。

---

## 下一步

下一篇 [异常与错误码](04-exception-error-code.md)——为什么不用 RuntimeException，统一异常怎么工作。

> 🚀 [04 — 异常与错误码 →](04-exception-error-code.md)

---

## 自测题

1. **为什么不能用同一个对象既做 DTO（接收输入）又做 VO（返回输出）？**
   <details><summary>答案</summary>DTO 有 @NotBlank 校验注解（输入时生效），但返回时不需要校验。而且 VO 可能要脱敏（如隐藏 apiKey），DTO 可能要限制字段（如禁止传 deleted），混在一起语义混乱。</details>

2. **AgentDO 有 `deleted` 字段，为什么 AgentVO 没有？**
   <details><summary>答案</summary>deleted 是内部逻辑删除标记，前端不需要知道。VO 只暴露前端需要的字段，隐藏内部实现。</details>

3. **`BeanUtils.copyProperties` 是按什么规则复制的？**
   <details><summary>答案</summary>按"同名字段"复制——source.getAgentName() 的值复制到 target.setAgentName()。名字不一致的字段不会自动复制，需手动 set。</details>

---

📝 **本篇撰写期间修正的代码**：无。
