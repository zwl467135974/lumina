# C06 — 工具调用错误恢复

> **前置要求**：已完成 [C01 工具调用原理](C01-tool-calling-principle.md)、[B06 Agent 循环控制](B06-agent-loop-control.md)
> **预计阅读**：15 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

Agent 调用工具时会失败——参数类型不对、工具抛异常、超时、熔断器跳闸。失败之后呢？

很多人第一反应是"加 try/catch 重试"或者"换备用工具"。但 ReAct Agent 的特殊之处在于：**LLM 本身就是修复者**。它看到错误后，能在下一轮推理中自我修正参数重试。

这一节讲清两件事：
1. AgentScope 2.0 的 ReAct 循环**本来就**会把工具错误回喂给 LLM——这不是 Lumina 的代码
2. Lumina 在此基础上做的是什么——把"错误"变成**可操作的错误**（让 LLM 知道错在哪、怎么改）

---

## 先建立直觉：被纠正的工人

想象一个工人在装配零件：

- **糟糕的纠正**：工人装错零件，你只说"错了"。工人一脸懵，可能再装错一次，反复循环。
- **好的纠正**：你指着零件说"错了，因为这是 8mm 螺丝，你拿的是 10mm，正确规格在这个盒子里"。工人立刻换 8mm 重装。

ReAct Agent 中的 LLM 就是这个工人。工具返回的错误信息就是"纠正"。**纠正越具体，LLM 重试成功的概率越高。** 这就是 Lumina 在错误恢复上做的事——不是发明新机制，而是把纠正信息写得更"可操作"。

---

## AgentScope 2.0 的内置机制

工具调用失败后的回喂链路**完全是 AgentScope ReAct 循环自带的**，不需要 Lumina 任何额外代码：

```
Round N:
  LLM 输出 ToolCall: webSearch({"query": 123})     ← 参数类型错（要 string 给了 int）
  ReAct 循环执行工具 → 抛异常
  buildErrorResult → ToolResultBlock(state=ERROR, output="...")
  包装为 TOOL 角色消息塞回 messages

Round N+1:
  LLM 看到 messages 里：
    - 我的 ToolCall（query=123）
    - TOOL 消息："Error: query must be string, your input was ..."
  LLM 思考：哦，参数类型错了
  LLM 输出新 ToolCall: webSearch({"query": "123"})  ← 自动修正重试
```

**关键**：错误回喂是 ReAct 循环的"本能"。如果没有这一步，Agent 就会变成"调一次失败就死"的脆皮系统。

### 三种触发路径

看 `ToolDefinitionToAgentToolAdapter.callAsync()` 的实现，所有错误都汇聚到同一个 `buildErrorResult()`：

```java
// 文件：lumina-agent-core/.../tool/ToolDefinitionToAgentToolAdapter.java

@Override
public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
    return Mono.fromCallable(() -> {
        try {
            // 路径 1：熔断器开启 → 直接返回错误
            if (circuitBreaker != null && !circuitBreaker.allowExecution(toolName)) {
                String msg = "工具熔断中，暂不可用: " + toolName;
                return buildErrorResult(msg, paramsJson);
            }

            // 正常执行
            Object result = toolDefinition.execute(paramsJson);
            return ToolResultBlock.text(resultString);

        } catch (Exception e) {
            // 路径 2：执行抛异常 → 返回错误
            return buildErrorResult(e.getMessage(), paramsJson);
        }
    })
    .timeout(Duration.ofMillis(executionTimeoutMs))
    .onErrorResume(TimeoutException.class, ex -> {
        // 路径 3：执行超时 → 返回错误
        String msg = "工具执行超时（" + (executionTimeoutMs / 1000) + "s）: " + toolName;
        return Mono.just(buildErrorResult(msg, "{}"));
    });
}
```

| 错误路径 | 触发条件 | 谁负责重试 |
|---------|---------|-----------|
| 熔断器开启 | 同一工具短时间连续失败触发熔断 | LLM 看到后通常会换工具或放弃 |
| 执行抛异常 | 参数校验失败 / 工具内部 bug / 下游 500 | LLM 修正参数后重试 |
| 执行超时 | 工具执行超过 `executionTimeoutMs`（默认 60s） | LLM 可能换简化参数或放弃 |

