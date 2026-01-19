---
name: lumina_code_style
description: Use this skill when writing Java code, creating new classes, or reviewing code style. This skill enforces Lumina framework coding standards including naming conventions, package structure, class organization, and code quality practices.
---

# Lumina 代码风格规范

## 功能概述

本技能包用于确保 Lumina 框架项目的代码风格一致性，包括命名规范、包结构、类组织、注释规范等。

## 核心规范

### 包命名规范

```
基础包名: io.lumina
模块包名: io.lumina.{domain}
示例: io.lumina.agent, io.lumina.system, io.lumina.user
```

### 类命名规范

| 类型 | 命名规则 | 示例 |
|------|---------|------|
| **Controller** | `{业务}Controller` | `AgentController`, `UserController` |
| **Service** | `{业务}Service` | `AgentService`, `TaskService` |
| **Service Impl** | `{业务}ServiceImpl` | `AgentServiceImpl` |
| **Entity (Domain)** | `{实体名}` | `Agent`, `Task`, `User` |
| **DO (Data Object)** | `{实体}DO` | `AgentDO`, `TaskDO` |
| **DTO** | `{业务}{操作}DTO` | `CreateAgentDTO`, `QueryAgentDTO` |
| **VO** | `{业务}VO` | `AgentVO`, `TaskVO` |
| **Mapper** | `{实体}Mapper` | `AgentMapper`, `TaskMapper` |
| **Converter** | `{实体}Converter` | `AgentConverter` |
| **Exception** | `{业务}Exception` | `AgentNotFoundException` |
| **Enum** | `{业务}{类型}Enum` | `TaskStatusEnum`, `AgentTypeEnum` |

### 方法命名规范

| 类型 | 命名规则 | 示例 |
|------|---------|------|
| **Controller 方法** | `{操作}{资源}` | `createAgent`, `getAgent`, `listAgents`, `updateAgent`, `deleteAgent` |
| **Service 方法** | `{操作}{实体}` | `createAgent`, `getAgentById`, `listAgents`, `executeTask` |
| **Mapper 方法** | `select{条件}`, `insert`, `update`, `delete` | `selectById`, `selectByName`, `insert`, `deleteById` |
| **Domain 方法** | `{业务动作}` | `executeTask`, `validate`, `activate` |

### 变量命名规范

- **实体对象**: 使用实体名，如 `agent`, `task`, `user`
- **集合**: 使用复数形式，如 `agents`, `tasks`, `users`
- **布尔值**: 使用 `is`/`has` 前缀，如 `isActive`, `hasPermission`
- **常量**: 全大写，下划线分隔，如 `MAX_RETRY_COUNT`, `DEFAULT_TIMEOUT`

### 注释规范

#### 类注释

```java
/**
 * Agent 领域实体
 * 
 * <p>Agent 是系统的核心领域对象，代表一个可执行的 AI 智能体。
 * 它封装了 Agent 的业务逻辑和业务规则。
 * 
 * @author Lumina Team
 * @since 1.0.0
 */
public class Agent {
    // ...
}
```

#### 方法注释

```java
/**
 * 执行 Agent 任务
 * 
 * <p>根据任务配置执行 Agent，支持同步和异步两种模式。
 * 
 * @param taskId 任务ID
 * @param command 执行命令
 * @return 任务执行结果
 * @throws AgentNotFoundException 当 Agent 不存在时
 * @throws TaskExecutionException 当任务执行失败时
 */
public TaskResult executeTask(Long taskId, ExecuteTaskDTO dto) {
    // ...
}
```

## 使用场景

- 创建新的 Java 类时，确保命名符合规范
- 编写方法时，确保方法命名符合规范
- 代码审查时，检查代码风格一致性
- 重构代码时，保持命名规范

## 检查清单

- [ ] 包名是否符合 `io.lumina.{domain}` 格式
- [ ] 类名是否符合命名规范
- [ ] 方法名是否符合命名规范
- [ ] 变量名是否符合命名规范
- [ ] 是否添加了必要的类注释和方法注释
- [ ] 常量是否使用全大写+下划线格式

## 可用资源

- `references/naming-conventions.md`: 详细命名规范说明
- `examples/GoodExample.java`: 符合规范的代码示例
- `examples/BadExample.java`: 不符合规范的代码示例

