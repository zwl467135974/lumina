# B03 — AgentScope SDK 2.0 与 Lumina 封装层

> **前置要求**：已完成 [B02-Plan-Execute](B02-plan-execute-pattern.md)
> **预计阅读**：18 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

Lumina 的 Agent 不是从零写的——它底层依赖 **AgentScope Java SDK 2.0**。这节讲清 Lumina 和 AgentScope 的关系：AgentScope 是什么，2.0 有哪些核心能力，Lumina 在它上面封装了什么。

> **版本说明**：Lumina 3.7.0 起从 AgentScope 1.0.7 升级到 **2.0.0**，最大的变化是记忆管理方式（`.memory()` → `AgentStateStore`）和新增了 Tracer 推理链追踪能力。

---

## 三层架构

```
┌──────────────────────────────────────────┐
│  Lumina 业务层 (lumina-business-agent)     │  Agent 管理、会话、知识库挂载、成本
│  AgentController / AgentServiceImpl        │
├──────────────────────────────────────────┤
│  Lumina 引擎层 (lumina-agent-core)         │  配置加载、Prompt、记忆、流式封装、Trace
│  DefaultAgentExecutionEngine               │
├──────────────────────────────────────────┤
│  AgentScope Java SDK 2.0.0                 │  ReActAgent 核心循环、Model/Toolkit
│  io.agentscope.core.*                      │  AgentStateStore、Tracer SPI
├──────────────────────────────────────────┤
│  各厂商模型扩展 (agentscope-extensions)     │  DashScope/OpenAI/Anthropic/Gemini/Ollama
└──────────────────────────────────────────┘
```

### AgentScope 2.0 提供什么

| 组件 | 说明 |
|------|------|
| `ReActAgent` | ReAct 循环的完整实现（Reason→Act→Observe） |
| `Model` / `ChatModelBase` | LLM 调用抽象，各厂商扩展实现 |
| `Toolkit` | 工具管理（注册、调用、Schema 生成） |
| `Msg` / `MsgRole` | 消息封装（支持文本 + 多模态 ContentBlock） |
| `AgentStateStore` | **2.0 新增**——Agent 状态持久化（记忆跨实例共享） |
| `Tracer` | **2.0 新增**——推理链追踪 SPI（拦截 Agent/Model/Tool 调用） |
| `StreamOptions` | 流式输出控制（增量模式、推理/行动事件） |

### Lumina 封装了什么

- **配置管理**：AgentConfig（从 DB 加载，不是硬编码）
- **多模型适配**：ChatModelFactory（统一接口适配 5+ 厂商）
- **记忆管理**：MemoryManager（Redis 短期热存储）+ RedisAgentStateStore（**跨实例状态共享**）
- **推理链追踪**：LuminaTraceTracer（实现 AgentScope Tracer SPI，采集推理步骤）
- **安全防护**：Prompt 注入检测 + PII 脱敏
- **成本追踪**：Token 统计持久化
- **流式封装**：StreamChunk 标准化事件格式
- **容错机制**：Resilience4j 熔断器 + Provider Failover 链

---

## ReActAgent 的 Builder

AgentScope 2.0 的 Builder 与 1.0.7 有重要区别：

```java
// 文件：lumina-agent-core/.../engine/impl/DefaultAgentExecutionEngine.java:869-889
ReActAgent.Builder agentBuilder = ReActAgent.builder()
        .name(config.getAgentName())            // Agent 名称
        .sysPrompt(config.getPromptTemplate())   // System Prompt（注意是 sysPrompt 不是 prompt）
        .model(model)                            // LLM 大脑（ChatModelFactory 创建）
        .toolkit(toolkit);                       // 工具箱（EnhancedToolManager 构建）

// 2.0 新增：注入 AgentStateStore（替代 1.0.7 的 .memory()）
if (redisAgentStateStore != null) {
    agentBuilder.stateStore(redisAgentStateStore);       // 跨实例记忆共享
    agentBuilder.defaultSessionId(conversationId);       // 绑定会话
}

return agentBuilder.build();
```

### ⚠️ 1.0.7 → 2.0 的关键变化

| 1.0.7 写法 | 2.0 写法 | 原因 |
|-----------|---------|------|
| `.memory(new InMemoryMemory())` | `.stateStore(redisAgentStateStore)` | 记忆从 Agent 内部管理改为外部 Store，支持跨实例共享 |
| `.prompt(systemPrompt)` | `.sysPrompt(systemPrompt)` | 方法名变更 |
| `io.agentscope.core.model.DashScopeChatModel` | `io.agentscope.extensions.model.dashscope.DashScopeChatModel` | 模型实现移到 extensions 包 |

> **为什么去掉 `.memory()`？** 1.0.7 中每个 ReActAgent 实例自带一个 InMemoryMemory，多实例部署时记忆不共享。2.0 将记忆抽到 `AgentStateStore` 接口，可以存 Redis，任何实例都能读取——这就是 [E02 长期记忆](E02-long-term-memory.md) 和 [RedisAgentStateStore](E05-agent-state-store.md) 的基础。

---

