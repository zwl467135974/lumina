# A05 — 从 Chatbot 到 Agent 的进化

> **前置要求**：已完成 [A04-Prompt 高级](A04-prompt-advanced.md)
> **预计阅读**：15 分钟
> **难度**：⭐⭐☆☆☆

---

## 这节解决什么问题

你用 ChatGPT 时，它只是"聊天"——你问它答。但 Lumina 的 Agent 能"做事"——查时间、发请求、调 API。**从"只会说"到"能做事"，这一步进化叫 Agent。**

这节是模块 A 的收官——串联前 4 篇，从"聊天机器人"到"AI Agent"。

---

## Chatbot vs Agent

### Chatbot（聊天机器人）

```
用户: "今天北京天气怎么样？"
AI: "我无法获取实时天气信息。我的训练数据截止到 2025 年..."
```

**特点**：只会"说话"，不能"做事"。不知道实时信息，不能调外部系统。

### Agent（智能体）

```
用户: "今天北京天气怎么样？"
Agent: [思考] 需要实时天气，我有 webSearch 工具
       [行动] 调用 webSearch("北京今天天气")
       [观察] 获取到天气数据: 28°C 晴
       [回答] "北京今天 28°C，晴天。"
```

**特点**：能"思考→行动→观察→再思考"，循环直到完成任务。

---

## Agent 的核心：ReAct 循环

```
用户给任务
  ↓
┌──→ Reason（推理）：分析当前情况，决定下一步做什么
│     ↓
│   Act（行动）：调用工具（搜索/计算/HTTP 请求...）
│     ↓
│   Observe（观察）：看工具返回的结果
│     ↓
└── 结果够了？──No──→ 回到 Reason
      │
      Yes
      ↓
    给出最终回答
```

这个循环叫 **ReAct**（Reason + Act）。详见 [AI 模块 B01-Agent 是什么](B01-what-is-agent.md)。

---

## 进化的三个台阶

| 阶段 | 能力 | 类比 |
|------|------|------|
| **Chatbot** | 只会聊天 | 只会口头指挥的实习生 |
| **Tool Agent** | 会用工具 | 有工具箱的实习生（能上网查、能算数） |
| **Autonomous Agent** | 自主规划+执行 | 能自己拆解任务、安排步骤的高级实习生 |

Lumina 的 ReAct 是第二阶（Tool Agent），Plan-Execute 是第三阶（Autonomous Agent）。

---

## Agent 为什么比 Chatbot 强

| 能力 | Chatbot | Agent |
|------|---------|-------|
| 回答问题 | ✅ | ✅ |
| 实时信息 | ❌ | ✅ 用搜索工具 |
| 数学计算 | ❌ 常算错 | ✅ 用计算工具 |
| 查数据库 | ❌ | ✅ 用 SQL 工具 |
| 执行代码 | ❌ | ✅ Code Interpreter |
| 多步推理 | ❌ | ✅ ReAct 循环 |

**一句话**：Chatbot 只有"嘴"，Agent 有"嘴+手+脑"。

---

## 在 Lumina 里怎么创建 Agent

```java
// 文件：lumina-modules/lumina-business-agent/.../domain/enums/AgentTypeEnum.java
public enum AgentTypeEnum {
    REACT("ReAct"),              // 推理-行动循环
    PLAN_EXECUTE("PlanAndExecute"), // 先规划再执行
    MULTI_AGENT("MultiAgent"),   // 多 Agent 协作
    RAG("RAG");                  // 知识库问答
}
```

创建 Agent 时选类型——ReAct 是默认的，够用大多数场景。

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| Chatbot | 只会聊天（嘴） |
| Agent | 会用工具+循环思考（嘴+手+脑） |
| ReAct | Reason→Act→Observe 循环 |
| 进化路径 | Chatbot → Tool Agent → Autonomous Agent |

---

## 🎉 模块 A 完成

你已经学完了 AI 基础认知（A01-A05），现在你懂得：
- LLM 是什么（海量文本训练的预测器）
- Token 怎么算（LLM 的计价单位）
- Prompt 怎么写（任务说明书 + CoT/Few-shot/防注入）
- Chatbot vs Agent（从只会说到能做事）

---

## 下一步

进入 [模块 B：Agent 核心](README.md)——深入 ReAct 循环、Plan-Execute 模式、AgentScope SDK。

> 🚀 [B01 — Agent 是什么 →](B01-what-is-agent.md)

---

📝 **本篇撰写期间修正的代码**：无。
