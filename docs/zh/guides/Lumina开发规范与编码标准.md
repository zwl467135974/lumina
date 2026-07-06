# Lumina 框架开发规范与编码标准

## 一、设计原则

### 1.1 核心原则

1. **简化分层架构** - 业务驱动开发，以领域模型为核心，但简化实现
2. **分层清晰** - 清晰的层次划分，职责单一
3. **统一规范** - 前后端统一的接口规范、命名规范
4. **代码质量** - 可读性、可维护性、可扩展性优先
5. **实用主义** - 架构服务于业务，避免过度设计

---

## 二、简化分层架构规范

### 2.1 标准分层结构

```
lumina-modules/
└── lumina-{domain}/
    └── src/main/java/io/lumina/
        └── {domain}/
            ├── api/                    # 接口层 (API Layer)
            │   ├── controller/         # REST 控制器
            │   └── dto/                # 数据传输对象 (Request/Response)
            │
            ├── service/                 # 业务服务层 (Service Layer) - 核心
            │   ├── {业务}Service.java  # 业务服务接口
            │   └── impl/                # 业务服务实现
            │
            ├── domain/                 # 领域模型层 (Domain Layer)
            │   ├── model/               # 领域实体（包含业务方法）
            │   └── enums/               # 领域枚举
            │   │
            │   # 以下为可选（根据业务复杂度决定是否使用）
            │   ├── valueobject/        # 值对象（复杂场景使用）
            │   ├── event/              # 领域事件（如需要事件驱动）
            │   └── repository/         # 仓储接口（如需要抽象持久化）
            │
            └── infrastructure/          # 基础设施层 (Infrastructure Layer)
                ├── mapper/              # MyBatis Mapper
                ├── entity/              # 数据库实体 (DO - Data Object)
                └── converter/           # DO <-> Domain 转换器（可选）
                │
                # 以下为可选
                ├── external/            # 外部服务调用
                └── config/              # 配置类
```

### 2.2 层次职责说明

#### 2.2.1 API 层 (接口层)
- **职责**: 处理 HTTP 请求/响应，参数校验，权限控制
- **包含**: Controller、DTO (Request/Response)
- **规则**:
  - Controller 只负责接收请求、调用 Service、返回响应
  - 不包含业务逻辑
  - 使用 DTO 进行数据传输

#### 2.2.2 Service 层 (业务服务层) - 核心
- **职责**: 业务逻辑处理，事务管理，业务流程编排
- **包含**: Service 接口和实现
- **规则**:
  - 包含应用服务逻辑（编排、事务）
  - 包含领域服务逻辑（复杂业务规则）
  - 调用 Domain 模型的方法
  - 调用 Infrastructure 的 Mapper
  - 负责 DTO 与 Domain 对象的转换

#### 2.2.3 Domain 层 (领域模型层)
- **职责**: 领域实体和业务规则
- **包含**: Entity、枚举
- **可选**: Value Object、Domain Event、Repository 接口（根据业务复杂度决定）
- **规则**:
  - Entity 包含业务方法和业务规则
  - 不依赖其他层（保持领域模型的纯净）
  - **简单场景**: 直接使用基本类型（String、Long 等）
  - **复杂场景**: 使用 Value Object 封装业务规则
  - Repository 接口可选，简单场景直接使用 Mapper

#### 2.2.4 Infrastructure 层 (基础设施层)
- **职责**: 技术实现，数据库访问，外部服务调用
- **包含**: Mapper、DO、Converter（可选）
- **规则**:
  - DO (Data Object) 对应数据库表结构
  - 负责 DO 与 Domain Entity 的转换（简单场景可以直接转换）
  - 如果 DO 和 Domain 字段一致，可以合并使用

---

## 三、命名规范

### 3.1 包命名规范

```
基础包名: io.lumina

模块包名: io.lumina.{domain}
示例: io.lumina.agent, io.lumina.system, io.lumina.user
```

### 3.2 类命名规范

| 类型 | 命名规则 | 示例 | 说明 |
|------|---------|------|------|
| **Controller** | `{业务}Controller` | `AgentController`, `UserController` | REST 接口控制器 |
| **Service** | `{业务}Service` | `AgentService`, `TaskService` | 业务服务（合并应用服务和领域服务） |
| **Service Impl** | `{业务}ServiceImpl` | `AgentServiceImpl` | 业务服务实现 |
| **Entity (Domain)** | `{实体名}` | `Agent`, `Task`, `User` | 领域实体 |
| **DO (Data Object)** | `{实体}DO` | `AgentDO`, `TaskDO` | 数据库实体 |
| **DTO** | `{业务}{操作}DTO` | `CreateAgentDTO`, `QueryAgentDTO` | 数据传输对象 |
| **VO** | `{业务}VO` | `AgentVO`, `TaskVO` | 视图对象 (仅用于展示) |
| **Mapper** | `{实体}Mapper` | `AgentMapper`, `TaskMapper` | MyBatis Mapper |
| **Converter** | `{实体}Converter` | `AgentConverter` | DO/Domain 转换器（可选） |
| **Exception** | `{业务}Exception` | `AgentNotFoundException` | 领域异常 |
| **Enum** | `{业务}{类型}Enum` | `TaskStatusEnum`, `AgentTypeEnum` | 枚举类 |