三条路径殊途同归：都构造一个 `ToolResultBlock(state=ERROR)` 喂回 LLM。

---

## Lumina 的增强：可操作的错误消息

这是 Lumina 在 AgentScope 机制之上的**唯一**增强点。

### 默认错误 vs Lumina 错误

```
默认错误（如果只写 ToolResultBlock.text(e.getMessage())）：
  Error: query must be string

LLM 看到后：知道错了，但不知道正确的该是什么。可能瞎猜，多试几次。

Lumina 的错误（buildErrorResult）：
  Error: query must be string

  Expected parameters schema: {"type":"object","properties":{"query":{"type":"string"}},"required":["query"]}

  Your input was: {"query": 123}

  Please check the parameter types and format, then retry.

LLM 看到后：明确知道 query 应该是 string，且看到自己传的 123，立即修正。
```

### buildErrorResult 的三件套

```java
// 文件：lumina-agent-core/.../tool/ToolDefinitionToAgentToolAdapter.java（约 161 行）

private ToolResultBlock buildErrorResult(String errorMsg, String paramsJson) {
    StringBuilder hint = new StringBuilder();
    hint.append("Error: ").append(errorMsg);                       // ① 原始错误

    // ② 期望的参数 schema（让 LLM 知道正确格式）
    if (parametersSchema != null && !parametersSchema.isEmpty()) {
        String schemaJson = objectMapper.writeValueAsString(parametersSchema);
        hint.append("\n\nExpected parameters schema: ").append(schemaJson);
    }

    // ③ 实际传入的参数（让 LLM 对比差异）
    if (paramsJson != null && !paramsJson.equals("{}")) {
        hint.append("\n\nYour input was: ").append(paramsJson);
    }

    hint.append("\n\nPlease check the parameter types and format, then retry.");

    return ToolResultBlock.builder()
            .output(TextBlock.builder().text(hint.toString()).build())
            .state(ToolResultState.ERROR)
            .build();
}
```

**三个要素的分工**：
- **原始错误**：告诉 LLM 出了什么问题
- **Expected schema**：告诉 LLM 正确的参数结构长什么样
- **Your input**：让 LLM 直观对比"我传的"和"应该传的"差异

这三者合起来，相当于把"纠正工人"做到了极致——不只是说"错了"，而是说"错了，正确的是 X，你做的是 Y，请按 X 改"。

### 为什么用 builder 不用 ToolResultBlock.error()

这是一个**容易踩坑**的细节：

```java
// ❌ AgentScope 提供的 ToolResultBlock.error() 静态方法不设置 state
//    只设置 output 文本，state 保持默认值
ToolResultBlock.error("Error: ...")

// ✅ Lumina 用 builder 显式设置 state=ERROR
ToolResultBlock.builder()
        .output(TextBlock.builder().text(hint.toString()).build())
        .state(ToolResultState.ERROR)            // ← 关键
        .build();
```

如果不设 `state=ERROR`，下游的 ReAct 循环逻辑可能把这条结果误判为"成功"——错误回喂机制就失效了。这是 Lumina 写这块代码时踩过的坑。

---

## maxIters：防止死循环的安全阀

错误回喂是好事，但有个副作用——**LLM 可能反复重试失败的工具**，每次都"差一点修不对"，烧掉大量 Token。

这就是 [B06](B06-agent-loop-control.md) 讲的 `maxIters` 派上用场的地方：

```
maxIters=10 的 Agent：
  Round 1: 调 webSearch（错）→ 错误回喂
  Round 2: 修参数重试（还错）→ 错误回喂
  Round 3: 再修（还错）→ 错误回喂
  ...
  Round 10: 到达上限 → 强制停止，返回当前最佳结果
```

**错误回喂和 maxIters 是配合关系**：
- 错误回喂给 LLM 修正的机会（让简单错误能在 1-2 轮内自愈）
- maxIters 兜底防止"修不好还死磕"（超过 N 轮强制终止，止损 Token）

