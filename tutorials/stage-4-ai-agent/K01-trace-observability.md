# K01 — 推理链可观测性：让 Agent 的思考过程可见

> **前置要求**：已完成 [B03 AgentScope SDK](B03-agentscope-sdk.md) + 了解 Reactor 基础
> **预计阅读**：30 分钟
> **难度**：⭐⭐⭐⭐⭐

---

## 这节解决什么问题

Agent 执行时是个黑盒——用户发了消息，等了几秒，收到回复。但中间发生了什么？调了几次 LLM？用了什么工具？每步花了多少时间？消耗了多少 Token？

**推理链追踪（Trace）** 就是解决这个"黑盒问题"的。这节讲 Lumina 如何利用 AgentScope 2.0 的 Tracer SPI，实现全链路的推理步骤采集、持久化和可视化。

> **为什么难度 5 星？** 因为 Trace 系统的核心挑战不是"采集什么"，而是"如何跨线程传递上下文"——这涉及 Reactor 响应式编程中 Context 的传播机制，是 Java 高级面试的高频考点。

---

## 先建立直觉：医院病历

把 Agent 执行想象成一次**就医流程**：

```
你挂号（startTrace）
  → 分诊台问诊（callAgent）
    → 医生开检查单（callTool: 血常规）
      → 检验科出报告（callModel: 化验分析）
    → 医生看报告，再开检查（callTool: CT）
      → 影像科出报告（callModel: 影像分析）
    → 医生总结诊断（SUMMARIZE）
  → 拿到最终诊断书（finishTrace: markSuccess）
```

每一步都有**时间、耗时、输入、输出**。Trace 系统就是自动给整个流程生成一份**完整病历**，存在数据库里，随时可以回溯。

---

## 核心架构

```
用户请求
    │
    ▼
┌──────────────────────────────────┐
│  DefaultAgentExecutionEngine      │
│  ┌─────────────────────────────┐  │
│  │ 1. startTrace()             │  │  ← 创建 TraceContext，设入 ThreadLocal
│  │ 2. agent.call(messages)     │  │
│  │    │                        │  │
│  │    ▼                        │  │
│  │  ┌──────────────────────┐   │  │
│  │  │ AgentScope ReActAgent │   │  │
│  │  │  Tracer 拦截层        │   │  │  ← LuminaTraceTracer
│  │  │   callAgent()         │   │  │     从 Reactor Context 取 TraceContext
│  │  │   callModel()         │   │  │     记录 REASONING 步骤
│  │  │   callTool()          │   │  │     记录 TOOL_CALL 步骤
│  │  └──────────────────────┘   │  │
│  │ 3. markSuccess()           │  │
│  │ 4. finishTrace() ──异步──→ │  │  ← CompletableFuture 落库
│  └─────────────────────────────┘  │
└──────────────────────────────────┘
                 │
                 ▼
         lumina_agent_trace 表
         （steps JSON 列存完整步骤）
                 │
                 ▼
          前端 /agent/trace
         （el-timeline 时间线渲染）
```

---

## AgentScope 2.0 Tracer SPI

### 全局注册，一行生效

```java
// 文件：lumina-agent-core/.../tracing/TraceConfig.java
@PostConstruct
public void init() {
    if (traceEnabled) {
        TracerRegistry.register(luminaTraceTracer);
        // register() 内部自动调用 enableTracingHook()
        log.info("推理链 Tracer 已注册 + Hook 已启用");
    }
}
```

注册后，AgentScope 内部**所有** `ReActAgent.call()`、`model.stream()`、`tool.invoke()` 都会自动经过你的 Tracer——**不需要改任何业务代码**。

### 三个拦截方法

