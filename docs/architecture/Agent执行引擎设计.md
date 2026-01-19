# Agent 执行引擎详细设计

## 一、核心职责

Agent 执行引擎 (`AgentExecutionEngine`) 是 Lumina 框架的核心组件，负责：
- 执行 Agent 任务（不包含业务逻辑）
- 加载业务配置（提示词、工具）
- 构建和管理 Agent 实例
- 提供统一的执行接口

---

## 二、核心接口

### 2.1 AgentExecutionEngine

```java
public interface AgentExecutionEngine {
    
    /**
     * 执行 Agent 任务
     * 
     * @param businessType 业务类型（必填，显式指定）
     * @param task 任务内容
     * @param config 业务配置（可选，可覆盖默认配置）
     * @return 执行结果
     */
    Mono<ExecuteResult> execute(
        String businessType, 
        String task, 
        AgentConfig config
    );
    
    /**
     * 流式执行 Agent 任务
     */
    Flux<ExecuteChunk> stream(
        String businessType, 
        String task, 
        AgentConfig config
    );
}
```

### 2.2 执行结果

```java
@Data
public class ExecuteResult {
    private String result;           // 执行结果文本
    private Map<String, Object> metadata;  // 元数据（工具调用记录等）
    private Long duration;           // 执行耗时（毫秒）
    private List<ToolCall> toolCalls; // 工具调用记录
}
```

---

## 三、执行流程

```
1. 接收请求
   ↓
2. 加载业务配置
   ├─ 提示词配置
   ├─ 工具配置
   └─ Agent 配置
   ↓
3. 构建 Agent 实例
   ├─ 设置提示词
   ├─ 注册工具
   └─ 配置模型
   ↓
4. 执行任务
   ├─ 调用 AgentScope Agent
   └─ 处理结果
   ↓
5. 返回结果
```

---

## 四、配置加载

### 4.1 配置加载器

```java
public interface ConfigLoader {
    /**
     * 加载业务配置
     */
    BusinessAgentConfig load(String businessType);
    
    /**
     * 加载提示词
     */
    String loadPrompt(String promptPath);
    
    /**
     * 加载工具配置
     */
    List<ToolConfig> loadTools(List<String> toolNames);
}
```

### 4.2 配置优先级

1. **运行时配置**（代码传入的 `AgentConfig`）
2. **业务模块配置**（`lumina-agent-{domain}/resources/agent-config/`）
3. **全局默认配置**（`lumina-agent-core/resources/agent-config/default.yaml`）

---

## 五、Agent 构建

### 5.1 AgentBuilder

```java
public class AgentBuilder {
    
    public Agent build(BusinessAgentConfig config) {
        // 1. 加载提示词
        String prompt = promptLoader.load(config.getPromptPath());
        
        // 2. 加载工具
        Toolkit toolkit = toolManager.loadTools(config.getToolNames());
        
        // 3. 配置模型
        Model model = modelFactory.create(config.getModelConfig());
        
        // 4. 构建 Agent（使用 AgentScope）
        return ReActAgent.builder()
            .name(config.getAgentName())
            .sysPrompt(prompt)
            .toolkit(toolkit)
            .model(model)
            .memory(createMemory(config.getMemoryConfig()))
            .maxIters(config.getMaxIterations())
            .build();
    }
}
```

---

## 六、工具管理

### 6.1 ToolManager

```java
public interface ToolManager {
    /**
     * 加载工具
     */
    Toolkit loadTools(List<String> toolNames);
    
    /**
     * 注册工具
     */
    void registerTool(String name, AgentTool tool);
    
    /**
     * 获取工具
     */
    AgentTool getTool(String name);
}
```

### 6.2 工具来源

1. **框架工具**（`lumina-agent-core` 提供）
2. **业务工具**（`lumina-agent-{domain}` 提供）
3. **自定义工具**（运行时注册）

---

## 七、记忆管理

### 7.1 MemoryManager

```java
public interface MemoryManager {
    /**
     * 创建记忆实例
     */
    Memory createMemory(MemoryConfig config);
    
    /**
     * 获取记忆实例（按会话ID）
     */
    Memory getMemory(String sessionId);
}
```

### 7.2 记忆类型

- **InMemoryMemory**: 内存记忆（默认）
- **LongTermMemory**: 长期记忆（可选，需要 Mem0 等）

---

## 八、错误处理

### 8.1 异常类型

```java
// 业务类型不存在
public class BusinessTypeNotFoundException extends RuntimeException {}

// 配置加载失败
public class ConfigLoadException extends RuntimeException {}

// Agent 执行失败
public class AgentExecutionException extends RuntimeException {}
```

### 8.2 错误处理策略

- 配置错误：返回明确的错误信息
- 执行超时：设置超时时间，返回超时错误
- 工具调用失败：记录错误，继续执行或终止

---

## 九、性能优化

### 9.1 Agent 实例管理

- **单例模式**: 每个业务类型一个 Agent 实例（可配置）
- **池化模式**: Agent 实例池（高并发场景）
- **懒加载**: 首次使用时创建

### 9.2 缓存策略

- 配置缓存：业务配置缓存到内存
- 提示词缓存：提示词内容缓存
- 工具缓存：工具实例缓存

---

## 十、监控与追踪

### 10.1 监控指标

- 执行次数
- 执行耗时
- 工具调用次数
- 错误率

### 10.2 追踪

- 使用 OpenTelemetry 追踪
- 记录完整的执行链路
- 工具调用记录

---

**文档版本**: v1.0  
**创建时间**: 2025-01-XX

