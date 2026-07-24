# F01 — 流式输出：让用户边生成边看到

> **前置要求**：已完成 [模块 E 记忆对话](README.md)
> **预计阅读**：18 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

AI 生成回答需要几秒到几十秒。如果等全部生成完再返回，用户体验极差——像网页卡住了。**流式输出**（Streaming）让用户边生成边看到文字，像 ChatGPT 的打字机效果。

> 前端 SSE 的实现在[第一阶 16-Axios+SSE](../stage-1-foundation/16-axios-sse-basics.md)讲过了。这节聚焦后端：Reactor Flux + AgentScope 事件 + StreamChunk 标准化。

---

## 先建立直觉：水管 vs 水桶

**非流式** = **水桶模式**：等水龙头把桶接满，再一次性端给用户。用户干等 10 秒。

**流式** = **水管模式**：水龙头开着，水边流边送到用户嘴里。用户立刻看到第一个字，后续持续来。

技术上，水管模式用的是 **SSE（Server-Sent Events）**——HTTP 长连接，服务器持续推送数据块。

---

## 后端实现：三层架构

```
AgentScope ReActAgent          产出 Event 流（推理/行动/最终结果）
        │
        ▼
Lumina DefaultAgentExecutionEngine   Event → StreamChunk 转换 + 记忆保存 + 错误降级
        │
        ▼
AgentController                     StreamChunk → SSE 事件包装
        │
        ▼
前端 EventSource                     逐块渲染打字机效果
```

### 第一层：Controller SSE 端点

```java
// 文件：lumina-business-agent/.../api/controller/AgentController.java:431
@PostMapping(value = "/{id}/execute/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<StreamChunk>> executeAgentStream(
        @PathVariable("id") Long id,
        @RequestParam String task,
        @RequestParam(required = false) String conversationId) {
    return agentService.executeAgentStream(id, task, conversationId)
            .map(chunk -> ServerSentEvent.<StreamChunk>builder()
                    .id(String.valueOf(System.nanoTime()))
                    .event(chunk.type())    // REASONING_CHUNK / ACTING_CHUNK / FINAL ...
                    .data(chunk)            // StreamChunk JSON
                    .build());
}
```

**三个关键点**：
1. `produces = TEXT_EVENT_STREAM_VALUE` — 声明响应类型为 SSE
2. 返回 `Flux<ServerSentEvent<StreamChunk>>` — Reactor 响应式流
3. 每个 StreamChunk 包成独立的 SSE 事件（带 event type + data）

### 第二层：Event → StreamChunk 转换

AgentScope 内部产出的是 `Event` 对象，Lumina 统一转换为 `StreamChunk`：

```java
// 文件：lumina-agent-core/.../engine/impl/DefaultAgentExecutionEngine.java:531
private StreamChunk toStreamChunk(Event event) {
    String type = event.getType() != null ? event.getType().name() : "CHUNK";
    String content = event.getMessage() != null
            ? event.getMessage().getTextContent() : "";

    // FINAL 事件提取 Token 用量（供流式路径统计成本）
    ExecuteResult.TokenUsage tokenUsage = null;
    if (type.equals(StreamEventType.FINAL) || type.equals(StreamEventType.AGENT_RESULT)) {
        tokenUsage = extractTokenUsage(event.getMessage());
    }

    return new StreamChunk(type, content, event.isLast(), tokenUsage);
}
```

### 第三层：StreamChunk 数据结构

```java
// 文件：lumina-agent-core/.../model/StreamChunk.java
public record StreamChunk(
    String type,                            // 事件类型
    String content,                         // 文本内容
    boolean last,                           // 是否最后一块
    ExecuteResult.TokenUsage tokenUsage     // Token 统计（仅 FINAL 有）
) {}
```

---

## 事件类型全景

```java
// 文件：lumina-agent-core/.../model/StreamEventType.java
public final class StreamEventType {
    public static final String REASONING_CHUNK = "REASONING_CHUNK";  // 推理片段（思考过程）
    public static final String ACTING_CHUNK    = "ACTING_CHUNK";     // 行动片段（工具调用）
    public static final String FINAL           = "FINAL";            // 最终回复
    public static final String AGENT_RESULT    = "AGENT_RESULT";     // Agent 结果
    public static final String RAG_SOURCES     = "RAG_SOURCES";      // RAG 检索来源
    public static final String ERROR           = "ERROR";            // 错误
}
```