### 3.3 方法命名规范

| 类型 | 命名规则 | 示例 |
|------|---------|------|
| **Controller 方法** | `{操作}{资源}` | `createAgent`, `getAgent`, `listAgents`, `updateAgent`, `deleteAgent` |
| **Service 方法** | `{操作}{实体}` | `createAgent`, `getAgentById`, `listAgents`, `executeTask` |
| **Mapper 方法** | `select{条件}`, `insert`, `update`, `delete` | `selectById`, `selectByName`, `insert`, `deleteById` |
| **Domain 方法** | `{业务动作}` | `executeTask`, `validate`, `activate` |

### 3.4 变量命名规范

- **实体对象**: 使用实体名，如 `agent`, `task`, `user`
- **集合**: 使用复数形式，如 `agents`, `tasks`, `users`
- **布尔值**: 使用 `is`/`has` 前缀，如 `isActive`, `hasPermission`
- **常量**: 全大写，下划线分隔，如 `MAX_RETRY_COUNT`, `DEFAULT_TIMEOUT`

### 3.5 数据库命名规范

| 类型 | 命名规则 | 示例 |
|------|---------|------|
| **表名** | `{模块}_{实体名}`，小写，下划线分隔 | `lumina_agent`, `lumina_task`, `lumina_user` |
| **字段名** | 小写，下划线分隔 | `agent_id`, `agent_name`, `create_time` |
| **主键** | `{实体名}_id` | `agent_id`, `task_id` |
| **外键** | `{关联实体}_id` | `user_id`, `tenant_id` |
| **索引** | `idx_{表名}_{字段名}` | `idx_agent_name`, `idx_task_status` |
| **唯一索引** | `uk_{表名}_{字段名}` | `uk_agent_code` |

---

## 四、前后端接口规范

### 4.1 RESTful API 设计规范

#### 4.1.1 URL 设计

```
基础路径: /api/v1/{domain}

资源操作:
GET    /api/v1/agents              # 列表查询
GET    /api/v1/agents/{id}          # 详情查询
POST   /api/v1/agents              # 创建
PUT    /api/v1/agents/{id}          # 更新
DELETE /api/v1/agents/{id}          # 删除

特殊操作:
POST   /api/v1/agents/{id}/execute  # 执行 Agent
POST   /api/v1/agents/{id}/pause     # 暂停 Agent
```

#### 4.1.2 HTTP 方法使用

| 方法 | 用途 | 幂等性 |
|------|------|--------|
| **GET** | 查询资源 | ✅ 幂等 |
| **POST** | 创建资源或执行操作 | ❌ 非幂等 |
| **PUT** | 完整更新资源 | ✅ 幂等 |
| **PATCH** | 部分更新资源 | ⚠️ 建议幂等 |
| **DELETE** | 删除资源 | ✅ 幂等 |

#### 4.1.3 统一响应格式

```java
// 成功响应
{
  "code": 200,
  "msg": "操作成功",
  "data": { ... },
  "timestamp": 1704067200000
}

// 失败响应
{
  "code": 400,
  "msg": "参数错误",
  "data": null,
  "timestamp": 1704067200000,
  "errors": [
    {
      "field": "agentName",
      "message": "Agent名称不能为空"
    }
  ]
}

// 分页响应
{
  "code": 200,
  "msg": "查询成功",
  "data": {
    "list": [ ... ],
    "total": 100,
    "pageNum": 1,
    "pageSize": 10,
    "pages": 10
  },
  "timestamp": 1704067200000
}
```

#### 4.1.4 状态码规范

| 状态码 | 含义 | 使用场景 |
|--------|------|---------|
| **200** | 成功 | 所有成功操作 |
| **400** | 参数错误 | 请求参数校验失败 |
| **401** | 未授权 | 未登录或 Token 过期 |
| **403** | 无权限 | 无操作权限 |
| **404** | 资源不存在 | 查询的资源不存在 |
| **409** | 资源冲突 | 资源已存在或状态冲突 |
| **500** | 服务器错误 | 系统内部错误 |

