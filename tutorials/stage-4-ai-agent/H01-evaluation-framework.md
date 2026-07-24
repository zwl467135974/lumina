# H01 — Agent 评估框架：改了 Prompt 怎么知道变好了

> **前置要求**：已完成 [模块 G 成本管控](README.md)
> **预计阅读**：20 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

你给 Agent 改了一句 Prompt，从 "简洁回答" 改成 "详细回答"。改完后：
- 用户说"感觉好了一点"——但到底是好了还是差了？
- 改 Prompt 前通过率 85%，改完是 90% 还是 80%？

**没有量化评估，就是在盲改。** Lumina 的评估框架让你用标准数据集跑 Agent、自动打分、对比历史。

---

## 先建立直觉：考试判卷

评估 Agent 就像给学生**考试**：

1. **出卷**：准备一套标准问答题（数据集）
2. **考试**：Agent 逐题作答
3. **判卷**：自动打分（4 种评分标准）
4. **出成绩单**：通过率、分科成绩、和历史考试对比

---

## 评估流程

```
┌──────────────────────────────────────────────────┐
│  1. 创建数据集（YAML 或 DB）                       │
│     TestCase: { input, expected, category, scorer } │
├──────────────────────────────────────────────────┤
│  2. 启动评估（指定 Agent + 数据集）                 │
│     POST /api/v1/evaluations/run                  │
│     { agentId: 243, datasetId: 1 }                │
├──────────────────────────────────────────────────┤
│  3. 逐条执行 + 打分                                │
│     for (TestCase tc : dataset) {                 │
│         actual = agent.execute(tc.input);         │
│         score = scorer.score(tc.expected, actual);│
│     }                                             │
├──────────────────────────────────────────────────┤
│  4. 生成报告                                       │
│     通过率: 85%  (17/20)                           │
│     分类: 数学 90% | 时间 80% | RAG 75%            │
│     对比上次: +5% ↑                                │
└──────────────────────────────────────────────────┘
```

---

## 数据模型

### TestCase（单条测试用例）

```java
// 文件：lumina-agent-core/.../evaluation/model/TestCase.java
public class TestCase {
    private String id;           // 用例 ID（如 "math-1"）
    private String category;     // 分类（如 "数学"、"时间"、"RAG"）
    private String input;        // 用户输入
    private String expected;     // 期望输出
    private ScoringMethod scorer; // 评分方法（EXACT_MATCH / CONTAINS / SEMANTIC / LLM_JUDGE）
}
```

### EvaluationDataset（数据集）

```java
// 文件：lumina-modules/lumina-business-agent/.../evaluation/model/EvaluationDataset.java
public class EvaluationDataset {
    private Long id;
    private String name;          // "运营平台 v1 数据集"
    private List<TestCase> cases; // 测试用例列表
    private String description;
}
```

数据集可以存 DB（通过 API 管理）或 YAML 文件：

```yaml
# 文件：examples/ops-platform/data/eval-dataset.yaml
- id: "math-1"
  category: "数学"
  input: "1+1=?"
  expected: "2"
  scorer: EXACT_MATCH

- id: "time-1"
  category: "时间"
  input: "现在是几月？"
  expected: "7月"
  scorer: CONTAINS

- id: "rag-1"
  category: "RAG"
  input: "Lumina 支持哪些向量数据库？"
  expected: "Qdrant 和 Milvus"
  scorer: SEMANTIC
```

---

## EvaluationRun（评估运行记录）

```java
// 文件：lumina-modules/lumina-business-agent/.../infrastructure/entity/EvaluationRunDO.java
// 存储在 lumina_evaluation_run 表
public class EvaluationRunDO {
    private Long id;
    private Long agentId;           // 评估的 Agent
    private Long datasetId;         // 数据集
    private String status;          // RUNNING / COMPLETED / FAILED
    private int totalCases;         // 总用例数
    private int passedCases;        // 通过数
    private double passRate;        // 通过率
    private String modelInfo;       // 使用的模型（用于对比不同模型）
    private Integer promptVersion;  // Prompt 版本（用于对比不同 Prompt）
    private LocalDateTime createTime;
}
```

每次评估运行都记录**完整上下文**（哪个 Agent + 哪个数据集 + 哪个模型 + 哪个 Prompt 版本），方便历史对比。