如果没有 maxIters，一个坏工具可能让 Agent 烧掉几十轮 Token 才"放弃"。

---

## 一个完整例子

假设有个工具要求 `query` 必须是非空字符串，LLM 第一轮传了空字符串：

```
=== Round 1 ===
LLM 思考: 用户问"今天天气"，我调 webSearch 查一下。
LLM ToolCall: webSearch({"query": ""})

执行 webSearch → 抛 IllegalArgumentException("query must be non-empty")
buildErrorResult 构造:
  Error: query must be non-empty
  Expected parameters schema: {"properties":{"query":{"type":"string","minLength":1}}}
  Your input was: {"query": ""}
  Please check the parameter types and format, then retry.

=== Round 2 ===
LLM 看到 Round 1 的错误，思考: query 不能为空，我得填具体内容。
LLM ToolCall: webSearch({"query": "今天天气"})

执行成功 → 返回天气信息 → Round 3 LLM 综合回答用户
```

整个修复过程**没有任何人工干预**，全靠"可操作的错误消息" + ReAct 循环 + maxIters 兜底。

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| 错误回喂机制 | AgentScope ReAct 循环自带，ToolResultBlock(ERROR) → TOOL 消息 → LLM 看见重试 |
| Lumina 的增强 | buildErrorResult 把错误写成"原始错误 + 期望 schema + 实际输入 + 重试指引" |
| 三条错误路径 | 熔断器开启 / 执行抛异常 / 执行超时，都汇聚到 buildErrorResult |
| state=ERROR | 必须用 builder 显式设置，ToolResultBlock.error() 不设 state 会失效 |
| maxIters 兜底 | 防止 LLM 反复重试坏工具烧 Token |

### 自测题

1. AgentScope ReAct 循环在工具调用失败时，默认行为是什么？Lumina 需要写额外代码触发它吗？
   <details><summary>答案</summary>ReAct 循环<b>默认就会</b>把工具的 ToolResultBlock 包装为 TOOL 角色消息塞回对话历史，LLM 在下一轮推理时能看到错误并自我修正重试。这是 AgentScope 2.0 的内置机制，Lumina 不需要写额外代码来"触发"回喂——Lumina 做的只是让回喂的内容更"可操作"。</details>

2. buildErrorResult 返回的错误消息包含哪三块信息？为什么这三块缺一不可？
   <details><summary>答案</summary>三块：<b>原始错误</b>（说出问题）+ <b>Expected parameters schema</b>（正确格式）+ <b>Your input was</b>（实际传入）。缺原始错误 LLM 不知道出了什么事；缺 schema LLM 不知道正确格式；缺实际输入 LLM 无法直观对比差异。三者合一才能让 LLM 精确定位并修正。</details>

3. 为什么 Lumina 用 `ToolResultBlock.builder().state(ERROR).build()` 而不是直接调 `ToolResultBlock.error()`？
   <details><summary>答案</summary>AgentScope 的 <code>ToolResultBlock.error()</code> 静态方法只设置 output 文本，<b>不会设置 state=ERROR</b>。如果不显式设置 state，下游 ReAct 循环逻辑可能把这条消息误判为"成功结果"，导致错误回喂机制失效。用 builder 显式设置 state 是踩过坑之后的修复。</details>

4. 如果没有 maxIters，工具错误回喂机制会带来什么风险？
   <details><summary>答案</summary>LLM 可能反复重试同一个修不对的工具——每轮都"差一点修不对"，错误回喂让 LLM 觉得"再试一次就能成功"，从而陷入死循环烧掉大量 Token。maxIters 强制在 N 轮后停止，是错误回喂机制的<b>必要安全阀</b>。两者是配合关系：回喂给修复机会，maxIters 防止损。</details>

---

> 🚀 [D01 — RAG 从零理解 →](D01-rag-from-scratch.md)

---

📝 **本篇撰写期间修正的代码**：无（错误恢复机制为既有实现，本节仅做解读）。
