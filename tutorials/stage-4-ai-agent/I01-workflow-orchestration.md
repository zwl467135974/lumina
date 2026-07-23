# I01 — 多 Agent 工作流编排

> **前置要求**：已完成 [模块 H](README.md)
> **预计阅读**：20 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

复杂任务一个 Agent 搞不定——需要多个步骤、多个 Agent 配合。**工作流**就是定义"谁先做、谁后做、什么条件走哪条路"。

---

## 工作流是什么？先建立直觉

### 类比：流水线

工厂的流水线：原料 → 加工 → 质检 → 包装 → 出库。每个环节有人/机器负责，按顺序流转。

**Lumina 工作流**就是 Agent 的流水线——用 YAML 定义节点和连线，按 DAG（有向无环图）执行。

---

## 一个真实工作流（运维巡检）

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

**人类可读的 YAML**——小白一看就懂"采集→分析→判断→分支"。

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| 工作流 | Agent 流水线（YAML 定义 DAG） |
| node | 一个步骤（agent/condition/loop/...） |
| edge | 连线（谁→谁） |
| 变量传递 | outputVar → 后续节点用 #变量名 引用 |

> 🚀 [I02 — 六种节点类型 →](I02-six-node-types.md)

---

📝 **本篇撰写期间修正的代码**：无。
