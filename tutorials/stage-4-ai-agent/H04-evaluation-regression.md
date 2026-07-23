# H04 — 评估回归

> **前置要求**：已完成 [H03-A/B Testing](H03-ab-testing.md)
> **预计阅读**：10 分钟
> **难度**：⭐⭐☆☆☆

---

## 这节解决什么问题

改了 Prompt 后，怎么知道"原来能答对的题现在还答对吗"？这叫**回归测试**——确保改动没有引入退化。

---

## 回归测试流程

```
1. 标记基线 Run（当前版本的效果快照）
2. 改 Prompt / 换模型
3. 再跑一次评估
4. 对比两次结果（哪些题从"通过"变"失败"了？）
```

---

## Lumina 的实现

```java
// EvaluationService
// 标记基线
POST /evaluations/runs/{id}/baseline

// 批量回归（一次跑多个数据集）
POST /evaluations/regression/batch

// 对比两个 Run
GET /evaluations/runs/compare?runA=1&runB=2
// 返回：哪些题进步了、哪些退步了
```

---

## Prompt 版本对比

```java
// 对比两个 Prompt 版本的内容差异
GET /evaluations/prompts/compare?name=react&vA=1&vB=2
// 行级 diff
```

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| 基线 | 当前版本的评估快照 |
| 回归 | 改动后重跑，对比是否退化 |
| 批量回归 | 一次跑多个数据集 |
| 版本对比 | Prompt 行级 diff |

---

## 🎉 模块 H 完成

> 🚀 [I01 — 工作流编排 →](I01-workflow-orchestration.md)

---

📝 **本篇撰写期间修正的代码**：无。