### 4.2 DTO 设计规范

#### 4.2.1 Request DTO

```java
// 创建请求
@Data
@EqualsAndHashCode(callSuper = false)
public class CreateAgentDTO {
    
    @NotBlank(message = "Agent名称不能为空")
    @Size(max = 100, message = "Agent名称长度不能超过100")
    private String agentName;
    
    @NotNull(message = "Agent类型不能为空")
    private AgentTypeEnum agentType;
    
    @Size(max = 500, message = "描述长度不能超过500")
    private String description;
}

// 查询请求
@Data
@EqualsAndHashCode(callSuper = false)
public class QueryAgentDTO {
    
    private String agentName;
    
    private AgentTypeEnum agentType;
    
    @Min(value = 1, message = "页码必须大于0")
    private Integer pageNum = 1;
    
    @Min(value = 1, message = "每页数量必须大于0")
    @Max(value = 100, message = "每页数量不能超过100")
    private Integer pageSize = 10;
}
```

#### 4.2.2 Response DTO / VO

```java
// 响应对象
@Data
public class AgentVO {
    
    private Long agentId;
    
    private String agentName;
    
    private AgentTypeEnum agentType;
    
    private String description;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
```

### 4.3 参数校验规范

```java
// Controller 层使用 @Valid 注解
@PostMapping("/agents")
public R<AgentVO> createAgent(@Valid @RequestBody CreateAgentDTO dto) {
    // ...
}

// 自定义校验注解
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AgentCodeValidator.class)
public @interface ValidAgentCode {
    String message() default "Agent编码格式不正确";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
```

---

## 五、数据持久化规范

### 5.0 核心原则

1. **优先使用 MyBatis-Plus** - 所有简单 CRUD 操作必须使用 MyBatis-Plus
2. **复杂 SQL 才用 XML** - 仅当 MyBatis-Plus 无法满足时，才使用 XML Mapper
3. **禁止 SQL 函数和存储过程** - 不允许在 SQL 中编写函数、存储过程、触发器
4. **业务逻辑在 Java 代码中** - 所有业务逻辑必须在 Java 代码中实现，不允许写在 SQL 中

### 5.1 MyBatis-Plus 优先使用规范

#### 5.1.1 MyBatis-Plus 基础使用

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

#### 5.1.2 MyBatis-Plus 常用操作示例

