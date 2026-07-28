# B02 — Plan-Execute 模式

> **前置要求**：已完成 [B01-Agent 是什么](B01-what-is-agent.md)
> **预计阅读**：20 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

ReAct 模式"走一步看一步"——每轮推理才决定下一步。但复杂任务需要**先规划再执行**——先拆解成步骤，再逐步做。

这节讲 Plan-Execute 模式，和 ReAct 的取舍。

---

## Plan-Execute vs ReAct

### 类比：装修房子

- **ReAct 式**：走到客厅"嗯该刷墙了"→ 刷墙 → 走到卧室"该铺地板了"→ 铺地板……走到哪算哪
- **Plan-Execute 式**：先写清单"1.客厅刷墙 2.卧室铺地板 3.厨房换橱柜"→ 按清单逐步执行

**Plan-Execute 适合**：步骤多、需要全局规划的任务
**ReAct 适合**：步骤少、即时性强的任务

---

## 三阶段架构

```java
// 文件：lumina-agent-core/.../engine/PlanExecuteAgent.java
// 类注释（26-34行）：
// 1. Plan 阶段：Planner（无工具，输出 JSON 子任务列表）
// 2. Execute 阶段：Executor（带工具，逐个执行子任务）
// 3. Summarize 阶段：汇总各步结果生成最终答复
```

```
用户任务: "帮我分析上周的销售数据，找出 TOP3 产品并生成报告"

Phase 1 - Plan（规划）:
  Planner（无工具）拆解:
  [
    {"step": "查询上周销售数据"},
    {"step": "按销售额排序找出 TOP3"},
    {"step": "生成分析报告"}
  ]

Phase 2 - Execute（执行）:
  Executor（带工具）逐个执行:
  Step 1: 调 SQL 工具查数据 → 拿到原始数据
  Step 2: 调计算工具排序 → 找出 TOP3
  Step 3: 调生成工具 → 生成报告

Phase 3 - Summarize（汇总）:
  Summarizer 合并三个步骤的结果 → 最终回答
```

---

## Planner 的 Prompt

```java
// 文件：PlanExecuteAgent.java:42-46
private static final String PLANNER_PROMPT = """
    You are a task planning assistant. Break down the user's request
    into 1-5 actionable sub-tasks.
    Output ONLY a JSON array: [{"step": "description"}, ...]
    No explanations, no markdown fences.
    """;
```

**关键**：Planner **没有工具**——它只负责"想"，不负责"做"。输出必须是 JSON 数组格式。

---

## 降级机制

```java
// Planner 可能规划失败（LLM 没按格式输出 JSON）
// Lumina 的处理：规划失败 → 降级为直接执行（相当于 ReAct）
if (parsePlanFailed) {
    log.warn("Planner 规划失败，降级为直接执行");
    return executeDirectly(task);    // 不规划，直接做
}
```

**优雅降级**——Plan-Execute 失败不会崩溃，退回 ReAct 行为。

---

## 怎么选 ReAct 还是 Plan-Execute

| 维度 | ReAct | Plan-Execute |
|------|-------|-------------|
| 适合任务 | 简单/即时 | 复杂/多步骤 |
| Token 消耗 | 少（少轮循环） | 多（Planner+Executor+Summarizer 三个 LLM 调用） |
| 全局规划 | ❌ 走一步看一步 | ✅ 先拆解 |
| 可靠性 | 高（成熟） | 中（规划可能失败） |
| Lumina 默认 | ✅ 是 | 否 |

**建议**：默认用 ReAct。只有任务明显需要"先拆解再执行"时才切 Plan-Execute。

---

## 在 Lumina 里怎么切换

```yaml
# 创建 Agent 时配 agentType
agentType: ReAct          # 默认，推理-行动循环
agentType: PlanAndExecute # Plan-Execute 模式
```

代码里的分发（概念示意，真实方法名为 `executeAgentWithAgentScope` 内部分支）：
```java
// DefaultAgentExecutionEngine.java
if ("PlanAndExecute".equalsIgnoreCase(agentType)) {
    return executePlanAndExecute(task, config);  // 走 Plan-Execute
} else {
    return executeReAct(task, config);           // 默认 ReAct
}
```

---

## 小结

| 模式 | 一句话记忆 | 适合 |
|------|-----------|------|
| ReAct | 走一步看一步 | 简单/即时任务 |
| Plan-Execute | 先规划清单再逐步执行 | 复杂/多步骤任务 |
| 三阶段 | Plan（规划）→ Execute（执行）→ Summarize（汇总） | |
| 降级 | 规划失败退回 ReAct | 容错 |

> 🚀 [B03 — AgentScope SDK →](B03-agentscope-sdk.md)

---

📝 **本篇撰写期间修正的代码**：无。