```java
// 文件：lumina-agent-core/.../tracing/LuminaTraceTracer.java
public class LuminaTraceTracer implements Tracer {

    private final TraceCollector collector;

    // ① 拦截 Agent 调用——trace 的根节点
    @Override
    public Mono<Msg> callAgent(AgentBase agent, List<Msg> inputs, Supplier<Mono<Msg>> next) {
        return Mono.deferContextual(ctxView -> {
            // 从 Reactor Context 取已有 TraceContext
            TraceContext ctx = ctxView.getOrDefault(TraceContext.KEY, null);
            // 没有就查 ThreadLocal（同步路径兜底）
            if (ctx == null) ctx = collector.getCurrentContext();
            // 都没有就新建（独立 SDK 调用场景）
            if (ctx == null) { /* 自行创建 */ }

            return next.get()
                .contextWrite(c -> c.put(TraceContext.KEY, ctx));  // 向下游传播
        });
    }

    // ② 拦截 LLM 调用——每轮 Reason
    @Override
    public Flux<ChatResponse> callModel(ChatModelBase model, ..., Supplier<Flux<ChatResponse>> next) {
        return Flux.deferContextual(ctxView -> {
            TraceContext ctx = ctxView.getOrDefault(TraceContext.KEY, null);
            TraceStep step = collector.startReasoningStep(ctx);  // 记录开始时间

            return next.get().doOnNext(response -> {
                ChatUsage usage = response.getUsage();
                if (usage != null && usage.getTotalTokens() > 0) {
                    // finish() 计算 now - startTimestamp = LLM 实际耗时
                    collector.finishReasoningStep(ctx, step,
                        usage.getInputTokens(), usage.getOutputTokens());
                }
            });
        });
    }

    // ③ 拦截工具调用——每轮 Act
    @Override
    public Mono<ToolResultBlock> callTool(Toolkit toolkit, ToolCallParam param,
                                          Supplier<Mono<ToolResultBlock>> next) {
        // 类似 callModel，记录工具名/输入/输出/耗时
    }
}
```

---

## ⚡ 核心难点：Reactor Context 跨线程传播

### 问题：ThreadLocal 在响应式编程中失效

传统同步代码中，我们用 ThreadLocal 传递上下文：

```java
// 同步代码——ThreadLocal 有效
ThreadLocal<TraceContext> CURRENT = new ThreadLocal<>();
CURRENT.set(ctx);                    // 线程 A 设置
agent.call(messages).block();        // 线程 A 执行 block，ThreadLocal 可见 ✅
CURRENT.get();                       // 线程 A 读取——拿到 ctx ✅
```

但 AgentScope 2.0 基于 **Reactor 响应式框架**，`callModel` 和 `callTool` 可能在**不同的调度器线程**上执行：

```java
// Reactor 异步代码——ThreadLocal 失效
ThreadLocal<TraceContext> CURRENT = new ThreadLocal<>();
CURRENT.set(ctx);                    // 线程 A 设置
agent.call(messages)
    .subscribeOn(Schedulers.boundedElastic())  // 切到线程 B
    .doOnNext(msg -> {
        CURRENT.get();               // 线程 B 读取——null ❌
    });
```

**这就是为什么一开始 Trace 的 steps 全是 NULL**——callModel 在另一个线程执行时 ThreadLocal 为空。

### 解决方案：Reactor Context

Reactor 提供了 **Context** 机制——它是**只读向上传播**的，沿着订阅链自动传递，不受线程切换影响。

```
订阅方向（subscribe 向上）          数据方向（onNext 向下）
     ↑                                  ↓
  Subscriber ←── Context ──← Publisher
     ↑                                  ↓
  doOnNext   ←── Context ──← map
     ↑                                  ↓
  source     ←── Context ──← contextWrite(写入点)
```

**关键规则**：`contextWrite` 写入的值，上游所有操作者都能通过 `deferContextual` 读到。

### Lumina 的实现

引擎层在创建 Mono 时注入 TraceContext：

```java
// 文件：lumina-agent-core/.../engine/impl/DefaultAgentExecutionEngine.java
private Mono<Msg> injectTraceContext(Mono<Msg> mono) {
    TraceContext ctx = traceCollector.getCurrentContext();  // 从 ThreadLocal 取
    if (ctx != null) {
        return mono.contextWrite(c -> c.put(TraceContext.KEY, ctx));
        //                  ↑ 写入 Reactor Context，上游可见
    }
    return mono;
}
```

Tracer 的 callModel/callTool 通过 `deferContextual` 读取：

```java
// callModel 读取
Flux.deferContextual(ctxView -> {
    TraceContext ctx = ctxView.getOrDefault(TraceContext.KEY, null);
    //                                        ↑ 从 Reactor Context 读取
});
```

### 三层兜底策略

但并不是所有路径都走了 `contextWrite`（比如 PlanExecuteAgent 内部的 `.call().block()`）。Lumina 设计了三层兜底：

```
callAgent 取 TraceContext 的优先级：

① Reactor Context（contextWrite 注入的）    ← 标准响应式路径
② ThreadLocal（getCurrentContext）          ← 同步 .block() 路径
③ 新建 TraceContext                          ← 独立 SDK 调用兜底
```

