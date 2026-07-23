# H01 — Agent 评估框架

> **前置要求**：已完成 [模块 G](README.md)
> **预计阅读**：15 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

改了 Prompt 或模型，怎么知道是变好了还是变差了？Lumina 的评估框架：用标准问答数据集跑 Agent，自动打分、统计通过率。

---

## 评估流程

```
1. 准备数据集（标准问答对）
   TestCase: { input: "1+1=?", expected: "2", category: "数学" }

2. 让 Agent 跑每个 Case
   Agent(input) → actual

3. 自动打分（4 种评分器）
   Score = Scorer.score(expected, actual)

4. 统计报告
   通过率 / 分类统计 / 历史趋势
```

---

## 数据集格式（YAML）

```yaml
# 文件：examples/ops-platform/data/eval-dataset.yaml
- id: "math-1"
  category: "数学"
  input: "1+1=?"
  expected: "2"

- id: "time-1"
  category: "时间"
  input: "现在是几月？"
  expected: "7月"
```

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| 评估 | 标准数据集跑 Agent → 自动打分 |
| TestCase | input + expected + category |
| 数据集 | YAML 格式 |
| 价值 | 改 Prompt/模型后有量化对比 |

> 🚀 [H02 — 四种评分器 →](H02-four-scorers.md)

---

📝 **本篇撰写期间修正的代码**：无。
