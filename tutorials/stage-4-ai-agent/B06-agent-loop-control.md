# B06 — Agent 循环控制与安全阀

> **前置要求**：已完成 [B03 AgentScope SDK](B03-agentscope-sdk.md)
> **预计阅读**：15 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

ReAct Agent 的核心是 **Reason→Act→Observe 循环**——LLM 思考下一步做什么，执行工具，看结果，再思考。但如果没有限制，Agent 可能陷入死循环：反复调用同一个工具、在两个选项间来回纠结、一直说"让我再想想"。

**每多一轮循环就多烧一次 LLM Token**——10 轮无意义循环可能浪费 $0.5。在多租户平台上，这是真金白银。

---

## 先建立直觉：员工的工作时长限制

你给实习生一个任务："帮我查一下上周销售数据"。

- 正常情况：查数据（1 轮）→ 汇报结果 → 完成
- 异常情况：查数据 → 发现格式不对 → 再查 → 又不对 → 再查……无限循环

**maxIters 就是给实习生的"最多花 2 小时"的时间限制**——到了上限就得停，不管做没做完。

---

## AgentScope 2.0 的 maxIters

AgentScope 的 `ReActAgent.Builder` 提供了 `maxIters(int)` 方法：

```java
ReActAgent agent = ReActAgent.builder()
    .name("MyAgent")
    .model(model)
    .maxIters(10)           // 最多循环 10 次（默认值）
    .build();
```

**一次迭代 = 一次 LLM 推理 + 可能的一次工具调用**。超过 maxIters 后，Agent 强制停止并返回当前最佳结果。

---

## Lumina 的配置化实现

### 全局默认 vs 单 Agent 覆盖

Lumina 支持两层配置：

```yaml
# application.yml — 全局默认
lumina:
  agent:
    max-iterations: 10   # 所有 Agent 默认最多循环 10 次
```

```java
// AgentConfig — 单个 Agent 可覆盖
AgentConfig config = new AgentConfig();
config.setMaxIterations(3);  // 这个 Agent 只允许 3 轮（适合简单任务，省 Token）
```

### 引擎层的配置解析

```java
// 文件：lumina-agent-core/.../engine/impl/DefaultAgentExecutionEngine.java
// createReActAgent 方法中

// 循环迭代限制（防止死循环烧 Token）
int maxIters = config.getMaxIterations() != null
        ? config.getMaxIterations()              // Agent 级别配置优先
        : agentProperties.getMaxIterations();    // 否则用全局默认
agentBuilder.maxIters(maxIters);
```

---

## 怎么选择 maxIters 值

| Agent 类型 | 推荐 maxIters | 原因 |
|-----------|--------------|------|
| 简单问答 | 3-5 | 不需要工具调用，1 轮就该回答 |
| 工具调用型 | 10 | 查数据 + 分析 + 汇报 |
| Plan-Execute | 15-20 | 分解任务 + 逐步执行 + 汇总 |
| 复杂研究型 | 20-30 | 需要多轮搜索 + 交叉验证 |

> **成本视角**：maxIters=10 的 Agent，最坏情况消耗 10 次 LLM 调用的 Token。如果用 GPT-4（约 $0.03/1K Token），一次完整循环可能花 $0.5-1.0。设 maxIters=3 可以把成本控制在 $0.15 以内。

---

## 三层容错体系

maxIters 是 Lumina **第三层**成本/安全控制，和已有的两层配合：

```
┌──────────────────────────────────────────┐
│  第 1 层：单次调用容错（LlmResilienceWrapper）│  LLM 调用失败时重试 3 次 + 熔断
├──────────────────────────────────────────┤
│  第 2 层：Provider 容错（ProviderFailover）  │  主 LLM 挂了切到备用 LLM
├──────────────────────────────────────────┤
│  第 3 层：循环限制（maxIters）              │  Agent 推理循环不超过 N 次
└──────────────────────────────────────────┘
```

第 1 层管**单次调用**，第 2 层管**Provider 级别**，第 3 层管**推理循环级别**——三层叠加才能真正做到生产级的成本可控。

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| maxIters | Agent 循环的最大次数（每次 = 1 次推理 + 可能的工具调用） |
| 全局默认 | `lumina.agent.max-iterations=10` |
| Agent 覆盖 | `AgentConfig.maxIterations` 优先于全局 |
| 三层容错 | 调用重试 → Provider 切换 → 循环限制 |

### 自测题

1. maxIters=5 的 Agent 执行一个需要 3 轮推理的任务，会怎样？
   <details><summary>答案</summary>正常完成。3 轮 < 5 轮上限，Agent 会在第 3 轮给出最终回答后主动停止，不会跑到 5 轮。</details>

2. 为什么简单问答 Agent 应该设 maxIters=3 而不是 10？
   <details><summary>答案</summary>省钱。每次迭代都消耗 LLM Token，简单问答 1-3 轮就该回答，设 10 轮浪费潜在成本。</details>

3. maxIters 和 LlmResilienceWrapper 的重试有什么区别？
   <details><summary>答案</summary>maxIters 管的是 Agent 的推理循环（Reason→Act→Observe 重复几次）；LlmResilienceWrapper 管的是单次 LLM HTTP 调用失败后重试几次。两者层级不同。</details>

> 🚀 [B04 — Agent 配置体系 →](B04-agent-config-system.md)

---

📝 **本篇撰写期间修正的代码**：无（maxIters 能力为本次新增）。