```java
@Service
public class AgentService {
    
    @Autowired
    private AgentMapper agentMapper;
    
    @Autowired(required = false)
    private AgentConverter agentConverter;  // 可选，简单场景可以直接转换
    
    /**
     * 保存 Agent - 使用 MyBatis-Plus
     */
    public void saveAgent(Agent agent) {
        AgentDO agentDO = convertToDO(agent);
        agentMapper.insert(agentDO);
        agent.setAgentId(agentDO.getAgentId());
    }
    
    /**
     * 根据 ID 查询 - 使用 MyBatis-Plus
     */
    public Agent getAgentById(Long agentId) {
        AgentDO agentDO = agentMapper.selectById(agentId);
        if (agentDO == null) {
            return null;
        }
        return convertToDomain(agentDO);
    }
    
    /**
     * 根据名称查询 - 使用 MyBatis-Plus QueryWrapper
     */
    public Agent getAgentByName(String agentName) {
        AgentDO agentDO = agentMapper.selectOne(
            new LambdaQueryWrapper<AgentDO>()
                .eq(AgentDO::getAgentName, agentName)
                .eq(AgentDO::getDeleted, 0)
        );
        return agentDO != null ? convertToDomain(agentDO) : null;
    }
    
    /**
     * 条件查询列表 - 使用 MyBatis-Plus QueryWrapper
     */
    public List<Agent> listAgents(QueryAgentDTO dto) {
        LambdaQueryWrapper<AgentDO> wrapper = new LambdaQueryWrapper<AgentDO>()
            .eq(AgentDO::getDeleted, 0);
        
        // 动态条件
        if (StringUtils.isNotBlank(dto.getAgentName())) {
            wrapper.like(AgentDO::getAgentName, dto.getAgentName());
        }
        if (dto.getAgentType() != null) {
            wrapper.eq(AgentDO::getAgentType, dto.getAgentType());
        }
        
        wrapper.orderByDesc(AgentDO::getCreateTime);
        
        List<AgentDO> list = agentMapper.selectList(wrapper);
        return list.stream()
            .map(this::convertToDomain)
            .collect(Collectors.toList());
    }
    
    /**
     * 分页查询 - 使用 MyBatis-Plus Page
     */
    public PageResult<Agent> pageAgents(QueryAgentDTO dto) {
        // 创建分页对象
        Page<AgentDO> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        
        // 构建查询条件
        LambdaQueryWrapper<AgentDO> wrapper = new LambdaQueryWrapper<AgentDO>()
            .eq(AgentDO::getDeleted, 0);
        
        if (StringUtils.isNotBlank(dto.getAgentName())) {
            wrapper.like(AgentDO::getAgentName, dto.getAgentName());
        }
        if (dto.getAgentType() != null) {
            wrapper.eq(AgentDO::getAgentType, dto.getAgentType());
        }
        
        wrapper.orderByDesc(AgentDO::getCreateTime);
        
        // 执行分页查询
        Page<AgentDO> result = agentMapper.selectPage(page, wrapper);
        
        // 转换为领域对象
        List<Agent> agents = result.getRecords().stream()
            .map(this::convertToDomain)
            .collect(Collectors.toList());
        
        return PageResult.of(agents, result.getTotal());
    }
    
    /**
     * 更新 - 使用 MyBatis-Plus
     */
    public void updateAgent(Agent agent) {
        AgentDO agentDO = convertToDO(agent);
        agentMapper.updateById(agentDO);
    }
    
    /**
     * 条件更新 - 使用 MyBatis-Plus UpdateWrapper
     */
    public void updateAgentStatus(Long agentId, AgentStatus status) {
        agentMapper.update(null,
            new LambdaUpdateWrapper<AgentDO>()
                .set(AgentDO::getStatus, status.getValue())
                .set(AgentDO::getUpdateTime, LocalDateTime.now())
                .eq(AgentDO::getAgentId, agentId)
                .eq(AgentDO::getDeleted, 0)
        );
    }
    
    /**
     * 软删除 - 使用 MyBatis-Plus
     */
    public void deleteAgent(Long agentId) {
        agentMapper.update(null,
            new LambdaUpdateWrapper<AgentDO>()
                .set(AgentDO::getDeleted, 1)
                .set(AgentDO::getUpdateTime, LocalDateTime.now())
                .eq(AgentDO::getAgentId, agentId)
        );
    }
    
    /**
     * 转换方法 - 简单场景可以直接转换，复杂场景使用 Converter
     */
    private AgentDO convertToDO(Agent agent) {
        if (agentConverter != null) {
            return agentConverter.toDO(agent);
        }
        // 简单场景：直接转换
        AgentDO agentDO = new AgentDO();
        agentDO.setAgentId(agent.getAgentId());
        agentDO.setAgentName(agent.getAgentName());
        agentDO.setAgentType(agent.getAgentType().getValue());
        agentDO.setStatus(agent.getStatus().getValue());
        return agentDO;
    }
    
    private Agent convertToDomain(AgentDO agentDO) {
        if (agentConverter != null) {
            return agentConverter.toDomain(agentDO);
        }
        // 简单场景：直接转换
        Agent agent = new Agent();
        agent.setAgentId(agentDO.getAgentId());
        agent.setAgentName(agentDO.getAgentName());
        agent.setAgentType(AgentType.valueOf(agentDO.getAgentType()));
        agent.setStatus(AgentStatus.valueOf(agentDO.getStatus()));
        return agent;
    }
}
```

#### 5.1.3 MyBatis-Plus 配置

```java
@Configuration
@MapperScan("io.lumina.**.infrastructure.persistence.mapper")
public class MyBatisPlusConfig {
    
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        
        // 分页插件
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        
        // 乐观锁插件（如需要）
        // interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        
        return interceptor;
    }
    
    @Bean
    public GlobalConfig globalConfig() {
        GlobalConfig globalConfig = new GlobalConfig();
        GlobalConfig.DbConfig dbConfig = new GlobalConfig.DbConfig();
        
        // 逻辑删除字段
        dbConfig.setLogicDeleteField("deleted");
        dbConfig.setLogicDeleteValue("1");
        dbConfig.setLogicNotDeleteValue("0");
        
        // 自动填充（创建时间、更新时间）
        globalConfig.setDbConfig(dbConfig);
        return globalConfig;
    }
}
```

#### 5.1.4 实体类注解规范

```java
/**
 * Agent 数据库实体 (DO)
 */
@Data
@TableName("lumina_agent")
public class AgentDO {
    
    /**
     * 主键
     */
    @TableId(value = "agent_id", type = IdType.AUTO)
    private Long agentId;
    
    /**
     * Agent 名称
     */
    @TableField("agent_name")
    private String agentName;
    
    /**
     * Agent 类型
     */
    @TableField("agent_type")
    private String agentType;
    
    /**
     * 描述
     */
    @TableField("description")
    private String description;
    
    /**
     * 状态
     */
    @TableField("status")
    private Integer status;
    
    /**
     * 租户ID
     */
    @TableField("tenant_id")
    private Long tenantId;
    
    /**
     * 创建者
     */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private String createBy;
    
    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    /**
     * 更新者
     */
    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
    
    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    
    /**
     * 删除标志（逻辑删除）
     */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
    
    /**
     * 备注
     */
    @TableField("remark")
    private String remark;
    
    /**
     * 不映射到数据库的字段
     */
    @TableField(exist = false)
    private String tempField;
}
```