---

## 通过率统计

```java
// 文件：lumina-modules/lumina-business-agent/.../service/impl/EvaluationServiceImpl.java
public EvaluationRunDTO runEvaluation(Long agentId, Long datasetId) {
    EvaluationDataset dataset = loadDataset(datasetId);
    int passed = 0;

    for (TestCase tc : dataset.getCases()) {
        // 1. Agent 执行
        String actual = agentService.executeAgent(agentId, tc.getInput(), null);

        // 2. 自动打分
        EvaluationScorer scorer = scorerMap.get(tc.getScorer());
        ScoreResult result = scorer.score(tc.getExpected(), actual, tc.getInput());

        if (result.isPassed()) passed++;
    }

    // 3. 统计
    double passRate = (double) passed / dataset.getCases().size();

    // 4. 持久化（供历史对比）
    EvaluationRunDO run = new EvaluationRunDO();
    run.setAgentId(agentId);
    run.setPassedCases(passed);
    run.setPassRate(passRate);
    runMapper.insert(run);

    return toDTO(run);
}
```

---

## 历史对比：改了 Prompt 到底好了没

```
评估历史（Agent 243 + 数据集 v1）

Run #5  Prompt v2 (详细)  GLM-4-Flash   通过率 90%  ← 改完
Run #4  Prompt v1 (简洁)  GLM-4-Flash   通过率 85%  ← 改之前
Run #3  Prompt v1 (简洁)  GPT-4         通过率 92%  ← 换模型试过
Run #2  Prompt v1 (简洁)  GLM-4         通过率 88%
Run #1  Prompt v1 (简洁)  GLM-4-Flash   通过率 83%  ← 基线
```

一眼看出：Prompt 改完后通过率 +5%（83→90），但还不如 GPT-4（92%）。**量化决策，不再盲猜。**

---

## CI/CD 集成

评估框架可以接入 CI 流水线——每次改 Prompt 或升级模型时自动跑评估：

```yaml
# GitHub Actions 示例
- name: Run Agent Evaluation
  run: |
    curl -X POST http://lumina/api/v1/evaluations/run \
      -d '{"agentId": 243, "datasetId": 1}' \
      -H "Authorization: Bearer ${{ secrets.LUMINA_TOKEN }}"

- name: Check Pass Rate
  run: |
    PASS_RATE=$(curl .../evaluations/latest | jq '.passRate')
    if (( $(echo "$PASS_RATE < 0.85" | bc -l) )); then
      echo "评估通过率下降！当前: $PASS_RATE"
      exit 1
    fi
```

通过率低于阈值（如 85%）就阻止部署——和单元测试一样的作用。

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| 评估 | 标准数据集跑 Agent → 自动打分 → 对比历史 |
| TestCase | input + expected + scorer（评分方法） |
| EvaluationRun | 一次评估的完整记录（Agent + 数据集 + 模型 + Prompt 版本） |
| 通过率 | passed / total，用于历史趋势对比 |
| CI 集成 | 通过率低于阈值阻止部署（回归门禁） |

### 自测题

1. 为什么每次 EvaluationRun 都要记录 modelInfo 和 promptVersion？
   <details><summary>答案</summary>为了历史对比。改了 Prompt 后对比通过率变化，需要知道上次用的是哪个版本。不记录就无法回答"是 Prompt 变好还是模型变好"。</details>

2. 改 Prompt 后通过率从 85% 涨到 90%，但用户说"感觉变差了"——可能是什么原因？（提示：数据集覆盖率）
   <details><summary>答案</summary>数据集覆盖率不足。如果数据集只覆盖数学/时间类问题，可能创意类回答变差了但数据集测不到。需要扩充数据集覆盖更多场景。</details>

3. CI 中设置通过率阈值为 85%，但某次评估只有 84%——应该阻止部署吗？
   <details><summary>答案</summary>不一定阻止。84% 可能是统计波动（差 1 个用例），建议查看具体哪条用例失败了——如果是边缘 case 偶发失败，可以放行；如果是核心能力退化，应该阻止。</details>

> 🚀 [H02 — 四种评分器 →](H02-four-scorers.md)

---

📝 **本篇撰写期间修正的代码**：无。
