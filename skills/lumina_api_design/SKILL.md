---
name: lumina_api_design
description: Use this skill when designing REST APIs, creating controllers, or defining DTOs. This skill enforces RESTful API design standards, request/response formats, error handling, and parameter validation.
---

# Lumina API 接口设计规范

## 功能概述

本技能包用于确保 Lumina 框架项目的 REST API 设计符合规范，包括 URL 设计、HTTP 方法使用、统一响应格式、参数校验等。

## RESTful API 设计规范

### URL 设计

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

### HTTP 方法使用

| 方法 | 用途 | 幂等性 |
|------|------|--------|
| **GET** | 查询资源 | ✅ 幂等 |
| **POST** | 创建资源或执行操作 | ❌ 非幂等 |
| **PUT** | 完整更新资源 | ✅ 幂等 |
| **PATCH** | 部分更新资源 | ⚠️ 建议幂等 |
| **DELETE** | 删除资源 | ✅ 幂等 |

### 统一响应格式

#### 成功响应

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": { ... },
  "timestamp": 1704067200000
}
```

#### 失败响应

```json
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
```

#### 分页响应

```json
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

### 状态码规范

| 状态码 | 含义 | 使用场景 |
|--------|------|---------|
| **200** | 成功 | 所有成功操作 |
| **400** | 参数错误 | 请求参数校验失败 |
| **401** | 未授权 | 未登录或 Token 过期 |
| **403** | 无权限 | 无操作权限 |
| **404** | 资源不存在 | 查询的资源不存在 |
| **409** | 资源冲突 | 资源已存在或状态冲突 |
| **500** | 服务器错误 | 系统内部错误 |

## DTO 设计规范

### Request DTO

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

### Response DTO / VO

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

## Controller 设计规范

```java
@RestController
@RequestMapping("/api/v1/agents")
public class AgentController {
    
    @Autowired
    private AgentService agentService;
    
    /**
     * 创建 Agent
     */
    @PostMapping
    public R<AgentVO> createAgent(@Valid @RequestBody CreateAgentDTO dto) {
        AgentVO vo = agentService.createAgent(dto);
        return R.success(vo);
    }
    
    /**
     * 查询 Agent 列表
     */
    @GetMapping
    public R<PageResult<AgentVO>> listAgents(@Valid QueryAgentDTO dto) {
        PageResult<AgentVO> result = agentService.pageAgents(dto);
        return R.success(result);
    }
    
    /**
     * 查询 Agent 详情
     */
    @GetMapping("/{id}")
    public R<AgentVO> getAgent(@PathVariable Long id) {
        AgentVO vo = agentService.getAgentById(id);
        return R.success(vo);
    }
    
    /**
     * 更新 Agent
     */
    @PutMapping("/{id}")
    public R<Void> updateAgent(@PathVariable Long id, 
                                @Valid @RequestBody UpdateAgentDTO dto) {
        agentService.updateAgent(id, dto);
        return R.success();
    }
    
    /**
     * 删除 Agent
     */
    @DeleteMapping("/{id}")
    public R<Void> deleteAgent(@PathVariable Long id) {
        agentService.deleteAgent(id);
        return R.success();
    }
}
```

## 参数校验规范

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

## 使用场景

- 设计新 API 时，确保 URL 和 HTTP 方法符合规范
- 创建 Controller 时，确保使用统一的响应格式
- 定义 DTO 时，确保参数校验完整
- 代码审查时，检查 API 设计是否符合规范

## 检查清单

- [ ] URL 是否符合 RESTful 规范
- [ ] HTTP 方法使用是否正确
- [ ] 响应格式是否统一
- [ ] 状态码使用是否正确
- [ ] DTO 是否包含参数校验注解
- [ ] Controller 是否使用 @Valid 进行参数校验
- [ ] 日期字段是否使用 @JsonFormat 格式化

## 可用资源

- `references/restful-guidelines.md`: RESTful API 设计指南
- `examples/controller-example.java`: Controller 示例
- `examples/dto-examples.java`: DTO 示例
- `examples/error-handling.java`: 错误处理示例