### 5.2 复杂 SQL 使用 XML（仅限复杂场景）

#### 5.2.1 何时使用 XML Mapper

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

#### 5.2.2 Mapper 接口规范（复杂查询）

```java
@Mapper
public interface AgentMapper extends BaseMapper<AgentDO> {
    
    /**
     * 复杂查询：统计各类型 Agent 数量（需要 GROUP BY）
     * 此类复杂查询才使用 XML
     */
    List<AgentTypeStatistics> selectTypeStatistics(@Param("tenantId") Long tenantId);
    
    /**
     * 复杂查询：关联查询 Agent 和 Task 信息（需要 JOIN）
     */
    List<AgentWithTaskVO> selectAgentWithTasks(@Param("agentId") Long agentId);
}
```

#### 5.2.3 XML Mapper 规范（仅复杂查询）

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" 
    "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="io.lumina.agent.infrastructure.persistence.mapper.AgentMapper">
    
    <!-- 结果映射 -->
    <resultMap id="AgentTypeStatisticsMap" type="io.lumina.agent.domain.vo.AgentTypeStatistics">
        <result column="agent_type" property="agentType"/>
        <result column="count" property="count"/>
    </resultMap>
    
    <!-- 复杂查询：统计各类型数量 -->
    <select id="selectTypeStatistics" resultMap="AgentTypeStatisticsMap">
        SELECT 
            agent_type,
            COUNT(*) AS count
        FROM lumina_agent
        WHERE deleted = 0
          AND tenant_id = #{tenantId}
        GROUP BY agent_type
        ORDER BY count DESC
    </select>
    
    <!-- 复杂查询：关联查询 -->
    <select id="selectAgentWithTasks" resultType="io.lumina.agent.domain.vo.AgentWithTaskVO">
        SELECT 
            a.agent_id AS agentId,
            a.agent_name AS agentName,
            t.task_id AS taskId,
            t.task_name AS taskName,
            t.status AS taskStatus
        FROM lumina_agent a
        LEFT JOIN lumina_task t ON a.agent_id = t.agent_id
        WHERE a.agent_id = #{agentId}
          AND a.deleted = 0
          AND (t.deleted = 0 OR t.deleted IS NULL)
        ORDER BY t.create_time DESC
    </select>
</mapper>
```

### 5.3 SQL 编写禁止事项

#### 5.3.1 禁止使用 SQL 函数

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

<!-- ✅ 正确：在 Java 代码中处理 -->
<select id="selectAgents">
    SELECT 
        agent_id,
        agent_name,
        create_time,
        agent_type
    FROM lumina_agent
</select>
```

```java
// Java 代码中处理
List<AgentDO> list = agentMapper.selectList(wrapper);
list.forEach(agent -> {
    // 日期格式化在 Java 中处理
    String createDate = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        .format(agent.getCreateTime());
    // 字符串拼接在 Java 中处理
    String displayName = agent.getAgentName() + "-" + agent.getAgentType();
});
```

#### 5.3.2 禁止使用存储过程和触发器

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

#### 5.3.3 禁止在 SQL 中写业务逻辑

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

<!-- ✅ 正确：业务逻辑在 Java 代码中 -->
<select id="selectAgents">
    SELECT * FROM lumina_agent
    WHERE deleted = 0
      AND status = 1
