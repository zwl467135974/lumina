# H02 — 四种评分器：从精确匹配到 AI 当裁判

> **前置要求**：已完成 [H01 评估框架](H01-evaluation-framework.md)
> **预计阅读**：18 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

评估框架要给 Agent 的回答打分——但"回答对不对"的标准不是唯一的：

- 数学题 "1+1=?" → 答案必须精确等于 "2"
- 常识题 "北京是首都吗？" → 包含 "是" 就算对
- 开放题 "解释什么是 RAG" → 意思相近就算对，不需要逐字匹配
- 创意题 "写一首诗" → 没有标准答案，需要 AI 判断质量

Lumina 提供 **4 种评分器**，每种适合不同的题型。

---

## 先建立直觉：四种判卷老师

| 老师 | 风格 | 适合什么题 |
|------|------|-----------|
| **ExactMatch**（严格老师） | 答案必须一模一样 | 数学计算、固定答案 |
| **Contains**（宽容老师） | 包含关键词就行 | 事实核查、常识判断 |
| **SemanticSimilarity**（语文老师） | 意思相近就行，看语义 | 解释题、开放问答 |
| **LLMJudge**（外聘专家） | AI 来当裁判打分 | 主观题、创意写作 |

---

## 评分器架构

```java
// 文件：lumina-modules/lumina-business-agent/.../evaluation/scorer/EvaluationScorer.java
public interface EvaluationScorer {
    /**
     * @param expected 期望答案
     * @param actual   Agent 实际回答
     * @param input    原始问题（LLMJudge 需要）
     * @return ScoreResult（分数 0-1 + 是否通过 + 评语）
     */
    ScoreResult score(String expected, String actual, String input);
}
```

```java
// 文件：.../scorer/ScoreResult.java
public record ScoreResult(
    double score,        // 0.0 - 1.0
    boolean passed,      // 分数 >= 阈值
    String reason        // 评语（为什么这个分）
) {}
```

四种实现通过策略模式注入：

```java
// EvaluationServiceImpl 中
private final Map<ScoringMethod, EvaluationScorer> scorerMap;
// scorerMap.get(ScoringMethod.EXACT_MATCH) → ExactMatchScorer 实例
```

---

## 1. ExactMatchScorer（精确匹配）

```java
// 文件：.../scorer/ExactMatchScorer.java
public class ExactMatchScorer implements EvaluationScorer {
    @Override
    public ScoreResult score(String expected, String actual, String input) {
        boolean match = expected.trim().equalsIgnoreCase(actual.trim());
        return new ScoreResult(match ? 1.0 : 0.0, match,
            match ? "精确匹配" : "答案不匹配");
    }
}
```

**特点**：
- 最严格——差一个字就是 0 分
- 最快——纯字符串比较，无 LLM 调用
- 适合：数学题、固定格式答案、二选一判断

**阈值建议**：通过线 = 1.0（只有完全匹配才算通过）

---

## 2. ContainsScorer（关键词包含）

```java
// 文件：.../scorer/ContainsScorer.java
public class ContainsScorer implements EvaluationScorer {
    @Override
    public ScoreResult score(String expected, String actual, String input) {
        // expected 可以是多个关键词，用 | 分隔
        // 如 "北京|Beijing|首都" → 包含任一即通过
        String[] keywords = expected.split("\\|");
        for (String keyword : keywords) {
            if (actual.toLowerCase().contains(keyword.trim().toLowerCase())) {
                return new ScoreResult(1.0, true, "包含关键词: " + keyword);
            }
        }
        return new ScoreResult(0.0, false, "未包含任何期望关键词");
    }
}
```

**特点**：
- 比 ExactMatch 宽容——答案不需要完全一致
- 支持多关键词（`|` 分隔）
- 适合：事实核查（"中国的首都是？" → 包含"北京"就行）

**阈值建议**：通过线 = 1.0

---

## 3. SemanticSimilarityScorer（语义相似度）

```java
// 文件：.../scorer/SemanticSimilarityScorer.java
public class SemanticSimilarityScorer implements EvaluationScorer {

    private final EmbeddingService embeddingService;  // 向量化服务

    @Override
    public ScoreResult score(String expected, String actual, String input) {
        // 1. 把期望答案和实际答案都转成向量
        float[] expectedVec = embeddingService.embed(expected);
        float[] actualVec = embeddingService.embed(actual);

        // 2. 计算余弦相似度
        double similarity = cosineSimilarity(expectedVec, actualVec);

        // 3. 相似度 > 0.75 算通过
        boolean passed = similarity >= 0.75;
        return new ScoreResult(similarity, passed,
            String.format("语义相似度: %.2f", similarity));
    }
}
```

