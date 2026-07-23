# B01 — Agent 是什么：ReAct 循环

> **前置要求**：已完成 [模块 A 全部](README.md)
> **预计阅读**：25 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

上一篇（A05）你了解了 Chatbot 和 Agent 的区别。这节深入 Agent 的核心运行机制——**ReAct 循环**到底怎么转的，Lumina 代码怎么实现。

---

## ReAct：Reason + Act

### 类比：一个会思考的实习生

老板说"帮我查一下这个月的销售额，做个图表"。

普通实习生（Chatbot）："我不知道怎么查……"
会思考的实习生（Agent）：
1. **思考**：需要查销售额 → 我有"查数据库"的工具
2. **行动**：执行 SQL 查询
3. **观察**：拿到数据了
4. **思考**：需要做图表 → 我有"画图"的工具
5. **行动**：生成图表
6. **观察**：图表做好了
7. **回答**：给老板图表

**这就是 ReAct 循环**：Reason（思考）→ Act（行动）→ Observe（观察）→ 再 Reason，直到完成。

---

## ReAct 循环详解

```
Step 1: Reason（推理）
  "用户要查北京天气。我没有实时数据，需要用 webSearch 工具。"

Step 2: Act（行动）
  调用工具: webSearch("北京今天天气")

Step 3: Observe（观察）
  工具返回: "北京 28°C 晴"

Step 4: Reason（推理）
  "拿到天气数据了，可以回答用户了。"

Step 5: 最终回答
  "北京今天 28°C，晴天。"
```

每一轮 Reason+Act+Observe 叫一次"循环"。简单任务 1-2 轮，复杂任务可能 5-10 轮。

---

## 在 Lumina 里 ReAct 怎么实现

### 执行引擎接口

```java
// 文件：lumina-agent-core/.../engine/AgentExecutionEngine.java
public interface AgentExecutionEngine {

    // 同步执行（等结果）
    ExecuteResult executeSync(String businessType, String task, AgentConfig config, String conversationId);

    // 流式执行（SSE 打字机）
    Flux<StreamChunk> executeStream(String businessType, String task, AgentConfig config, String conversationId);

    // 多模态执行（文本+图片）
    ExecuteResult executeMultimodalSync(String businessType, String task, List<MultimodalContent> contents, ...);
}
```

### 默认实现：DefaultAgentExecutionEngine

```java
// 文件：lumina-agent-core/.../engine/impl/DefaultAgentExecutionEngine.java
// 核心：创建 ReActAgent 并执行
public ExecuteResult executeAgentWithAgentScope(String agentType, String task, AgentConfig config, ...) {
    if ("PlanAndExecute".equalsIgnoreCase(agentType)) {
        // Plan-Execute 模式（下篇讲）
        return executeWithPlanExecute(task, config);
    } else {
        // 默认 ReAct 模式
        ReActAgent agent = createReActAgent(config);   // ← 创建 ReAct Agent
        return executeWithReAct(agent, task);           // ← 执行
    }
}
```

### createReActAgent

```java
private ReActAgent createReActAgent(AgentConfig config) {
    return ReActAgent.builder()
        .name(config.getAgentName())
        .model(chatModel)              // LLM 大脑
        .toolkit(toolkit)              // 工具箱
        .memory(memory)                // 记忆
        .prompt(systemPrompt)          // System Prompt
        .streamOptions(streamOptions)  // 流式选项
        .build();
}
```

**一行 Builder 搞定**——AgentScope SDK 的 ReActAgent 帮你实现了循环逻辑，Lumina 只管配参数。

---

## ReAct 的 System Prompt

ReAct 的魔法在于 System Prompt——它教 AI"你有工具，要先思考再行动"：

```
# prompts/react.txt
You are a helpful AI assistant with access to various tools.

When given a task, think step by step:
1. Understand what the user is asking for
2. Consider which tools might be helpful
3. Use tools to gather information
4. Provide a clear and helpful answer

Think carefully before acting, and explain your reasoning.
```

这段 Prompt 让 LLM 知道：你有工具可用，遇到需要实时信息/计算的场景就调工具。

---

## 流式输出：看 ReAct 的思考过程

ReAct 执行时，每个环节都会"流"出来：

```
StreamChunk(type="REASONING_CHUNK", content="用户要查天气，我需要用搜索工具...")  ← 推理过程
StreamChunk(type="ACTING_CHUNK", content="调用 webSearch 工具: '北京天气'")      ← 行动过程
StreamChunk(type="ACTING_CHUNK", content="工具返回: 28°C 晴")                    ← 观察结果
StreamChunk(type="FINAL", content="北京今天 28°C，晴天。")                        ← 最终回答
```

前端按 type 分流渲染：
- `REASONING_CHUNK` → 灰色斜体的"思考过程"
- `ACTING_CHUNK` → 蓝色框的"工具调用"
- `FINAL` → 正文字号的"最终回答"

> 📖 流式渲染详见 [AI 模块 F01-流式输出](F01-streaming-sse.md)。

---

## ReAct 的优势与局限

### 优势
- ✅ 能用工具获取实时信息
- ✅ 能分步推理解决复杂问题
- ✅ 思考过程可见（可调试）
- ✅ 向后兼容（不用工具就是普通 Chatbot）

### 局限
- ❌ 每轮循环都调 LLM（费 Token）
- ❌ 复杂任务可能循环很多次（慢）
- ❌ 推理方向可能跑偏（需要好的 Prompt）

---

## 动手试试

1. **打开 `AgentExecutionEngine.java`**：看接口有哪几个方法
2. **打开 `prompts/react.txt`**：读一遍 ReAct 的 System Prompt
3. **在 Lumina 创建一个 ReAct Agent**：配时间工具，问它"现在几点了"——看它怎么调工具

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| ReAct | Reason→Act→Observe 循环，直到完成 |
| 每轮都调 LLM | 每次推理都消耗 Token |
| System Prompt | 教 AI"你有工具，先思考再行动" |
| 流式输出 | 推理/行动/最终 三种片段流式返回 |

> 🚀 [B02 — Plan-Execute 模式 →](B02-plan-execute-pattern.md)

---

## 自测题

1. **ReAct 一次循环包含哪三步？**
   <details><summary>答案</summary>Reason（推理下一步）→ Act（调工具）→ Observe（看结果）。循环直到能给出最终回答。</details>

2. **ReAct 每轮循环消耗 Token 吗？为什么？**
   <details><summary>答案</summary>消耗。每轮 Reason 都要调一次 LLM，LLM 按输入+输出 Token 计费。循环越多越贵。</details>

---

📝 **本篇撰写期间修正的代码**：无。