</select>
```

```java
// Java 代码中处理业务逻辑
public List<Agent> findActiveAgents() {
    List<AgentDO> list = agentMapper.selectList(
        new LambdaQueryWrapper<AgentDO>()
            .eq(AgentDO::getDeleted, 0)
            .eq(AgentDO::getStatus, 1)
    );
    
    // 业务逻辑在 Java 中处理
    LocalDateTime now = LocalDateTime.now();
    return list.stream()
        .filter(agent -> {
            if (AgentType.PREMIUM.equals(agent.getAgentType())) {
                return agent.getCreateTime().isAfter(now.minusDays(30));
            } else if (AgentType.BASIC.equals(agent.getAgentType())) {
                return agent.getCreateTime().isAfter(now.minusDays(7));
            }
            return true;
        })
        .map(this::convertToDomain)
        .collect(Collectors.toList());
}
```

### 5.4 SQL 编写规范（XML Mapper 场景）

#### 5.4.1 基本规范（仅复杂查询使用）

1. **使用参数化查询** - 防止 SQL 注入
   ```xml
   <!-- ✅ 正确 -->
   WHERE agent_name = #{name}
   
   <!-- ❌ 错误 -->
   WHERE agent_name = '${name}'
   ```

2. **使用 `<where>` 标签** - 动态 SQL
   ```xml
   <where>
       deleted = 0
       <if test="name != null">
           AND agent_name = #{name}
       </if>
   </where>
   ```

3. **使用 `<include>` 标签** - 复用 SQL 片段
   ```xml
   <sql id="Base_Column_List">
       agent_id, agent_name, agent_type
   </sql>
   
   <select id="selectById">
       SELECT <include refid="Base_Column_List"/>
       FROM lumina_agent
   </select>
   ```

4. **避免 SELECT *** - 明确指定字段
   ```xml
   <!-- ✅ 正确 -->
   SELECT agent_id, agent_name, agent_type FROM lumina_agent
   
   <!-- ❌ 错误 -->
   SELECT * FROM lumina_agent
   ```

#### 5.4.2 性能优化规范

1. **索引使用**
   - WHERE 条件字段必须建立索引
   - 联合查询使用联合索引
   - 避免在索引字段上使用函数

2. **分页查询**
   ```java
   // ✅ 使用 MyBatis-Plus 分页（推荐）
   Page<AgentDO> page = new Page<>(pageNum, pageSize);
   LambdaQueryWrapper<AgentDO> wrapper = new LambdaQueryWrapper<AgentDO>()
       .eq(AgentDO::getDeleted, 0)
       .orderByDesc(AgentDO::getCreateTime);
   Page<AgentDO> result = agentMapper.selectPage(page, wrapper);
   
   // ⚠️ 复杂查询才使用 XML + PageHelper
   // PageHelper.startPage(pageNum, pageSize);
   // List<AgentDO> list = agentMapper.selectComplexQuery(query);
   ```

3. **批量操作**
   ```xml
   <!-- 批量插入 -->
   <insert id="batchInsert">
       INSERT INTO lumina_agent (agent_name, agent_type, create_time)
       VALUES
       <foreach collection="list" item="item" separator=",">
           (#{item.agentName}, #{item.agentType}, #{item.createTime})
       </foreach>
   </insert>
   ```

4. **避免 N+1 查询**
   - 使用 JOIN 或子查询
   - 使用 MyBatis 的 `<collection>` 进行关联查询

#### 5.4.3 软删除规范（MyBatis-Plus 自动处理）

```xml
<!-- MyBatis-Plus 已自动处理逻辑删除，XML 中也需要手动添加 -->
<select id="selectComplexQuery">
    SELECT ... FROM lumina_agent
    WHERE agent_id = #{id} AND deleted = 0
</select>

<!-- 注意：MyBatis-Plus 的 BaseMapper 已自动处理逻辑删除 -->
<!-- 使用 MyBatis-Plus 时，deleteById 会自动转换为 UPDATE deleted = 1 -->
```

### 5.6 数据库设计规范

#### 5.6.1 表设计规范

```sql
CREATE TABLE lumina_agent (
    agent_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Agent ID',
    agent_name VARCHAR(100) NOT NULL COMMENT 'Agent名称',
    agent_type VARCHAR(50) NOT NULL COMMENT 'Agent类型',
    description VARCHAR(500) COMMENT '描述',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-启用, 0-禁用',
    tenant_id BIGINT COMMENT '租户ID',
    create_by VARCHAR(64) COMMENT '创建者',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) COMMENT '更新者',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志: 0-未删除, 1-已删除',
    remark VARCHAR(500) COMMENT '备注',
    INDEX idx_agent_name (agent_name),
    INDEX idx_agent_type (agent_type),
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent表';
```

#### 5.6.2 字段规范

1. **主键**: 统一使用 `{实体}_id BIGINT AUTO_INCREMENT`
2. **时间字段**: 使用 `DATETIME`，默认值 `CURRENT_TIMESTAMP`
3. **软删除**: 统一使用 `deleted TINYINT DEFAULT 0`
4. **状态字段**: 使用 `TINYINT`，明确注释含义
5. **字符串字段**: 根据实际需求设置长度，避免过长
6. **字符集**: 统一使用 `utf8mb4`

---

## 六、代码质量规范

### 6.1 注释规范

#### 6.1.1 类注释

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

#### 6.1.2 方法注释

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
public TaskResult executeTask(Long taskId, ExecuteTaskCommand command) {
    // ...
}
```

### 6.2 异常处理规范

#### 6.2.1 异常分类

```java
// 领域异常 (Domain Exception)
public class AgentNotFoundException extends DomainException {
    public AgentNotFoundException(Long agentId) {
        super("Agent不存在: " + agentId);
    }
}

// 业务异常 (Business Exception)
public class AgentExecutionException extends BusinessException {
    public AgentExecutionException(String message) {
        super(message);
    }
}

// 系统异常 (System Exception)
// 使用框架提供的异常类
```

#### 6.2.2 异常处理

```java
// Controller 层统一异常处理
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(DomainException.class)
    public R<Void> handleDomainException(DomainException e) {
        return R.fail(400, e.getMessage());
    }
    
    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusinessException(BusinessException e) {
        return R.fail(409, e.getMessage());
    }
}
```

### 6.3 日志规范

```java
@Slf4j
public class AgentService {
    
    public void createAgent(CreateAgentDTO dto) {
        log.info("创建Agent开始, agentName: {}", dto.getAgentName());
        
        try {
            // 业务逻辑
            log.info("创建Agent成功, agentId: {}", agentId);
        } catch (Exception e) {
            log.error("创建Agent失败, agentName: {}", dto.getAgentName(), e);
            throw e;
        }
    }
}
```

**日志级别**:
- **DEBUG**: 详细的调试信息
- **INFO**: 关键业务流程节点
- **WARN**: 警告信息，不影响业务
- **ERROR**: 错误信息，需要处理

---

## 七、领域模型实践规范

### 7.1 领域实体设计

#### 7.1.1 Entity (领域实体) - 简单场景

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

#### 7.1.2 Value Object (值对象) - 复杂场景（可选）

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

### 7.2 业务服务设计

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
    
    /**
     * 激活 Agent
     */
    public void activateAgent(Long agentId) {
        AgentDO agentDO = agentMapper.selectById(agentId);
        if (agentDO == null || agentDO.getDeleted() == 1) {
            throw new NotFoundException("Agent不存在");
        }
        
        Agent agent = convertToDomain(agentDO);
        
        // 调用领域方法
        agent.activate();
        
        // 持久化
        agentDO = convertToDO(agent);
        agentMapper.updateById(agentDO);
    }
    
    /**
     * 转换方法 - 简单场景可以直接转换，复杂场景使用 Converter
     */
    private AgentDO convertToDO(Agent agent) {
        if (agentConverter != null) {
            return agentConverter.toDO(agent);
        }
        // 简单场景：直接转换
        AgentDO agentDO = new AgentDO();
        agentDO.setAgentId(agent.getAgentId());
        agentDO.setAgentName(agent.getAgentName());
        agentDO.setAgentType(agent.getAgentType().getValue());
        agentDO.setStatus(agent.getStatus().getValue());
        return agentDO;
    }
    
    private Agent convertToDomain(AgentDO agentDO) {
        if (agentConverter != null) {
            return agentConverter.toDomain(agentDO);
        }
        // 简单场景：直接转换
        Agent agent = new Agent();
        agent.setAgentId(agentDO.getAgentId());
        agent.setAgentName(agentDO.getAgentName());
        agent.setAgentType(AgentType.valueOf(agentDO.getAgentType()));
        agent.setStatus(AgentStatus.valueOf(agentDO.getStatus()));
        return agent;
    }
    
    private AgentVO convertToVO(Agent agent) {
        if (agentConverter != null) {
            return agentConverter.toVO(agent);
        }
        // 简单场景：直接转换
        AgentVO vo = new AgentVO();
        vo.setAgentId(agent.getAgentId());
        vo.setAgentName(agent.getAgentName());
        vo.setAgentType(agent.getAgentType().getValue());
        vo.setStatus(agent.getStatus().getValue());
        return vo;
    }
}
```

### 7.3 转换器设计（可选）

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
    
    /**
     * Domain -> VO
     */
    public AgentVO toVO(Agent agent) {
        if (agent == null) {
            return null;
        }
        AgentVO vo = new AgentVO();
        vo.setAgentId(agent.getAgentId());
        vo.setAgentName(agent.getAgentName());
        vo.setAgentType(agent.getAgentType().getValue());
        vo.setStatus(agent.getStatus().getValue());
        return vo;
    }
}
```