**特点**：
- 最智能——理解语义而非字面
- 依赖 Embedding 模型（需配置 RAG_EMBEDDING_* 环境变量）
- 适合：开放问答、概念解释（"什么是 RAG？" → 不同表述都算对）

**阈值建议**：通过线 = 0.75（可调，0.7 宽松 / 0.8 严格）

### 阈值调优指南

| 阈值 | 效果 | 适用 |
|------|------|------|
| 0.65 | 很宽松，意思沾边就过 | 初步筛选 |
| 0.75 | 平衡（推荐默认） | 大多数场景 |
| 0.85 | 很严格，表述要非常接近 | 高精度场景 |

> 调阈值的方法：先用 0.75 跑一轮，人工抽查 10 条——如果通过的里面有明显错的，提高阈值；如果没通过的其实是对的，降低阈值。

---

## 4. LlmJudgeScorer（AI 当裁判）

```java
// 文件：.../scorer/LlmJudgeScorer.java
public class LlmJudgeScorer implements EvaluationScorer {

    private final ChatModelFactory chatModelFactory;

    private static final String JUDGE_PROMPT = """
        你是一个评分裁判。请根据以下信息给回答打分（1-5 分）：

        问题：%s
        参考答案：%s
        实际回答：%s

        评分标准：
        5分：完美回答，准确且全面
        4分：基本正确，有小瑕疵
        3分：部分正确，有遗漏
        2分：大部分错误
        1分：完全错误

        只回复一个数字（1-5）。
        """;

    @Override
    public ScoreResult score(String expected, String actual, String input) {
        String prompt = String.format(JUDGE_PROMPT, input, expected, actual);

        // 调用 LLM 评分
        String llmResponse = callLlm(prompt);
        int score = parseScore(llmResponse);  // 解析 1-5 分

        double normalized = score / 5.0;  // 归一化到 0-1
        boolean passed = normalized >= 0.6;  // 3 分及以上算通过

        return new ScoreResult(normalized, passed,
            String.format("LLM 评分: %d/5", score));
    }
}
```

**特点**：
- 最灵活——能判断主观质量（创意、逻辑、完整性）
- 最贵——每次评分都调一次 LLM
- 适合：创意写作、总结质量、多维度评估

**阈值建议**：通过线 = 0.6（3/5 分及以上）

### 注意事项
- LLM 可能返回非数字（加了多余文字）→ 需要 `parseScore` 做容错
- 同一答案多次评分可能结果不同 → 建议跑 3 次取平均
- 评分用的 LLM 应该比被评估的 Agent 模型更强（GLM-4 评 GLM-4-Flash）

---

## 四种评分器对比

| 评分器 | 速度 | 成本 | 精确度 | 适用场景 |
|--------|------|------|--------|---------|
| ExactMatch | ⚡ 最快 | 免费 | 精确但死板 | 数学、固定答案 |
| Contains | ⚡ 很快 | 免费 | 关键词级 | 事实核查 |
| SemanticSimilarity | 🔵 中等 | Embedding 费用 | 语义级 | 开放问答 |
| LLMJudge | 🐢 最慢 | LLM 调用费 | 最灵活 | 主观题、创意 |

---

## 小结

| 评分器 | 一句话记忆 | 通过线 |
|--------|-----------|-------|
| ExactMatch | 精确匹配（0 或 1） | 1.0 |
| Contains | 包含关键词（支持 `|` 多选） | 1.0 |
| SemanticSimilarity | 向量余弦相似度 | 0.75 |
| LLMJudge | LLM 当裁判打 1-5 分 | 0.6（3/5） |

### 自测题

1. 为什么 LLMJudge 建议跑 3 次取平均？（提示：LLM 输出的随机性）
   <details><summary>答案</summary>LLM 输出有随机性（temperature > 0），同一答案多次评分可能不同（如 4/3/4）。取平均减少方差，结果更稳定。</details>

2. SemanticSimilarity 的阈值 0.75 怎么调优？
   <details><summary>答案</summary>先用 0.75 跑一轮，人工抽查 10 条：通过的里面有明显错的→提高阈值；没通过的其实是对的→降低阈值。反复调整直到满意。</details>

3. 四种评分器哪个最快？为什么？
   <details><summary>答案</summary>ExactMatch。纯字符串比较（equals），无 LLM/Embedding 调用，纳秒级完成。</details>

4. 如果评估"写一首关于秋天的诗"，用哪个评分器？为什么不用 ExactMatch？
   <details><summary>答案</summary>LLMJudge。写诗没有标准答案，需要 AI 判断创意/意境/韵律。ExactMatch 要求逐字匹配完全不适合。</details>

> 🚀 [H03 — A/B Testing →](H03-ab-testing.md)

---

📝 **本篇撰写期间修正的代码**：无。