```java
// LuminaTraceTracer.callAgent 的核心逻辑
TraceContext fromReactor = ctxView.getOrDefault(TraceContext.KEY, null);
TraceContext ctx = fromReactor != null ? fromReactor : collector.getCurrentContext();
if (ctx == null) { /* 新建 */ }
```

> **面试加分点**：能说清"Reactor Context 的传播方向是上游可见"和"ThreadLocal 在响应式中的局限性"，这就是高级 Java 开发者对响应式编程的理解深度。

---

## Trace 数据模型

### TraceContext（执行级）

```java
// 文件：lumina-agent-core/.../tracing/TraceContext.java
public class TraceContext {
    private final String traceUuid;        // 唯一标识
    private String agentName;              // Agent 名称
    private Long agentId;                  // Agent ID（可按 Agent 过滤）
    private String inputText;              // 用户输入
    private String outputText;             // Agent 输出
    private String status;                 // RUNNING / SUCCESS / FAILED
    private int totalPromptTokens;         // 累计输入 Token
    private int totalCompletionTokens;     // 累计输出 Token
    private long durationMs;               // 总耗时
    private List<TraceStep> steps;         // 步骤列表（线程安全 synchronizedList）
}
```

### TraceStep（步骤级）

```java
// 文件：lumina-agent-core/.../tracing/TraceStep.java
public class TraceStep {
    private int seq;                // 序号（从 1 开始，自动递增）
    private String type;            // REASONING / TOOL_CALL / RETRIEVAL / MEMORY_INJECTION / SUMMARIZE
    private String name;            // "LLM 推理" / "工具: webSearch" / "RAG 检索"
    private String input;           // 输入内容（截断到 500 字符）
    private String output;          // 输出内容
    private Integer promptTokens;   // 输入 Token（仅 REASONING）
    private Integer completionTokens;// 输出 Token（仅 REASONING）
    private long durationMs;        // 耗时
}
```

5 种步骤类型覆盖了 Agent 执行的完整生命周期：

| 类型 | 何时记录 | 包含信息 |
|------|---------|---------|
| `REASONING` | 每次 LLM 调用 | Token 用量 + 耗时 |
| `TOOL_CALL` | 每次工具调用 | 工具名 + 输入/输出 + 耗时 |
| `RETRIEVAL` | RAG 知识检索 | 查询 + 召回数 + 最高分 |
| `MEMORY_INJECTION` | 上下文构建 | 长期/短期记忆条数 |
| `SUMMARIZE` | PlanAndExecute 汇总 | 输入拼接 + 输出 + 耗时 |

---

## 异步落库：不阻塞主流程

Trace 落库通过 `CompletableFuture.runAsync` 异步执行，不影响 Agent 执行性能：

```java
// 文件：lumina-agent-core/.../tracing/TraceCollector.java
public void finishTrace(TraceContext ctx) {
    CURRENT.remove();  // 清理 ThreadLocal

    CompletableFuture.runAsync(() -> {
        traceSink.save(ctx);   // 异步写入 lumina_agent_trace 表
    });
}
```

TraceSink 是一个解耦接口——引擎层不需要知道数据怎么存：

```java
// 文件：lumina-agent-core/.../tracing/TraceSink.java
public interface TraceSink {
    void save(TraceContext ctx);
}
```

业务层实现 AgentTraceSink，用 ObjectMapper 将 steps 序列化为 JSON 存入 MySQL。

---

## 前端可视化

### 列表页

```vue
<!-- 文件：lumina-frontend/src/views/agent/trace.vue -->
<el-table :data="tableData" border>
  <el-table-column prop="agentName" label="Agent" width="120" />
  <el-table-column prop="inputText" label="输入" min-width="200" show-overflow-tooltip />
  <el-table-column label="状态" width="90">
    <template #default="{ row }">
      <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
    </template>
  </el-table-column>
  <el-table-column label="Token" width="80">
    <template #default="{ row }">{{ row.totalTokens || 0 }}</template>
  </el-table-column>
  <el-table-column label="耗时" width="90">
    <template #default="{ row }">{{ row.durationMs ? row.durationMs + 'ms' : '-' }}</template>
  </el-table-column>
</el-table>
```

### 详情页：时间线

