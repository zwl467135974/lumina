# Lumina 框架模块设计

## 一、设计原则

1. **显式业务标识** - 不自动识别意图，由调用方明确指定业务类型
2. **Agent 引擎分离** - Agent 模块作为执行引擎，不包含业务逻辑
3. **模块化设计** - 基础模块必须，业务模块可选
4. **渐进式引入** - 传统业务为主，Agent 能力为辅

---

## 二、模块划分

### 2.1 核心模块（必须）

#### lumina-common
**职责**: 公共组件和工具类
- 统一响应格式 (`R<T>`)
- 异常处理体系
- 工具类（日期、字符串、集合等）
- 常量定义

#### lumina-agent-core
**职责**: Agent 执行引擎（不参杂业务逻辑）
- Agent 执行框架（基于 AgentScope）
- 工具管理（Tool 注册、加载、调用）
- 提示词管理（Prompt 加载、模板渲染）
- 记忆管理（Memory 管理）
- 配置管理（Agent 配置加载）

#### lumina-framework
**职责**: 框架基础设施
- 配置管理（Spring Boot 配置）
- 日志追踪（OpenTelemetry）
- 监控指标（Metrics）
- 多租户支持（可选）

### 2.2 业务模块（可选，按需）

#### lumina-business-{domain}
**职责**: 传统业务模块
- 标准分层架构（API、Service、Domain、Infrastructure）
- 业务逻辑实现
- 数据持久化

**示例**:
- `lumina-business-order` - 订单业务
- `lumina-business-payment` - 支付业务
- `lumina-business-user` - 用户业务

#### lumina-agent-{domain}
**职责**: Agent 业务模块（封装 Agent 引擎）
- 业务配置（提示词、工具）
- Agent 服务封装
- 业务接口（可选）

**示例**:
- `lumina-agent-customer` - 客服 Agent
- `lumina-agent-analysis` - 数据分析 Agent
- `lumina-agent-assistant` - 智能助手 Agent

---

## 三、模块依赖关系

```
lumina-common (基础)
    ↑
    ├── lumina-agent-core (依赖 common)
    ├── lumina-framework (依赖 common)
    └── lumina-business-xxx (依赖 common, framework)
        └── lumina-agent-xxx (依赖 common, agent-core, business-xxx)
```

**依赖规则**:
- 核心模块不依赖业务模块
- 业务模块可依赖核心模块
- Agent 业务模块可依赖传统业务模块

---

## 四、核心接口设计

### 4.1 Agent 执行引擎接口

```java
public interface AgentExecutionEngine {
    /**
     * 执行 Agent 任务
     * 
     * @param businessType 业务类型（必填，显式指定）
     * @param task 任务内容
     * @param config 业务配置（可选）
     * @return 执行结果
     */
    Mono<ExecuteResult> execute(
        String businessType, 
        String task, 
        AgentConfig config
    );
}
```

### 4.2 业务模块接口（示例）

```java
// 传统业务模块
@RestController
@RequestMapping("/api/v1/business/order")
public class OrderController {
    // 传统业务接口
}

// Agent 业务模块
@RestController
@RequestMapping("/api/v1/agent/customer")
public class CustomerAgentController {
    // Agent 业务接口（内部调用 AgentExecutionEngine）
}
```

---

## 五、模块结构示例

### 5.1 lumina-agent-core 结构

```
lumina-agent-core/
├── src/main/java/io/lumina/agent/
│   ├── core/
│   │   ├── AgentExecutionEngine.java      # 执行引擎
│   │   └── AgentBuilder.java               # Agent 构建器
│   ├── config/
│   │   ├── ConfigLoader.java               # 配置加载器
│   │   └── BusinessAgentConfig.java       # 业务配置
│   ├── prompt/
│   │   ├── PromptLoader.java               # 提示词加载器
│   │   └── PromptTemplate.java             # 提示词模板
│   ├── tool/
│   │   ├── ToolManager.java                # 工具管理器
│   │   └── ToolRegistry.java               # 工具注册表
│   └── memory/
│       └── MemoryManager.java               # 记忆管理器
└── src/main/resources/
    └── agent-config/                        # 默认配置目录
```

### 5.2 lumina-agent-customer 结构

```
lumina-agent-customer/
├── src/main/java/io/lumina/agent/customer/
│   ├── service/
│   │   └── CustomerAgentService.java       # Agent 服务封装
│   └── api/
│       └── CustomerAgentController.java     # 业务接口
└── src/main/resources/
    ├── prompt/
    │   └── customer-service.md              # 客服提示词
    └── tools/
        └── customer-tools.json              # 客服工具配置
```

---

## 六、使用方式

### 6.1 传统业务模块

```java
// 标准分层架构，按开发规范实现
@Service
public class OrderService {
    // 传统业务逻辑
}
```

### 6.2 Agent 业务模块

```java
@Service
public class CustomerAgentService {
    
    @Autowired
    private AgentExecutionEngine agentEngine;
    
    public Mono<CustomerServiceResult> handleInquiry(
            CustomerInquiryRequest request) {
        
        String task = buildTask(request);
        
        return agentEngine.execute(
                "customer_service",  // 显式指定业务类型
                task,
                null
        ).map(this::convertToResult);
    }
}
```

### 6.3 统一接口（可选）

```java
@PostMapping("/api/v1/agent/execute")
public R<ExecuteResult> execute(
        @RequestParam String businessType,  // 显式指定
        @RequestBody AgentExecuteRequest request) {
    // ...
}
```

---

## 七、配置管理

### 7.1 Agent 业务配置

**位置**: `lumina-agent-{domain}/src/main/resources/agent-config/`

**格式**: YAML 或 JSON

```yaml
# customer-service-config.yaml
agent:
  name: customer_service_agent
  model: qwen-plus
  prompt: classpath:prompt/customer-service.md
  tools:
    - query_order
    - update_order_status
    - send_notification
  memory:
    type: in_memory
    maxSize: 100
```

### 7.2 配置加载优先级

1. 业务模块配置（`lumina-agent-{domain}`）
2. 全局默认配置（`lumina-agent-core`）
3. 运行时配置（代码传入）

---

## 八、扩展点

### 8.1 自定义工具

```java
@Component
public class CustomTool {
    @Tool(description = "自定义工具")
    public String customMethod(@ToolParam(name = "param") String param) {
        // 工具实现
    }
}
```

### 8.2 自定义提示词模板

```markdown
# 业务提示词模板
你是一个专业的客服助手...

## 可用工具
- query_order: 查询订单
- update_order_status: 更新订单状态
```

---

## 九、详细设计文档

- [Agent 执行引擎详细设计](./Agent执行引擎设计.md)
- [业务模块开发指南](../guides/业务模块开发指南.md)
- [配置管理规范](../guides/配置管理规范.md)
- [工具开发指南](../guides/工具开发指南.md)

---

**文档版本**: v1.0  
**创建时间**: 2025-01-XX

