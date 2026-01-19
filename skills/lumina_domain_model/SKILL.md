---
name: lumina_domain_model
description: Use this skill when designing domain entities, creating business logic, or implementing domain methods. This skill enforces domain-driven design principles with simplified implementation, ensuring business logic is encapsulated in domain entities.
---

# Lumina 领域模型实践规范

## 功能概述

本技能包用于确保 Lumina 框架项目的领域模型设计符合规范，包括实体设计、业务方法封装、值对象使用等。

## 领域实体设计

### 简单场景（推荐）

```java
/**
 * Agent 领域实体 - 简单场景示例
 * 
 * <p>简单场景：直接使用基本类型，不封装 Value Object
 */
@Data
public class Agent {
    
    private Long agentId;
    private String agentName;  // 直接使用 String
    private AgentType agentType;  // 使用枚举
    private AgentStatus status;
    
    /**
     * 执行任务 - 领域方法
     * 
     * <p>业务逻辑封装在领域实体中
     */
    public TaskResult executeTask(Task task) {
        // 业务规则校验
        if (!this.canExecute()) {
            throw new AgentCannotExecuteException("Agent当前状态无法执行任务");
        }
        
        // 业务逻辑
        return task.execute(this);
    }
    
    /**
     * 是否可以执行 - 业务规则
     */
    public boolean canExecute() {
        return status == AgentStatus.ACTIVE;
    }
    
    /**
     * 激活 Agent - 领域方法
     */
    public void activate() {
        if (this.status == AgentStatus.ACTIVE) {
            throw new IllegalStateException("Agent已经是激活状态");
        }
        this.status = AgentStatus.ACTIVE;
    }
    
    /**
     * 停用 Agent - 领域方法
     */
    public void deactivate() {
        if (this.status == AgentStatus.INACTIVE) {
            throw new IllegalStateException("Agent已经是停用状态");
        }
        this.status = AgentStatus.INACTIVE;
    }
}
```

### 复杂场景（可选）

```java
/**
 * 复杂场景：使用 Value Object 封装业务规则
 * 
 * <p>当字段需要封装业务规则时，使用 Value Object
 */
@Value
public class AgentName {
    private final String value;
    
    public AgentName(String value) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException("Agent名称不能为空");
        }
        if (value.length() > 100) {
            throw new IllegalArgumentException("Agent名称长度不能超过100");
        }
        this.value = value;
    }
}

/**
 * 复杂场景的 Entity 使用 Value Object
 */
@Data
public class Agent {
    private AgentId agentId;  // Value Object
    private AgentName agentName;  // Value Object
    private AgentType agentType;
    private AgentStatus status;
    
    // ... 业务方法
}
```

## 业务服务设计

```java
/**
 * Agent 业务服务
 * 
 * <p>合并了应用服务和领域服务的职责
 */
@Service
@Transactional
public class AgentService {
    
    private final AgentMapper agentMapper;
    private final AgentConverter agentConverter;  // 可选
    
    /**
     * 创建 Agent
     */
    public AgentVO createAgent(CreateAgentDTO dto) {
        // 1. 参数校验（DTO 已通过 @Valid 校验）
        
        // 2. 业务规则校验
        AgentDO existingAgent = agentMapper.selectOne(
            new LambdaQueryWrapper<AgentDO>()
                .eq(AgentDO::getAgentName, dto.getAgentName())
                .eq(AgentDO::getDeleted, 0)
        );
        if (existingAgent != null) {
            throw new BusinessException("Agent名称已存在");
        }
        
        // 3. 创建领域对象
        Agent agent = new Agent();
        agent.setAgentName(dto.getAgentName());
        agent.setAgentType(AgentType.valueOf(dto.getAgentType()));
        agent.setStatus(AgentStatus.INACTIVE);
        
        // 4. 持久化
        AgentDO agentDO = convertToDO(agent);
        agentMapper.insert(agentDO);
        agent.setAgentId(agentDO.getAgentId());
        
        // 5. 转换为 VO 返回
        return convertToVO(agent);
    }
    
    /**
     * 执行 Agent 任务
     */
    public TaskResult executeTask(Long agentId, ExecuteTaskDTO dto) {
        // 1. 查询 Agent
        AgentDO agentDO = agentMapper.selectById(agentId);
        if (agentDO == null || agentDO.getDeleted() == 1) {
            throw new NotFoundException("Agent不存在");
        }
        Agent agent = convertToDomain(agentDO);
        
        // 2. 创建 Task
        Task task = new Task();
        task.setTaskName(dto.getTaskName());
        task.setTaskConfig(dto.getTaskConfig());
        
        // 3. 调用领域方法（业务逻辑在 Domain 中）
        TaskResult result = agent.executeTask(task);
        
        // 4. 持久化结果
        // ...
        
        return result;
    }
}
```

