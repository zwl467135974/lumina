# 02 — 为什么这么分层

> **前置要求**：已完成 [01-一个请求的旅程](01-request-lifecycle.md)
> **预计阅读**：20 分钟
> **难度**：⭐⭐☆☆☆

---

## 这节解决什么问题

上一节你看到了一个请求经过 Controller → Service → Mapper 的旅程。但你会问：**为什么要分这么多层？一个类把所有事干完不行吗？**

这节回答"为什么要分层"——不是教条式地说"这是最佳实践"，而是用一个反面例子让你**亲身感受**不分层的痛苦。

---

## 不分层的痛苦：反面例子

假设我们把创建 Agent 的逻辑**全塞进 Controller**：

```java
// ❌ 反面教材：所有逻辑揉在一个类里
@RestController
public class AgentController {

    @PostMapping("/agents")
    public Map<String, Object> createAgent(@RequestBody Map<String, Object> body) {
        // 1. 手动校验
        if (body.get("name") == null || body.get("name").toString().isBlank()) {
            return Map.of("code", 400, "msg", "名称不能为空");
        }

        // 2. 手动拼 SQL
        String name = body.get("name").toString();
        String type = body.get("type").toString();
        Long tenantId = Long.valueOf(body.get("tenantId").toString());
        String sql = "INSERT INTO agent (name, type, tenant_id) VALUES (?, ?, ?)";

        // 3. 手动管数据库连接
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, type);
            ps.setLong(3, tenantId);
            ps.executeUpdate();
        } catch (SQLException e) {
            return Map.of("code", 500, "msg", "数据库错误");
        }

        // 4. 手动记日志
        logger.info("创建 Agent: name={}, tenantId={}", name, tenantId);

        // 5. 手动组装响应
        return Map.of("code", 200, "msg", "成功", "data", Map.of("name", name));
    }
}
```

### 有什么问题？

1. **不可测试**——想测业务逻辑必须连数据库
2. **不可复用**——"创建 Agent"的逻辑在别的地方也要用，但全塞在 Controller 里拿不出来
3. **不可维护**——校验规则变了要改 Controller，数据库表变了也要改 Controller
4. **不安全**——手动拼 SQL 容易注入，手动管连接容易泄漏
5. **不统一**——每个接口的响应格式可能不一样

---

## 分层架构：各司其职

Lumina 用四层架构，**每层只管自己的事**：

```
┌──────────────────────────────────────────┐
│  Controller（API 层）                       │  接收请求、校验参数、返回响应
│  "前台接待"                                │  不写业务逻辑
├──────────────────────────────────────────┤
│  Service（业务层）                          │  核心业务逻辑、事务管理
│  "业务专员"                                │  不关心 HTTP，也不关心 SQL
├──────────────────────────────────────────┤
│  Domain（领域层）                           │  业务规则、领域模型
│  "业务规则"                                │  纯 Java 对象，不依赖框架
├──────────────────────────────────────────┤
│  Infrastructure（基础设施层）               │  数据库访问（Mapper + DO）
│  "仓库管理员"                              │  只管存取数据
└──────────────────────────────────────────┘
```

### 用 Lumina 真实代码对照

```java
// === Controller 层：只管 HTTP ===
// 文件：AgentController.java
@PostMapping
public R<AgentVO> createAgent(@Valid @RequestBody CreateAgentDTO dto) {
    Agent agent = toDomain(dto);                    // 转换
    Agent created = agentService.createAgent(agent); // 委托 Service
    return R.success(toVO(created));                 // 转换 + 返回
}
// Controller 不知道数据库长什么样，不知道业务规则

// === Service 层：只管业务 ===
// 文件：AgentServiceImpl.java
@Transactional(rollbackFor = Exception.class)
public Agent createAgent(Agent agent) {
    agent.validateName();                // 领域校验
    AgentDO agentDO = toDO(agent);       // 转换
    agentMapper.insert(agentDO);         // 委托 Mapper
    return agent;
}
// Service 不知道 HTTP，不知道 SQL 怎么写

// === Mapper 层：只管数据库 ===
// 文件：AgentMapper.java
public interface AgentMapper extends BaseMapper<AgentDO> {}
// Mapper 连一行代码都不用写
```

---

## 分层的好处

### 1. 可测试

```java
// Service 不依赖 HTTP，可以直接 new 来测
@Test
void testCreateAgent() {
    AgentServiceImpl service = new AgentServiceImpl(mockMapper, ...);
    Agent agent = new Agent();
    agent.setAgentName("测试");
    service.createAgent(agent);
    verify(mockMapper).insert(any());    // 验证调了 insert
}
```

