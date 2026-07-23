# F01 — 流式输出

> **前置要求**：已完成 [模块 E](README.md)
> **预计阅读**：15 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

AI 生成回答需要几秒到几十秒。流式输出让用户**边生成边看到**，而不是干等。

> 前端 SSE 的实现在[第一阶 16-Axios+SSE](../stage-1-foundation/16-axios-sse-basics.md)讲过了。这节讲后端。

---

## 后端怎么实现

```java
// 文件：AgentController.java:431
@PostMapping(value = "/{id}/execute/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<StreamChunk>> executeAgentStream(
        @PathVariable("id") Long id,
        @RequestParam String task,
        @RequestParam(required = false) String conversationId) {
    return agentService.executeAgentStream(id, task, conversationId)
            .map(chunk -> ServerSentEvent.<StreamChunk>builder()
                    .id(String.valueOf(System.nanoTime()))
                    .event(chunk.type())       // 事件类型（REASONING_CHUNK/FINAL/...）
                    .data(chunk)               // 数据（StreamChunk JSON）
                    .build());
}
```

**关键三点**：
1. `produces = TEXT_EVENT_STREAM_VALUE` —— 声明这是 SSE 流
2. 返回 `Flux<ServerSentEvent<StreamChunk>>` —— 响应式流
3. 每个 chunk 包成 SSE 事件

---

## StreamChunk 的结构

```java
// 文件：lumina-agent-core/.../model/StreamChunk.java
public record StreamChunk(
    String type,         // REASONING_CHUNK / ACTING_CHUNK / FINAL / RAG_SOURCES / ERROR
    String content,      // 文本内容
    boolean last,        // 是否最后一块
    ExecuteResult.TokenUsage tokenUsage   // Token 统计（仅 FINAL 块有）
) {}
```

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| SSE 后端 | `produces=TEXT_EVENT_STREAM_VALUE` + `Flux<ServerSentEvent>` |
| StreamChunk | type(片段类型) + content(内容) + last(是否最后) |
| 片段类型 | 推理/行动/最终/RAG 来源 |

> 🚀 [F02 — 多模态 →](F02-multimodal.md)

---

📝 **本篇撰写期间修正的代码**：无。
