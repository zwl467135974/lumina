---
name: lumina_mybatis_plus
description: Use this skill when writing database access code, creating Mapper interfaces, or writing SQL queries. This skill enforces MyBatis-Plus usage priority, SQL writing restrictions, and data persistence best practices.
---

# Lumina MyBatis-Plus 使用规范

## 功能概述

本技能包用于确保 Lumina 框架项目正确使用 MyBatis-Plus，包括基础 CRUD、复杂查询、分页、批量操作等。

## 核心原则

1. **优先使用 MyBatis-Plus** - 所有简单 CRUD 操作必须使用 MyBatis-Plus
2. **复杂 SQL 才用 XML** - 仅当 MyBatis-Plus 无法满足时，才使用 XML Mapper
3. **禁止 SQL 函数和存储过程** - 不允许在 SQL 中编写函数、存储过程、触发器
4. **业务逻辑在 Java 代码中** - 所有业务逻辑必须在 Java 代码中实现，不允许写在 SQL 中

## MyBatis-Plus 基础使用

### Mapper 接口

```java
/**
 * Agent Mapper - 继承 BaseMapper，获得基础 CRUD 能力
 */
@Mapper
public interface AgentMapper extends BaseMapper<AgentDO> {
    
    // 基础 CRUD 方法已由 BaseMapper 提供，无需重复定义：
    // - insert(AgentDO entity)
    // - deleteById(Serializable id)
    // - updateById(AgentDO entity)
    // - selectById(Serializable id)
    // - selectList(Wrapper<AgentDO> queryWrapper)
    // - selectPage(IPage<AgentDO> page, Wrapper<AgentDO> queryWrapper)
    // - selectCount(Wrapper<AgentDO> queryWrapper)
    
    // 复杂查询才需要自定义方法（见 5.2.2）
}
```

### 常用操作示例

#### 条件查询

```java
// 根据名称查询
AgentDO agentDO = agentMapper.selectOne(
    new LambdaQueryWrapper<AgentDO>()
        .eq(AgentDO::getAgentName, agentName)
        .eq(AgentDO::getDeleted, 0)
);

// 条件查询列表
LambdaQueryWrapper<AgentDO> wrapper = new LambdaQueryWrapper<AgentDO>()
    .eq(AgentDO::getDeleted, 0);
if (StringUtils.isNotBlank(dto.getAgentName())) {
    wrapper.like(AgentDO::getAgentName, dto.getAgentName());
}
List<AgentDO> list = agentMapper.selectList(wrapper);
```

#### 分页查询

```java
// 创建分页对象
Page<AgentDO> page = new Page<>(dto.getPageNum(), dto.getPageSize());

// 构建查询条件
LambdaQueryWrapper<AgentDO> wrapper = new LambdaQueryWrapper<AgentDO>()
    .eq(AgentDO::getDeleted, 0)
    .orderByDesc(AgentDO::getCreateTime);

// 执行分页查询
Page<AgentDO> result = agentMapper.selectPage(page, wrapper);
```

#### 条件更新

```java
// 更新状态
agentMapper.update(null,
    new LambdaUpdateWrapper<AgentDO>()
        .set(AgentDO::getStatus, status.getValue())
        .set(AgentDO::getUpdateTime, LocalDateTime.now())
        .eq(AgentDO::getAgentId, agentId)
        .eq(AgentDO::getDeleted, 0)
);
```

#### 软删除

```java
// 软删除
agentMapper.update(null,
    new LambdaUpdateWrapper<AgentDO>()
        .set(AgentDO::getDeleted, 1)
        .set(AgentDO::getUpdateTime, LocalDateTime.now())
        .eq(AgentDO::getAgentId, agentId)
);
```

## 何时使用 XML Mapper

**✅ 可以使用 XML 的场景**:
- 多表关联查询（JOIN）
- 复杂的子查询
- 统计聚合查询（GROUP BY、HAVING）
- 动态 SQL 条件过于复杂，QueryWrapper 难以表达

**❌ 禁止使用 XML 的场景**:
- 简单的 CRUD 操作（必须使用 MyBatis-Plus）
- 单表条件查询（必须使用 QueryWrapper）
- 包含 SQL 函数、存储过程、触发器
- 包含业务逻辑判断

## SQL 编写禁止事项

### 禁止使用 SQL 函数

```xml
<!-- ❌ 错误：不允许使用 SQL 函数 -->
<select id="selectAgents">
    SELECT 
        agent_id,
        agent_name,
        DATE_FORMAT(create_time, '%Y-%m-%d') AS createDate,  <!-- 禁止 -->
        CONCAT(agent_name, '-', agent_type) AS displayName     <!-- 禁止 -->
    FROM lumina_agent
</select>
```

**替代方案**: 在 Java 代码中处理日期格式化和字符串拼接

### 禁止使用存储过程和触发器

```sql
-- ❌ 禁止：存储过程
CREATE PROCEDURE get_agent_statistics(IN tenant_id BIGINT)
BEGIN
    SELECT COUNT(*) FROM lumina_agent WHERE tenant_id = tenant_id;
END;

-- ❌ 禁止：触发器
CREATE TRIGGER update_agent_time
BEFORE UPDATE ON lumina_agent
FOR EACH ROW
BEGIN
    SET NEW.update_time = NOW();
END;
```

**替代方案**:
- 存储过程 → 使用 Java 代码 + MyBatis-Plus
- 触发器 → 使用 MyBatis-Plus 自动填充或 Java 代码处理

### 禁止在 SQL 中写业务逻辑

```xml
<!-- ❌ 错误：业务逻辑写在 SQL 中 -->
<select id="selectActiveAgents">
    SELECT * FROM lumina_agent
    WHERE deleted = 0
      AND status = 1
      AND (CASE 
            WHEN agent_type = 'PREMIUM' THEN create_time > DATE_SUB(NOW(), INTERVAL 30 DAY)
            WHEN agent_type = 'BASIC' THEN create_time > DATE_SUB(NOW(), INTERVAL 7 DAY)
            ELSE TRUE
          END)  <!-- 禁止：业务逻辑判断 -->
</select>
```

**替代方案**: 业务逻辑在 Java 代码中处理

## 实体类注解规范

```java
@Data
@TableName("lumina_agent")
public class AgentDO {
    
    @TableId(value = "agent_id", type = IdType.AUTO)
    private Long agentId;
    
    @TableField("agent_name")
    private String agentName;
    
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
    
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
```

## 使用场景

- 创建 Mapper 接口时，优先使用 MyBatis-Plus
- 编写查询代码时，优先使用 QueryWrapper
- 需要复杂查询时，才使用 XML Mapper
- 代码审查时，检查是否违反 SQL 规范

## 检查清单

- [ ] 简单 CRUD 是否使用 MyBatis-Plus
- [ ] 是否在 SQL 中使用了函数、存储过程、触发器
- [ ] 业务逻辑是否在 Java 代码中，而非 SQL 中
- [ ] 分页是否使用 MyBatis-Plus 的 Page 对象
- [ ] 软删除是否使用逻辑删除字段
- [ ] 实体类是否正确使用注解

## 可用资源

- `references/mybatis-plus-guide.md`: MyBatis-Plus 详细使用指南
- `examples/basic-crud.java`: 基础 CRUD 示例
- `examples/complex-query.xml`: 复杂查询 XML 示例
- `examples/bad-sql-examples.sql`: 禁止的 SQL 示例

