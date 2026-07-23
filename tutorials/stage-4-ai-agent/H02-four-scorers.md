# H02 — 四种评分器

> **前置要求**：已完成 [H01-评估框架](H01-evaluation-framework.md)
> **预计阅读**：10 分钟
> **难度**：⭐⭐☆☆☆

---

## 四种评分方式

| 评分器 | 原理 | 适合场景 | 示例 |
|--------|------|----------|------|
| **ExactMatch** | 精确匹配 | 答案固定 | "1+1=?" → "2" |
| **Contains** | 包含关键词 | 答案有关键词 | "首都?" → 包含"北京" |
| **SemanticSimilarity** | 语义相似度（向量余弦） | 答案不唯一 | "解释RAG" → 意思相近即可 |
| **LLMJudge** | LLM 当裁判打 1-5 分 | 主观题 | "写一首诗" → AI 判断质量 |

---

## ExactMatch（最简单）

```java
// 文件：.../evaluation/scorer/ExactMatchScorer.java
public double score(String expected, String actual) {
    return expected.trim().equals(actual.trim()) ? 1.0 : 0.0;
}
```

## LLMJudge（最智能）

```java
// 文件：.../evaluation/scorer/LlmJudgeScorer.java
// 让 LLM 当裁判："给这个回答打 1-5 分，判断质量"
public double score(String expected, String actual, String input) {
    String prompt = "问题: " + input + "\n期望: " + expected + "\n实际回答: " + actual +
                    "\n请打 1-5 分，只回复数字。";
    int score = Integer.parseInt(llm.call(prompt).trim());
    return score / 5.0;    // 归一化到 0-1
}
```

**AI 给 AI 打分**——适合主观评价（创意写作、总结质量）。

---

## 小结

| 评分器 | 一句话记忆 |
|------|-----------|
| ExactMatch | 精确匹配（0 或 1） |
| Contains | 包含关键词 |
| SemanticSimilarity | 向量余弦相似度 |
| LLMJudge | LLM 当裁判打 1-5 分 |

> 🚀 [H03 — A/B Testing →](H03-ab-testing.md)

---

📝 **本篇撰写期间修正的代码**：无。
