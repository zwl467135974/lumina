# I01 — 多 Agent 协作：工作流编排 + Supervisor 模式

> **前置要求**：已完成 [模块 H 质量保障](README.md)
> **预计阅读**：25 分钟
> **难度**：⭐⭐⭐⭐☆

---

## 这节解决什么问题

复杂任务一个 Agent 搞不定——需要多个 Agent 配合。Lumina 提供**两种多 Agent 协作模式**：

| 模式 | 适合场景 | 控制方式 |
|------|---------|---------|
| **DAG 工作流** | 固定流程（采集→分析→报告） | 人类定义 YAML 图 |
| **Supervisor 模式** | 动态路由（LLM 决定找谁） | AI 路由器自动选专家 |

---

## 模式一：DAG 工作流编排

### 类比：工厂流水线

原料 → 加工 → 质检 → 包装 → 出库。每个环节有专人负责，按顺序流转。

**Lumina 工作流**就是 Agent 的流水线——用 YAML 定义节点和连线，按 DAG（有向无环图）执行。

### 运维巡检工作流

```yaml
# 文件：examples/ops-platform/config/workflow-dag.yaml（简化）
nodes:
  - id: collect          # 步骤1：采集数据
    type: agent
    agentId: 1
    outputVar: inspection_result

  - id: analyze          # 步骤2：分析异常
    type: agent
    agentId: 2
    outputVar: analysis_json

  - id: check-severity   # 步骤3：条件判断
    type: condition
    expression: "#analysis_json.contains('P0')"
    trueBranch: human-approval    # P0 → 人工审批
    falseBranch: auto-report      # 非P0 → 自动报告

  - id: human-approval   # 步骤4a：人工审批
    type: human
    options: ["approve", "reject"]

  - id: auto-report      # 步骤4b：自动报告
    type: agent
    agentId: 2

edges:
  - from: collect
    to: analyze
  - from: analyze
    to: check-severity
```

**特点**：流程固定、人类定义、可预测。每个节点是独立的 Agent。

> 6 种节点类型的详细用法见 [I02 — 六种节点类型](I02-six-node-types.md)。

---

## 模式二：Supervisor 模式（LLM 路由）

### 类比：医院分诊台

你到医院不知道挂什么科——分诊台的**护士**（Supervisor）问完症状后告诉你：
- "这个去内科" → 内科医生（专家 Agent A）处理
- "处理完发现需要拍片" → 护士再看 → "去放射科" → 放射科医生（专家 Agent B）
- "结果都出来了" → 护士汇总 → "没什么大问题，回去休息"

Supervisor = **分诊台护士**，专家 Agent = **各科室医生**。

### 架构

```
用户 "帮我分析销售数据，写封汇报邮件"
    │
    ▼
┌─────────────────────────────────────┐
│  Supervisor（路由器 LLM）             │
│  可用专家：                           │
│    - 数据分析专家: 擅长数据解读        │
│    - 邮件写作专家: 擅长商务邮件        │
├─────────────────────────────────────┤
│  第 1 轮：选"数据分析专家"             │
│    └──→ 专家分析数据 → 结果回传        │
│  第 2 轮：选"邮件写作专家"             │
│    └──→ 专家根据分析结果写邮件 → 回传   │
│  第 3 轮：FINISH → 汇总回复            │
└─────────────────────────────────────┘
    │
    ▼
返回用户：完整分析 + 汇报邮件
```

### 配置方式

Agent 类型设为 `MultiAgent`，在 `sub_agents` JSON 中配置专家列表：

```json
[
  {
    "name": "数据分析专家",
    "description": "擅长解读数字、计算趋势、发现问题",
    "sysPrompt": "你是数据分析专家，擅长从数据中发现洞察。"
  },
  {
    "name": "邮件写作专家",
    "description": "擅长撰写专业商务邮件",
    "sysPrompt": "你是邮件写作专家，根据给定的信息撰写专业邮件。"
  }
]
```

### 实现原理