## 转换器设计（可选）

```java
/**
 * Agent 转换器
 * 
 * <p>简单场景可以直接转换，复杂场景使用转换器
 * 当 DO 和 Domain 字段差异较大时，建议使用转换器
 */
@Component
public class AgentConverter {
    
    /**
     * Domain -> DO
     */
    public AgentDO toDO(Agent agent) {
        if (agent == null) {
            return null;
        }
        AgentDO agentDO = new AgentDO();
        agentDO.setAgentId(agent.getAgentId());
        agentDO.setAgentName(agent.getAgentName());
        agentDO.setAgentType(agent.getAgentType().getValue());
        agentDO.setStatus(agent.getStatus().getValue());
        return agentDO;
    }
    
    /**
     * DO -> Domain
     */
    public Agent toDomain(AgentDO agentDO) {
        if (agentDO == null) {
            return null;
        }
        Agent agent = new Agent();
        agent.setAgentId(agentDO.getAgentId());
        agent.setAgentName(agentDO.getAgentName());
        agent.setAgentType(AgentType.valueOf(agentDO.getAgentType()));
        agent.setStatus(AgentStatus.valueOf(agentDO.getStatus()));
        return agent;
    }
}
```

## DO 和 Domain 合并使用（可选）

```java
/**
 * 当 DO 和 Domain 字段完全一致时，可以合并使用
 * 
 * <p>适用场景：简单业务，DO 和 Domain 字段一致，无需转换
 */
@Data
@TableName("lumina_agent")
public class Agent {  // 既是 DO，也是 Domain
    
    @TableId(value = "agent_id", type = IdType.AUTO)
    private Long agentId;
    
    @TableField("agent_name")
    private String agentName;
    
    /**
     * 领域方法
     */
    public boolean canExecute() {
        return status == 1;  // 1-启用
    }
    
    /**
     * 领域方法
     */
    public void activate() {
        this.status = 1;
    }
}
```

## 核心原则

1. **业务逻辑封装在 Domain 中** - Entity 包含业务方法和业务规则
2. **Domain 不依赖其他层** - 保持领域模型的纯净
3. **简单场景优先** - 直接使用基本类型，无需 Value Object
4. **Converter 可选** - 简单场景直接转换，复杂场景使用 Converter

## 使用场景

- 设计领域实体时，确保业务逻辑封装在实体中
- 创建业务服务时，确保调用领域方法而非直接操作
- 代码审查时，检查业务逻辑是否在正确的位置
- 重构代码时，确保领域模型保持纯净

## 检查清单

- [ ] 业务逻辑是否封装在 Domain 实体中
- [ ] Domain 是否不依赖其他层
- [ ] Service 是否调用 Domain 方法而非直接操作
- [ ] 简单场景是否直接使用基本类型
- [ ] 复杂场景是否使用 Value Object
- [ ] Converter 是否只在需要时使用

## 可用资源

- `references/domain-design-principles.md`: 领域设计原则
- `examples/simple-entity.java`: 简单场景实体示例
- `examples/complex-entity.java`: 复杂场景实体示例
- `examples/service-usage.java`: Service 使用示例

