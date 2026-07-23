# 08 — 响应式编程（Reactor）

> **前置要求**：已完成 [07-线程池](07-thread-pool-async.md)
> **预计阅读**：25 分钟
> **难度**：⭐⭐⭐⭐⭐

---

## 面试题引入

> **"面试官：响应式编程的背压是什么？Lumina 的 SSE 流式输出为什么用 Flux？"**

---

## 表层回答（60 分）

Reactor 是响应式流规范的实现。Flux 代表 0-N 个元素的异步序列，Mono 代表 0-1 个。背压是消费者告诉生产者"我能处理多少"。

---

## 深层原理（90 分）

### Flux vs Mono

| 类型 | 含义 | 类比 |
|------|------|------|
| `Mono<T>` | 0 或 1 个元素 | 快递单件（一个包裹） |
| `Flux<T>` | 0 到 N 个元素 | 快递流水线（一连串包裹） |

### 响应式 vs 传统

```java
// 传统同步：等结果（阻塞线程）
String result = doSomething();    // 线程在这里等
System.out.println(result);

// 响应式：注册回调（不阻塞）
Mono<String> mono = doSomethingAsync();
mono.subscribe(result -> System.out.println(result));    // 结果来了再执行
// 线程不等，继续干别的
```

---

## 在 Lumina 里的应用

### SSE 流式输出

```java
// 文件：AgentController.java
@PostMapping(value = "/{id}/execute/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<StreamChunk>> executeAgentStream(...) {
    return agentService.executeAgentStream(id, task, conversationId)
            .map(chunk -> ServerSentEvent.<StreamChunk>builder()
                    .event(chunk.type())
                    .data(chunk)
                    .build());
}
```

**为什么用 Flux**：AI 流式输出是"连续多个片段"——不是一个完整结果。Flux 天然表示"多个异步元素"，完美匹配。

### 异步执行（Mono）

```java
// AgentExecutionEngine 接口
Mono<ExecuteResult> execute(String businessType, String task, AgentConfig config, String conversationId);
// Mono 因为只返回一个结果（ExecuteResult）
```

---

## 背压（Backpressure）

### 问题

LLM 生成速度快（每秒 100 Token），前端渲染慢（每秒 20 Token）。如果不控制，生成的片段堆积在内存→OOM。

### 解决：背压

消费者告诉生产者"我只能处理 20 个"：

```
生产者（LLM）───100个/秒───► 缓冲区 ───20个/秒───► 消费者（前端）
                                 ↑
                          背压："慢点，我只消费20个"
```

### Reactor 怎么实现

```java
Flux<StreamChunk> flux = agentService.executeAgentStream(...)
    .onBackpressureBuffer(100);      // 缓冲 100 个，超了丢弃/报错
```

| 策略 | 行为 |
|------|------|
| `onBackpressureBuffer(n)` | 缓冲 n 个，超了报错 |
| `onBackpressureDrop()` | 消费不过来就丢弃 |
| `onBackpressureLatest()` | 只保留最新的 |

---

## 操作符（Operators）

```java
Flux<StreamChunk> result = agentService.executeAgentStream(...)
    .filter(chunk -> chunk.type() != null)        // 过滤
    .map(chunk -> toSSE(chunk))                   // 转换
    .onBackpressureBuffer(100)                    // 背压
    .doOnNext(chunk -> log.debug("发出: {}", chunk))  // 副作用
    .doOnError(e -> log.error("流出错", e));       // 错误处理
```

像 Stream API——链式操作，声明式。

---

## Context Propagation

> 📖 详见 [06-ThreadLocal](06-threadlocal-context.md)。

Reactor 异步线程切换时 ThreadLocal 丢失。Lumina 用 `Hooks.enableAutomaticContextPropagation()` 自动传播。

---

## 常见追问

### Q：Reactor 比 Traditional 好在哪？

**A**：用少量线程处理大量并发。传统模式一个请求占一个线程（阻塞等 IO），Reactor 一个线程能处理多个请求（事件驱动）。但调试难、学习曲线陡。

### Q：Spring WebFlux vs WebMVC？

**A**：WebFlux 全响应式（Netty + Reactor），WebMVC 传统同步（Tomcat + Servlet）。Lumina 是 **WebMVC + Reactor 混合**——Controller 返回 Flux（流式），但框架是 Servlet。

---

## 小结

| 知识点 | 一句话 |
|--------|--------|
| Flux | 0-N 个异步元素（流式输出） |
| Mono | 0-1 个异步元素（单次执行） |
| 背压 | 消费者控制生产速度 |
| SSE | Flux + ServerSentEvent |
| Context Propagation | ThreadLocal 跨异步线程传播 |

---

📝 **本篇撰写期间修正的代码**：无。