### 7.4 DO 和 Domain 合并使用（可选）

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
    
    @TableField("agent_type")
    private String agentType;
    
    @TableField("status")
    private Integer status;
    
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

/**
 * Service 中直接使用，无需转换
 */
@Service
public class AgentService {
    
    @Autowired
    private AgentMapper agentMapper;  // AgentMapper extends BaseMapper<Agent>
    
    public Agent getAgentById(Long id) {
        return agentMapper.selectById(id);  // 直接返回，无需转换
    }
}
```

---

## 八、前端编码规范

### 8.1 文件组织规范

```
src/
├── api/              # API 接口定义
│   └── agent.ts
├── views/            # 页面组件
│   └── agent/
│       ├── index.vue
│       └── components/
├── components/       # 公共组件
├── stores/           # 状态管理 (Pinia)
├── types/            # TypeScript 类型定义
├── utils/            # 工具函数
└── router/           # 路由配置
```

### 8.2 API 调用规范

```typescript
// api/agent.ts
import request from '@/utils/request'

export interface CreateAgentDTO {
  agentName: string
  agentType: string
  description?: string
}

export interface AgentVO {
  agentId: number
  agentName: string
  agentType: string
  description?: string
  createTime: string
  updateTime: string
}

export interface QueryAgentDTO {
  agentName?: string
  agentType?: string
  pageNum?: number
  pageSize?: number
}