| 事件类型 | 前端用途 | 用户看到的 |
|---------|---------|-----------|
| `REASONING_CHUNK` | 实时显示推理过程 | "让我想想..." 的打字机效果 |
| `ACTING_CHUNK` | 显示工具调用进度 | "正在搜索..." 的进度提示 |
| `RAG_SOURCES` | 展示知识来源 | 引用文档列表 + 相似度分数 |
| `FINAL` | 渲染最终 Markdown 回复 | 完整答案 |
| `ERROR` | 显示错误提示 | "执行失败：..." |

---

## 流式构建器：buildAgentStreamFlux

引擎层负责把 AgentScope 的 Flux 管道组装起来：

```java
// 文件：DefaultAgentExecutionEngine.java buildAgentStreamFlux()
ReActAgent agent = createReActAgent(agentConfig);
StreamOptions options = StreamOptions.builder()
        .incremental(true)              // 增量模式（只发新生成的字）
        .includeReasoningChunk(true)    // 包含推理过程
        .includeActingChunk(true)       // 包含行动过程
        .build();

Flux<StreamChunk> agentFlux = agent.stream(contextMessages, options)
        .map(this::toStreamChunk)       // Event → StreamChunk
        .doOnNext(chunk -> {
            // 累积最终回复（用于记忆保存）
            if (chunk.type().equals(StreamEventType.FINAL)) {
                finalResponse.append(chunk.content());
            }
        })
        .doOnComplete(() -> {
            // 流结束后保存到记忆
            if (conversationId != null) {
                memoryManager.addMemory(conversationId, "user", task);
                memoryManager.addMemory(conversationId, "assistant", finalResponse.toString());
            }
        })
        .onErrorResume(e -> {
            // 出错不中断流，降级为错误事件
            return Flux.just(new StreamChunk(StreamEventType.ERROR,
                    e.getMessage(), true));
        });
```

**关键设计**：
- **增量模式**（incremental=true）：每个 chunk 只包含新增的文字，前端 append 即可
- **记忆保存**在 `doOnComplete` 中：流结束后才保存完整回复（不会保存半截）
- **错误降级**用 `onErrorResume`：流不会因为一个错误中断，而是发送 ERROR 事件让前端优雅处理

---

## RAG 来源推送

如果 Agent 配了知识库，流式输出会先推送检索到的文档来源：

```java
// 文件：DefaultAgentExecutionEngine.java buildRagSourcesFlux()
// 在 Agent 回答之前，先推送 RAG 检索结果
return Mono.fromCallable(() -> retrieve(task))
        .flatMapMany(docs -> {
            if (docs == null || docs.isEmpty()) return Mono.empty();

            // 推送 RAG_SOURCES 事件（前端展示引用来源）
            String json = objectMapper.writeValueAsString(docs);
            return Mono.just(new StreamChunk(StreamEventType.RAG_SOURCES, json, false));
        });
```

用户体验：先看到"引用了 3 篇文档"，然后看到 Agent 基于文档的回答。

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| SSE 后端 | `produces=TEXT_EVENT_STREAM_VALUE` + `Flux<ServerSentEvent>` |
| StreamChunk | type(事件类型) + content(内容) + last(是否最后) + tokenUsage |
| 增量模式 | incremental=true，每 chunk 只含新增文字 |
| 记忆保存 | doOnComplete 中执行，流结束后才保存完整回复 |
| 错误降级 | onErrorResume 不中断流，发送 ERROR 事件 |

### 自测题

1. 为什么记忆保存放在 `doOnComplete` 而不是 `doOnNext`？
   <details><summary>答案</summary>doOnNext 在每个 chunk 触发，此时回复还没完整；doOnComplete 在流结束后触发，能拿到完整的 finalResponse 才保存。</details>

2. `incremental=true` 和 `incremental=false` 有什么区别？前端处理方式有何不同？
   <details><summary>答案</summary>true=每 chunk 只含新增文字（前端 append），false=每 chunk 含完整内容（前端覆盖）。true 更省带宽。</details>

3. RAG_SOURCES 事件为什么在 FINAL 之前推送？
   <details><summary>答案</summary>先展示引用来源让用户知道答案依据，再看 Agent 回答，体验更好（类似论文先引文后结论）。</details>

4. `onErrorResume` 返回 `Flux.just(ERROR)` 有什么好处（对比直接抛异常）？
   <details><summary>答案</summary>不中断整个流，前端收到 ERROR 事件可以优雅显示错误提示；如果直接抛异常，已发送的 chunk 会丢失，前端可能卡在等待状态。</details>

> 🚀 [F02 — 多模态 →](F02-multimodal.md)

---

📝 **本篇撰写期间修正的代码**：无。
