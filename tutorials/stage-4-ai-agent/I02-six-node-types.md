# I02 — 六种节点类型

> **前置要求**：已完成 [I01-工作流编排](I01-workflow-orchestration.md)
> **预计阅读**：15 分钟
> **难度**：⭐⭐☆☆☆

---

## 六种节点

| 节点类型 | 作用 | 类比 |
|----------|------|------|
| **agent** | 执行一个 Agent | 流水线工人 |
| **condition** | 条件判断，走不同分支 | 质检分流 |
| **loop** | 循环执行 | 批量处理 |
| **parallel** | 并行执行多个分支 | 多工位同时开工 |
| **transform** | 数据转换（SpEL 表达式） | 加工改型 |
| **human** | 人工审批（暂停等待） | 人工审核 |

---

## condition（条件分支）

```yaml
- id: check-severity
  type: condition
  expression: "#analysis_json.contains('P0')"
  trueBranch: human-approval    # P0 走这
  falseBranch: auto-report      # 非P0 走这
```

## human（人工审批）

```yaml
- id: human-approval
  type: human
  prompt: "检测到 P0 故障，请审核是否告警"
  options: ["approve", "reject"]
  decisionVar: "approval_decision"
```

**特殊**：执行到 human 节点时工作流**暂停**，等人审批后 resume 继续。

## parallel（并行）

```yaml
- id: parallel-check
  type: parallel
  branches:
    - [check-cpu, check-memory]
    - [check-disk, check-network]
  # 两组同时执行，全部完成后才继续
```

---

## 小结

| 节点 | 什么时候用 |
|------|-----------|
| agent | 执行 AI 任务 |
| condition | if/else 分支 |
| loop | 循环（批量） |
| parallel | 并行加速 |
| transform | 数据转换 |
| human | 需要人工确认 |

> 🚀 [I03 — Flowable 引擎 →](I03-flowable-bpmn-engine.md)

---

📝 **本篇撰写期间修正的代码**：无。