### 2. 可复用

"创建 Agent"的逻辑在 Service 里，**任何地方**都能调用——Controller、定时任务、消息消费者、其他 Service。

### 3. 可替换

想从 MySQL 换成 PostgreSQL？只改 Mapper 层。想从 REST 换成 GraphQL？只改 Controller 层。**业务逻辑一行不改**。

### 4. 职责清晰

看到 bug：数据写错了 → 查 Mapper 层。业务规则错了 → 查 Service 层。接口格式错了 → 查 Controller 层。**定位问题一目了然**。

---

## 为什么是四层不是三层？

传统 Spring 是三层（Controller-Service-Mapper）。Lumina 多了一层 **Domain（领域层）**：

```java
// 文件：lumina-modules/lumina-business-agent/.../domain/model/Agent.java
public class Agent {
    private String agentName;
    private String agentType;

    // 业务规则封装在领域模型里
    public void validateName() {
        if (agentName == null || agentName.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Agent 名称不能为空");
        }
        if (agentName.length() > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "名称不能超过 100 字符");
        }
    }

    public void validateType() {
        if (!AgentTypeEnum.isValid(agentType)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的 Agent 类型");
        }
    }
}
```

**Domain 层的价值**：业务规则不散落在 Service 各处，而是**内聚在领域模型里**。`Agent` 自己知道"自己的名字该怎么校验"。

> 📖 领域模型的详细用法见[第二阶 03-DTO/VO/Domain 模式](03-dto-vo-domain-pattern.md)。

---

## 分层的铁律

1. **上层依赖下层，下层不能依赖上层**
   - Controller 依赖 Service ✅
   - Service 依赖 Mapper ✅
   - Mapper 依赖 Controller ❌（循环依赖）

2. **跨层不能跳过**
   - Controller 直接调 Mapper ❌（跳过了 Service）
   - 除非是纯查询（没有业务逻辑），可以 Controller 直接调 Mapper 的简单查询

3. **DTO/VO/DO 不能跨层传递**
   - Controller 收的是 DTO，传给 Service 要转成 Domain
   - Service 存的是 DO，返回给 Controller 要转成 VO

> ⚠️ 这就是为什么有那么多"转换"（toDomain/toDO/toVO）——下一节详讲。

---

## 动手试试

1. **打开 `AgentController.java`**：确认它只有"接收→转换→委托→返回"，没有业务逻辑
2. **打开 `AgentServiceImpl.java` 的 `createAgent`**：确认它有业务逻辑（校验、转换），但没有 HTTP 相关代码
3. **打开 `AgentMapper.java`**：确认它只有一行（继承 BaseMapper）

---

## 小结

| 层 | 职责 | 类比 | Lumina 示例 |
|----|------|------|-------------|
| Controller | HTTP 接收/响应 | 前台接待 | AgentController |
| Service | 业务逻辑+事务 | 业务专员 | AgentServiceImpl |
| Domain | 业务规则 | 规则手册 | Agent.validateName() |
| Infrastructure | 数据库访问 | 仓库管理员 | AgentMapper |

**核心原则**：每层只管自己的事，上层依赖下层，不跳层，不反向依赖。

---

## 下一步

下一篇 [DTO/VO/Domain 模式](03-dto-vo-domain-pattern.md)——为什么一个 Agent 要分成 DTO、VO、Domain、DO 四种对象？

> 🚀 [03 — DTO/VO/Domain →](03-dto-vo-domain-pattern.md)

---

## 自测题

1. **为什么 Controller 不能直接调 Mapper（跳过 Service）？**
   <details><summary>答案</summary>因为业务逻辑（校验、事务、规则）在 Service 层。跳过 Service 意味着这些逻辑被遗漏。例外：纯查询无业务逻辑时可以跳过。</details>

2. **领域模型（Domain）和传统三层架构比，多解决了什么问题？**
   <details><summary>答案</summary>把业务规则从 Service 里抽出来内聚到领域对象。Agent 自己知道名字该怎么校验，不用在 Service 里到处写 if-else。</details>

3. **如果 Service 直接返回 DO 给 Controller，会有什么问题？**
   <details><summary>答案</summary>DO 包含数据库所有字段（如 deleted、tenant_id），直接返回可能泄露敏感信息或内部结构。而且前端类型和数据库结构强耦合，改表要改前端。</details>

---

📝 **本篇撰写期间修正的代码**：无。