```vue
<el-timeline>
  <el-timeline-item v-for="step in selectedTrace.steps" :key="step.seq"
    :type="stepTagType(step.type)" :timestamp="step.durationMs + 'ms'">
    <el-card shadow="never">
      <el-tag :type="stepTagType(step.type)" size="small">{{ stepLabel(step.type) }}</el-tag>
      <span>{{ step.name }}</span>
      <span v-if="step.promptTokens">{{ step.promptTokens }} + {{ step.completionTokens }} Token</span>
    </el-card>
  </el-timeline-item>
</el-timeline>
```

> **踩坑记录**：API 返回的 `steps` 是 JSON 字符串而非数组，前端必须 `JSON.parse` 否则 `.length` 返回字符数，timeline 渲染出 N 个 `undefinedms`。

---

## 实际 Trace 数据示例

一次 PlanAndExecute 执行的 trace 时间线：

```
步骤 1:  REASONING   "LLM 推理"    4670ms   (Plan 阶段：分解任务)
步骤 2:  REASONING   "LLM 推理"    4856ms
步骤 3:  TOOL_CALL   "getCurrentUserContext"  129ms
步骤 4:  REASONING   "LLM 推理"    3520ms   (子任务 1 执行)
步骤 5:  TOOL_CALL   "getCurrentUserContext"  1ms
步骤 6:  REASONING   "LLM 推理"    1151ms   (子任务 2 执行)
...
步骤 14: SUMMARIZE   "结果汇总"    0ms      (汇总所有子任务结果)
```

从时间线可以一眼看出：哪步最慢、Token 花在哪里、工具调了几次。

---

## 数据清理

trace 表只增不删会膨胀，Lumina 通过定时任务自动清理：

```java
// 文件：lumina-modules/lumina-business-agent/.../schedule/AgentTraceCleanupJob.java
@Scheduled(cron = "${lumina.agent.trace.cleanup.cron:0 0 3 * * ?}")  // 每天凌晨 3 点
public void cleanup() {
    agentTraceService.cleanupExpired(retentionDays);  // 默认保留 30 天
}
```

清理采用分批 DELETE + LIMIT，避免大事务锁表。

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| Tracer SPI | AgentScope 2.0 全局拦截 Agent/Model/Tool 调用 |
| Reactor Context | 响应式编程中跨线程传递上下文的机制（替代 ThreadLocal） |
| TraceContext | 一次 Agent 执行的完整上下文（UUID + steps + tokens） |
| 三层兜底 | Reactor Context → ThreadLocal → 新建 |
| 异步落库 | CompletableFuture.runAsync，不阻塞主流程 |
| 5 种步骤类型 | REASONING / TOOL_CALL / RETRIEVAL / MEMORY_INJECTION / SUMMARIZE |

### 自测题

1. 为什么 ThreadLocal 在 Reactor 响应式编程中会失效？
   <details><summary>答案</summary>Reactor 在不同调度器线程上执行操作（如 boundedElastic），ThreadLocal 是线程绑定的，切换线程后值丢失。</details>

2. Reactor Context 的传播方向是什么？（提示：上游可见）
   <details><summary>答案</summary>上游可见。contextWrite 写入的值，沿着订阅链向上传播，上游的 deferContextual 能读到。</details>

3. `contextWrite` 和 `deferContextual` 分别在什么位置使用？
   <details><summary>答案</summary>contextWrite 在 Publisher 链的下游（靠近订阅者）写入；deferContextual 在上游（靠近数据源）读取。</details>

4. PlanAndExecute 路径为什么需要 ThreadLocal 兜底？
   <details><summary>答案</summary>PlanExecuteAgent 内部用 .block() 同步调用，在同一线程执行，ThreadLocal 可见。但没走 contextWrite 注入 Reactor Context，所以需要 ThreadLocal 兜底。</details>

5. REASONING 步骤的 durationMs 为什么不能在 doOnNext 中创建 step 后立即 finish()？
   <details><summary>答案</summary>startTimestamp 在 step 创建时记录，如果创建和 finish 都在 doOnNext 中，时间差几乎为 0。应该在 LLM 调用前创建 step（记录开始时刻），在返回后 finish（计算实际耗时）。</details>

> 🚀 返回 [AI 专项导读](README.md)

---

📝 **本篇撰写期间修正的代码**：无（代码已在 Trace 系统开发期间全部修复，本篇是教学同步）。