## Msg 消息封装

AgentScope 用 `Msg` 对象表示消息，使用 Builder 模式：

```java
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;

// 文本消息
Msg userMsg = Msg.builder()
    .role(MsgRole.USER)
    .textContent("今天天气怎么样？")
    .build();

// 多模态消息（文本 + 图片）
Msg multimodalMsg = Msg.builder()
    .role(MsgRole.USER)
    .content(List.of(
        TextBlock.builder().text("这张图片是什么？").build(),
        ImageBlock.builder().source(Base64Source.builder()
            .mediaType("image/png")
            .data(base64Data)
            .build()).build()
    ))
    .build();
```

四种角色：`SYSTEM` / `USER` / `ASSISTANT` / `TOOL`（工具返回结果）。

---

## 事件流（Events）

AgentScope 执行时发出事件（Event），Lumina 的 `toStreamChunk` 方法将其转换为标准化的 StreamChunk：

```java
// 文件：lumina-agent-core/.../engine/impl/DefaultAgentExecutionEngine.java:531-545
private StreamChunk toStreamChunk(Event event) {
    String type = event.getType() != null ? event.getType().name() : "CHUNK";
    String content = event.getMessage() != null
            ? event.getMessage().getTextContent() : "";
    return new StreamChunk(type, content, event.isLast());
}
```

| AgentScope Event 类型 | 含义 | 前端用途 |
|----------------------|------|---------|
| `REASONING_CHUNK` | LLM 思考输出 | 实时显示推理过程 |
| `ACTING_CHUNK` | 工具调用进度 | 显示"正在搜索..." |
| `FINAL` / `AGENT_RESULT` | 最终回复 | 渲染 Markdown 回复 |
| `ERROR` | 执行出错 | 显示错误提示 |

---

## Tracer SPI：推理链追踪（2.0 新增）

AgentScope 2.0 提供了 `Tracer` 接口，可以全局拦截所有 Agent/Model/Tool 调用。Lumina 实现了自己的 `LuminaTraceTracer`：

```java
// 文件：lumina-agent-core/.../tracing/LuminaTraceTracer.java
public class LuminaTraceTracer implements Tracer {

    // 拦截 Agent 调用（创建 trace root）
    @Override
    public Mono<Msg> callAgent(AgentBase agent, List<Msg> inputs, Supplier<Mono<Msg>> next) { ... }

    // 拦截 LLM 调用（记录 Token 用量 + 耗时）
    @Override
    public Flux<ChatResponse> callModel(ChatModelBase model, ..., Supplier<Flux<ChatResponse>> next) { ... }

    // 拦截工具调用（记录输入/输出/耗时）
    @Override
    public Mono<ToolResultBlock> callTool(Toolkit toolkit, ToolCallParam param, ..., Supplier<Mono<ToolResultBlock>> next) { ... }
}
```

一行注册全局生效，**不需要改任何 Builder 代码**：

```java
// 文件：lumina-agent-core/.../tracing/TraceConfig.java
TracerRegistry.register(luminaTraceTracer);  // 自动 enableTracingHook()
```

> 推理链追踪的完整原理（Reactor Context 传播、ThreadLocal 兜底、前端可视化）详见 [K01 — 推理链可观测性](K01-trace-observability.md)。

---

## 小结

| 层 | 职责 | 2.0 关键变化 |
|----|------|-------------|
| AgentScope SDK | ReActAgent 循环、Model/Toolkit | `.memory()` → `.stateStore()`；新增 Tracer SPI |
| Lumina 引擎层 | 配置/记忆/安全/成本/Trace 封装 | RedisAgentStateStore + LuminaTraceTracer |
| Lumina 业务层 | Agent CRUD/会话/知识库 | 不变 |

### 自测题

1. AgentScope 2.0 为什么去掉 `.memory()` 改用 `.stateStore()`？（提示：多实例部署）
   <details><summary>答案</summary>1.0.7 的 .memory() 在每个 Agent 实例内部存记忆（InMemoryMemory），多实例部署时记忆不共享。2.0 改为外部 AgentStateStore，可以存 Redis，任何实例都能读取。</details>

2. `sysPrompt` 和 1.0.7 的 `prompt` 有什么区别？
   <details><summary>答案</summary>只是方法名变更，功能一样——都是设置 System Prompt。2.0 统一命名为 sysPrompt。</details>

3. Tracer SPI 的三个拦截方法分别拦截什么？
   <details><summary>答案</summary>callAgent 拦截 Agent 调用（创建 trace root）、callModel 拦截 LLM 调用（记录 Token 和耗时）、callTool 拦截工具调用（记录输入/输出）。</details>

4. Lumina 的 `toStreamChunk` 做了什么转换？
   <details><summary>答案</summary>把 AgentScope 的 Event 对象转换为 Lumina 标准的 StreamChunk（提取 type、content、isLast、tokenUsage）。</details>

> 🚀 [B04 — Agent 配置体系 →](B04-agent-config-system.md)

---

📝 **本篇撰写期间修正的代码**：无（本篇是对 2.0 升级后的文档同步更新）。