export interface PageResult<T> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
  pages: number
}

// API 方法
export function createAgent(data: CreateAgentDTO) {
  return request.post<AgentVO>('/api/v1/agents', data)
}

export function getAgent(id: number) {
  return request.get<AgentVO>(`/api/v1/agents/${id}`)
}

export function listAgents(params: QueryAgentDTO) {
  return request.get<PageResult<AgentVO>>('/api/v1/agents', { params })
}

export function updateAgent(id: number, data: CreateAgentDTO) {
  return request.put<AgentVO>(`/api/v1/agents/${id}`, data)
}

export function deleteAgent(id: number) {
  return request.delete(`/api/v1/agents/${id}`)
}
```

### 8.3 组件规范

```vue
<template>
  <div class="agent-list">
    <el-table :data="agentList" v-loading="loading">
      <el-table-column prop="agentName" label="Agent名称" />
      <el-table-column prop="agentType" label="类型" />
      <el-table-column prop="createTime" label="创建时间" />
      <el-table-column label="操作">
        <template #default="{ row }">
          <el-button @click="handleEdit(row)">编辑</el-button>
          <el-button @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { listAgents, deleteAgent, type AgentVO } from '@/api/agent'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const agentList = ref<AgentVO[]>([])

const loadData = async () => {
  loading.value = true
  try {
    const res = await listAgents({ pageNum: 1, pageSize: 10 })
    agentList.value = res.data.list
  } catch (error) {
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

const handleDelete = async (row: AgentVO) => {
  try {
    await ElMessageBox.confirm('确定要删除吗？', '提示')
    await deleteAgent(row.agentId)
    ElMessage.success('删除成功')
    loadData()
  } catch (error) {
    // 用户取消或删除失败
  }
}

onMounted(() => {
  loadData()
})
</script>
```

---

## 九、总结

### 9.1 核心原则回顾

1. **简化分层架构** - 清晰的层次划分，Service 层为核心，Domain 层包含业务逻辑
2. **统一命名规范** - 前后端统一的命名规则
3. **RESTful API** - 标准的 REST 接口设计
4. **SQL 规范** - 优先使用 MyBatis-Plus，复杂 SQL 才用 XML
5. **代码质量** - 注释、异常处理、日志规范
6. **实用主义** - 架构服务于业务，避免过度设计

### 9.2 开发流程

1. **需求分析** → 识别业务需求和领域模型
2. **领域设计** → 设计 Entity（包含业务方法）
3. **服务设计** → 设计 Service（业务逻辑处理）
4. **接口设计** → 设计 Controller、DTO
5. **持久化设计** → 设计 DO、Mapper
6. **前端开发** → 调用 API，实现页面

### 9.3 架构选择说明

**为什么选择简化分层架构而非完整 DDD？**

1. ✅ **降低学习成本** - 减少 DDD 概念，易于上手
2. ✅ **提高开发效率** - 减少转换类，快速开发
3. ✅ **保留核心思想** - Domain 实体包含业务逻辑，业务逻辑集中
4. ✅ **符合框架定位** - 让开发者聚焦业务，而非架构复杂性

**何时需要引入完整 DDD？**

- 业务变得非常复杂，需要严格的领域建模
- 团队有 DDD 经验，需要更严格的架构约束
- 需要处理复杂的聚合根和领域事件

**渐进式演进**：
- 初期：使用简化分层架构，快速开发
- 中期：根据业务复杂度，逐步引入 DDD 概念（Value Object、Repository 接口等）
- 后期：如果业务变得非常复杂，再考虑完整 DDD

---

**文档版本**: v1.0  
**创建时间**: 2025-01-XX  
**最后更新**: 2025-01-XX