```java
// 文件：lumina-agent-core/.../engine/MultiAgentSupervisor.java
public class MultiAgentSupervisor {

    // Supervisor 循环
    public Msg execute(List<Msg> messages) {
        for (int round = 0; round < maxRounds; round++) {
            // 1. Supervisor 判断下一步交给谁
            String decision = supervisor.call(routePrompt).block().getTextContent();

            // 2. FINISH → 汇总所有专家结果
            if ("FINISH".equalsIgnoreCase(decision)) {
                return generateFinalSummary();
            }

            // 3. 找到匹配的专家并执行
            SubAgentSpec expert = findAgent(decision);
            Msg result = expert.call(task).block();
            expertResults.append(result.getTextContent());
        }
    }
}
```

**关键设计**：
- Supervisor 有自己的 System Prompt，包含专家列表 + 能力描述
- 每轮输出只有专家名或 FINISH（LLM 只做路由判断不做内容生成）
- 专家各自独立配置 Model + Toolkit（可以不同模型、不同工具）
- `maxRounds` 防止无限循环（默认 5 轮）

### 子 Agent 配置继承

每个专家可以独立配置，也可以继承父 Agent：

| 字段 | null 时行为 |
|------|------------|
| `sysPrompt` | 继承父 Agent 的 promptTemplate |
| `llmConfig` | 继承父 Agent 的 LLM 配置（同模型） |
| `toolConfig` | 继承父 Agent 的工具配置 |

**典型用法**：父 Agent 配 GLM-4-Flash（便宜），专家 A 用 Flash 继承（数据分析不需要强模型），专家 B 用 GPT-4（邮件质量要求高）。

---

## 两种模式怎么选

| 维度 | DAG 工作流 | Supervisor 模式 |
|------|-----------|----------------|
| 流程确定性 | 固定（人定义） | 动态（AI 决定） |
| 可预测性 | 高（每次一样） | 中（依赖 LLM 判断） |
| 灵活性 | 低（改流程要改 YAML） | 高（自动适应不同请求） |
| 调试难度 | 低（DAG 可视化） | 中（看 Supervisor 日志） |
| 适合场景 | 运维巡检、审批流程 | 客服路由、研究分析 |

**经验法则**：流程确定用 DAG，路由不确定用 Supervisor。两者可以组合——DAG 的 AgentNode 可以是一个 MultiAgent 类型的 Agent。

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| DAG 工作流 | 固定流程，YAML 定义节点和连线 |
| Supervisor 模式 | LLM 路由器自动选择专家 Agent |
| SubAgentConfig | 专家配置（name/description/sysPrompt，可继承父 Agent） |
| maxRounds | Supervisor 最大路由轮次（默认 5） |
| 选择原则 | 流程确定用 DAG，路由不确定用 Supervisor |

### 自测题

1. DAG 工作流和 Supervisor 模式的核心区别是什么？
   <details><summary>答案</summary>DAG 是人类预定义的固定流程图（可预测但不灵活）；Supervisor 是 LLM 动态路由（灵活但依赖 AI 判断质量）。</details>

2. Supervisor 每轮输出什么？为什么不直接输出回答内容？
   <details><summary>答案</summary>只输出专家名或 FINISH。因为 Supervisor 只做路由判断（谁擅长处理），内容生成交给专家。分离关注点，路由和执行各司其职。</details>

3. 子 Agent 的 llmConfig 为 null 时会发生什么？
   <details><summary>答案</summary>继承父 Agent 的 LLM 配置（用同一个模型）。这样可以省配置——所有专家用同一个模型时不需要重复配。</details>

4. 为什么 Supervisor 模式需要 maxRounds？
   <details><summary>答案</summary>防止 LLM 路由判断失误导致无限循环（反复在两个专家间来回切换）。和 ReAct 的 maxIters 一样是安全阀。</details>

> 🚀 [I02 — 六种节点类型 →](I02-six-node-types.md)

---

📝 **本篇撰写期间修正的代码**：无（MultiAgent Supervisor 为本次新增功能）。
