# E1: Agent 评估框架 — 设计方案

> 基于 Lumina v2.0.0 现有架构和 AgentScope 1.0.7 能力设计。

## 一、设计目标

为 Lumina 提供开箱即用的 Agent 质量评估能力，补全 **构建 → 评估 → 部署 → 监控** 平台闭环。

核心问题：
- Agent 换了 Prompt / 模型 / 工具后，效果变好还是变差？
- 如何量化 Agent 质量（准确率、延迟、Token 消耗）？
- 如何做回归测试（改配置后历史用例是否仍通过）？

## 二、模块结构

```
lumina-modules/lumina-evaluation/           # 新增独立业务模块
├── pom.xml                                 # 依赖 lumina-agent-core + lumina-framework
├── src/main/java/io/lumina/evaluation/
│   ├── model/                              # 领域模型
│   │   ├── EvaluationDataset.java          # 评估数据集（N 条测试用例）
│   │   ├── TestCase.java                   # 单条测试用例（input + expected + category）
│   │   ├── EvaluationRun.java              # 一次评估运行（数据集 + Agent + 评分方法）
│   │   ├── CaseResult.java                 # 单条评估结果（actual + score + latency + tokens）
│   │   ├── RunReport.java                  # 评估报告（汇总统计 + 分布 + 趋势）
│   │   └── ScoringMethod.java             # 评分方法枚举
│   │
│   ├── scorer/                             # 评分器（策略模式）
│   │   ├── Scorer.java                     # 评分器接口
│   │   ├── ExactMatchScorer.java           # 精确匹配（分类/结构化输出）
│   │   ├── ContainsScorer.java             # 关键词包含（答案中是否包含预期信息）
│   │   ├── SemanticSimilarityScorer.java   # 语义相似度（Embedding 余弦）
│   │   └── LlmJudgeScorer.java            # LLM-as-Judge（GPT/DeepSeek 打分 1-5 分）
│   │
│   ├── runner/                             # 评估执行器
│   │   ├── EvaluationRunner.java           # 批量执行 + 评分 + 汇总
│   │   └── DatasetLoader.java              # YAML/JSON 数据集加载
│   │
│   ├── infrastructure/                     # 持久化
│   │   ├── entity/
│   │   │   ├── EvaluationDatasetDO.java    # 数据集表
│   │   │   └── EvaluationRunDO.java        # 评估运行记录表
│   │   └── mapper/
│   │       ├── EvaluationDatasetMapper.java
│   │       └── EvaluationRunMapper.java
│   │
│   ├── api/                                # REST API
│   │   ├── controller/EvaluationController.java
│   │   └── dto/
│   │       ├── CreateDatasetDTO.java
│   │       ├── RunEvaluationDTO.java
│   │       └── EvaluationReportVO.java
│   │
│   └── service/
│       ├── EvaluationService.java          # 接口
│       └── impl/EvaluationServiceImpl.java
│
├── src/main/resources/
│   └── db/migration/
│       └── V13__add_evaluation_tables.sql  # Flyway 迁移
│
└── src/test/java/
    └── io/lumina/evaluation/
        ├── scorer/
        │   ├── ExactMatchScorerTest.java
        │   ├── ContainsScorerTest.java
        │   └── SemanticSimilarityScorerTest.java
        └── runner/
            └── EvaluationRunnerTest.java
```

**依赖关系：**
```
lumina-evaluation
  ├── lumina-common          # 统一响应、异常
  ├── lumina-framework       # 配置、审计
  └── lumina-agent-core      # AgentExecutionEngine（执行 Agent）
                             # ChatModelFactory（LLM-as-Judge 评分器）
                             # OpenAICompatibleEmbeddingModel（语义相似度评分器）
```

## 三、核心模型设计

### 3.1 测试用例

```java
@Data
public class TestCase {
    private String id;                    // 用例 ID（唯一标识）
    private String input;                 // 用户输入
    private String expected;              // 预期输出（答案/分类/关键词）
    private String category;              // 分类标签（用于分组统计）
    private Map<String, String> tags;     // 扩展标签（如 difficulty: hard）
}
```

### 3.2 评估数据集

```java
@Data
public class EvaluationDataset {
    private Long id;
    private String name;                  // "客服 Agent 评估集"
    private String description;
    private Long agentId;                 // 关联的 Agent ID（可选）
    private String agentType;             // 关联的 Agent 类型
    private List<TestCase> cases;         // 测试用例列表
    private Long tenantId;
    private LocalDateTime createTime;
}
```

### 3.3 评分方法枚举

```java
public enum ScoringMethod {
    EXACT_MATCH,           // 精确匹配（expected == actual）
    CONTAINS,              // 包含匹配（actual.contains(expected)）
    SEMANTIC_SIMILARITY,   // 语义相似度（Embedding cosine ≥ threshold）
    LLM_JUDGE              // LLM-as-Judge（1-5 分制）
}
```

### 3.4 单条评估结果

```java
@Data
public class CaseResult {
    private String caseId;
    private String input;
    private String expected;
    private String actual;                // Agent 实际输出
    private double score;                 // 0.0 - 1.0（标准化得分）
    private String scoreDetail;           // 评分明细（如 "cosine=0.87, threshold=0.8"）
    private boolean passed;               // 是否通过（score ≥ threshold）
    private long latencyMs;               // 执行耗时
    private int promptTokens;             // 输入 Token
    private int completionTokens;         // 输出 Token
    private int totalTokens;              // 总 Token
    private String errorMessage;          // 执行错误（如果有）
}
```

### 3.5 评估报告

```java
@Data
public class RunReport {
    private Long runId;
    private String datasetName;
    private String agentType;
    private ScoringMethod scoringMethod;
    private LocalDateTime runTime;
    
    // 汇总统计
    private int totalCases;
    private int passedCases;
    private double passRate;              // 通过率 0-100%
    private double avgScore;              // 平均得分
    private double avgLatencyMs;          // 平均延迟
    private int totalTokens;              // 总 Token 消耗
    
    // 分组统计（按 TestCase.category）
    private Map<String, CategoryStat> categoryStats;
    
    // 失败用例
    private List<CaseResult> failedCases;
    
    // 全部结果
    private List<CaseResult> allResults;
}
```

## 四、评分器设计

### 4.1 评分器接口

```java
public interface Scorer {
    
    ScoringMethod getMethod();
    
    /**
     * 评估单条用例
     *
     * @param testCase    测试用例（含预期输出）
     * @param actual      Agent 实际输出
     * @param threshold   通过阈值（0-1）
     * @return 评分结果
     */
    CaseResult.Score score(TestCase testCase, String actual, double threshold);
}
```

### 4.2 评分器实现

#### ExactMatchScorer（精确匹配）
```java
@Component
public class ExactMatchScorer implements Scorer {
    public CaseResult.Score score(TestCase testCase, String actual, double threshold) {
        boolean match = actual.trim().equalsIgnoreCase(testCase.getExpected().trim());
        double score = match ? 1.0 : 0.0;
        return new CaseResult.Score(score, match ? "exact match" : "mismatch", score >= threshold);
    }
}
```

#### ContainsScorer（关键词包含）
```java
@Component
public class ContainsScorer implements Scorer {
    public CaseResult.Score score(TestCase testCase, String actual, double threshold) {
        boolean contains = actual.toLowerCase().contains(testCase.getExpected().toLowerCase());
        double score = contains ? 1.0 : 0.0;
        return new CaseResult.Score(score, contains ? "contains" : "not found", score >= threshold);
    }
}
```

#### SemanticSimilarityScorer（语义相似度）

**复用现有 `OpenAICompatibleEmbeddingModel`：**

```java
@Component
public class SemanticSimilarityScorer implements Scorer {

    private final OpenAICompatibleEmbeddingModel embeddingModel;
    // OpenAICompatibleEmbeddingModel 已在 lumina-agent-core 中实现
    // 支持 DashScope / SiliconFlow / OpenAI 兼容的 /embeddings 端点

    public CaseResult.Score score(TestCase testCase, String actual, double threshold) {
        // 1. Embed expected 和 actual
        float[] expectedVec = embeddingModel.embed(testCase.getExpected()).block();
        float[] actualVec = embeddingModel.embed(actual).block();
        
        // 2. 计算余弦相似度
        double cosine = cosineSimilarity(expectedVec, actualVec);
        
        // 3. 与阈值比较
        return new CaseResult.Score(cosine, 
            String.format("cosine=%.4f, threshold=%.2f", cosine, threshold),
            cosine >= threshold);
    }
    
    private double cosineSimilarity(float[] a, float[] b) {
        // 标准余弦相似度计算
    }
}
```

#### LlmJudgeScorer（LLM-as-Judge）

**复用现有 `ChatModelFactory` + AgentScope `Model`：**

```java
@Component
public class LlmJudgeScorer implements Scorer {

    private final ChatModelFactory chatModelFactory;
    private final LuminaAgentProperties agentProperties;
    // ChatModelFactory 已在 lumina-agent-core 中实现
    // 支持 DashScope / OpenAI / Anthropic / Ollama 模型创建

    private static final String JUDGE_PROMPT = """
        你是一个严格的评分员。请评估以下回答的质量。
        
        问题：{0}
        参考答案：{1}
        实际回答：{2}
        
        评分标准（1-5 分）：
        - 5分：完全正确，包含所有关键信息
        - 4分：基本正确，遗漏少量细节
        - 3分：部分正确，但有明显遗漏
        - 2分：大部分不正确
        - 1分：完全错误
        
        请只返回一个数字（1-5）。
        """;

    public CaseResult.Score score(TestCase testCase, String actual, double threshold) {
        // 1. 创建 Judge 模型（复用 ChatModelFactory）
        Model judgeModel = chatModelFactory.create(
            agentProperties.getLlm().toLLMConfig(),
            agentProperties.getLlm(),
            agentProperties.getLlm().getApiKey()
        );
        
        // 2. 构造评分 Prompt
        String prompt = MessageFormat.format(JUDGE_PROMPT,
            testCase.getInput(),
            testCase.getExpected(),
            actual
        );
        
        // 3. 调用 LLM 评分
        Msg msg = Msg.user(prompt);
        Msg response = judgeModel.call(msg);
        String judgeResult = response.getTextContent();
        
        // 4. 解析分数（1-5 → 0.0-1.0 标准化）
        int rawScore = Integer.parseInt(judgeResult.replaceAll("[^1-5]", "").substring(0, 1));
        double normalizedScore = rawScore / 5.0;
        
        return new CaseResult.Score(normalizedScore,
            String.format("judge=%d/5 (%s)", rawScore, response.getChatUsage().getTotalTokens() + " tokens"),
            normalizedScore >= threshold);
    }
}
```

## 五、评估执行器

### EvaluationRunner

```java
@Component
public class EvaluationRunner {

    private final AgentExecutionEngine executionEngine;  // 复用现有引擎
    private final Map<ScoringMethod, Scorer> scorers;    // 按方法注入

    /**
     * 执行评估
     *
     * @param dataset       评估数据集
     * @param agentId       被评估的 Agent ID
     * @param agentType     Agent 类型（用于 executionEngine.executeSync）
     * @param scoringMethod 评分方法
     * @param threshold     通过阈值（默认 0.7）
     * @return 评估报告
     */
    public RunReport run(EvaluationDataset dataset, 
                         Long agentId, 
                         String agentType,
                         ScoringMethod scoringMethod,
                         double threshold) {
        
        Scorer scorer = scorers.get(scoringMethod);
        List<CaseResult> results = new ArrayList<>();

        for (TestCase testCase : dataset.getCases()) {
            // 1. 执行 Agent（同步阻塞，null conversationId = 无记忆干扰）
            long start = System.currentTimeMillis();
            ExecuteResult execResult = executionEngine.executeSync(
                agentType, 
                testCase.getInput(), 
                null,  // AgentConfig（null = 使用默认配置）
                null   // 无会话上下文（每条用例独立执行）
            );
            long latency = System.currentTimeMillis() - start;

            // 2. 构造结果
            CaseResult caseResult = new CaseResult();
            caseResult.setCaseId(testCase.getId());
            caseResult.setInput(testCase.getInput());
            caseResult.setExpected(testCase.getExpected());
            caseResult.setActual(execExecResult.getResult());
            caseResult.setLatencyMs(latency);

            if (execResult.getTokenUsage() != null) {
                caseResult.setPromptTokens(execResult.getTokenUsage().getPromptTokens());
                caseResult.setCompletionTokens(execResult.getTokenUsage().getCompletionTokens());
                caseResult.setTotalTokens(execResult.getTokenUsage().getTotalTokens());
            }

            // 3. 评分
            if (execResult.getSuccess()) {
                CaseResult.Score score = scorer.score(testCase, execResult.getResult(), threshold);
                caseResult.setScore(score.value());
                caseResult.setScoreDetail(score.detail());
                caseResult.setPassed(score.passed());
            } else {
                caseResult.setScore(0.0);
                caseResult.setPassed(false);
                caseResult.setErrorMessage(execResult.getError());
            }

            results.add(caseResult);
        }

        // 4. 汇总报告
        return buildReport(dataset, agentType, scoringMethod, threshold, results);
    }
}
```

**关键设计决策：**
- 使用 `executeSync` 而非 `executeStream`，因为评估需要阻塞等待完整结果
- `conversationId = null` 确保每条用例独立执行（无历史记忆干扰）
- 支持并行执行（后续可用 `Flux.fromIterable(cases).flatMap(...)` 优化）
- Resilience4j 已在依赖链中，可包装 Judge 调用（重试 + 熔断）

## 六、数据集格式

### YAML 格式（推荐）

```yaml
name: "客服 Agent 评估集"
description: "意图分类 + 通用问答评估"
agentType: "customer-service"

cases:
  # 意图分类（ExactMatch）
  - id: "intent-001"
    input: "我要退款"
    expected: "refund"
    category: "intent-classification"
    tags:
      difficulty: easy

  - id: "intent-002"
    input: "查询我的订单状态"
    expected: "order-query"
    category: "intent-classification"
    tags:
      difficulty: easy

  # 通用问答（SemanticSimilarity）
  - id: "qa-001"
    input: "你们的退款政策是什么？"
    expected: "7天内无理由退款，商品需保持原包装"
    category: "policy-qa"
    tags:
      difficulty: medium

  # 工具调用（Contains — 检查关键信息）
  - id: "tool-001"
    input: "帮我查一下北京明天的天气"
    expected: "温度"    # 只要回答中包含"温度"就算通过
    category: "tool-calling"
    tags:
      difficulty: easy
```

### API 上传

```bash
# 从 YAML 文件创建数据集
POST /api/v1/evaluation/datasets
Content-Type: application/json
{
  "name": "客服 Agent 评估集",
  "agentType": "customer-service",
  "casesYaml": "<YAML content>"
}

# 或通过文件上传
POST /api/v1/evaluation/datasets/import
Content-Type: multipart/form-data
file: dataset.yaml
```

## 七、REST API 设计

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/evaluation/datasets` | 创建数据集 |
| GET | `/api/v1/evaluation/datasets` | 数据集列表 |
| GET | `/api/v1/evaluation/datasets/{id}` | 数据集详情 |
| DELETE | `/api/v1/evaluation/datasets/{id}` | 删除数据集 |
| POST | `/api/v1/evaluation/runs` | 执行评估 |
| GET | `/api/v1/evaluation/runs` | 评估运行历史 |
| GET | `/api/v1/evaluation/runs/{id}` | 评估报告详情 |
| GET | `/api/v1/evaluation/runs/{id}/trend` | 历史趋势（同一数据集多次评估对比） |

### 执行评估请求

```bash
POST /api/v1/evaluation/runs
{
  "datasetId": 1,
  "agentId": 10,
  "scoringMethod": "SEMANTIC_SIMILARITY",
  "threshold": 0.75
}
```

### 评估报告响应

```json
{
  "runId": 1,
  "datasetName": "客服 Agent 评估集",
  "agentType": "customer-service",
  "scoringMethod": "SEMANTIC_SIMILARITY",
  "runTime": "2026-07-06T15:00:00",
  "totalCases": 10,
  "passedCases": 8,
  "passRate": 80.0,
  "avgScore": 0.82,
  "avgLatencyMs": 1234,
  "totalTokens": 8500,
  "categoryStats": {
    "intent-classification": {"total": 4, "passed": 4, "passRate": 100.0},
    "policy-qa": {"total": 4, "passed": 3, "passRate": 75.0},
    "tool-calling": {"total": 2, "passed": 1, "passRate": 50.0}
  },
  "failedCases": [...]
}
```

## 八、数据库设计

```sql
-- V13__add_evaluation_tables.sql

CREATE TABLE lumina_evaluation_dataset (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    name            VARCHAR(200) NOT NULL,
    description     VARCHAR(500),
    agent_type      VARCHAR(50),
    cases_yaml      TEXT NOT NULL,             -- 测试用例 YAML
    case_count      INT NOT NULL DEFAULT 0,
    tenant_id       BIGINT NOT NULL,
    create_by       BIGINT,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted      TINYINT NOT NULL DEFAULT 0,
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评估数据集';

CREATE TABLE lumina_evaluation_run (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    dataset_id      BIGINT NOT NULL,
    dataset_name    VARCHAR(200) NOT NULL,
    agent_id        BIGINT,
    agent_type      VARCHAR(50),
    scoring_method  VARCHAR(30) NOT NULL,
    threshold       DECIMAL(4,2) NOT NULL DEFAULT 0.70,
    total_cases     INT NOT NULL,
    passed_cases    INT NOT NULL,
    pass_rate       DECIMAL(5,2) NOT NULL,
    avg_score       DECIMAL(5,4) NOT NULL,
    avg_latency_ms  BIGINT,
    total_tokens    INT NOT NULL DEFAULT 0,
    results_json    TEXT,                      -- 全部用例结果 JSON
    tenant_id       BIGINT NOT NULL,
    create_by       BIGINT,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_dataset (dataset_id),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评估运行记录';
```

## 九、前端设计

```
views/evaluation/
├── index.vue              # 评估仪表盘（最近运行 + 数据集列表）
├── dataset/
│   ├── index.vue          # 数据集管理（CRUD + YAML 编辑）
│   └── detail.vue         # 数据集详情（用例列表）
├── run/
│   ├── create.vue         # 新建评估（选数据集 + Agent + 评分方法）
│   └── report.vue         # 评估报告（统计卡片 + 分类柱状图 + 失败用例列表）
└── components/
    ├── StatCard.vue        # 统计卡片（通过率/平均分/延迟/Token）
    ├── CategoryChart.vue   # 分类柱状图（ECharts）
    └── CaseResultTable.vue # 用例结果表格
```

### 报告页面布局

```
┌─────────────────────────────────────────────────┐
│ 评估报告 — 客服 Agent 评估集                       │
├──────────┬──────────┬──────────┬─────────────────┤
│ 通过率    │ 平均得分  │ 平均延迟  │ 总 Token        │
│ 80%      │ 0.82     │ 1.2s     │ 8,500          │
├──────────┴──────────┴──────────┴─────────────────┤
│ 分类统计（柱状图）                                 │
│ ██████████ 意图分类   100%                         │
│ ███████░░░ 政策问答    75%                         │
│ █████░░░░░ 工具调用    50%                         │
├─────────────────────────────────────────────────┤
│ 失败用例                                         │
│ [qa-003] Q: 退款政策？ Expected: 7天... Actual:...│
│ [tool-002] Q: 上海天气 Expected: 温度 Actual: ... │
├─────────────────────────────────────────────────┤
│ 历史趋势（折线图）                                │
│ 评估 #1 ──→ 评估 #2 ──→ 评估 #3（当前）            │
│ 通过率: 60%     70%         80%                   │
└─────────────────────────────────────────────────┘
```

## 十、AgentScope 集成要点

### 10.1 执行引擎复用

评估框架**直接调用** `AgentExecutionEngine.executeSync()`，不绕过 AgentScope：

```java
ExecuteResult result = executionEngine.executeSync(
    agentType,      // AgentScope ReActAgent 的 businessType
    testCase.getInput(),
    null,           // AgentConfig（null = 使用 YAML 配置加载的默认 Agent）
    null            // conversationId（null = 无记忆，确保用例独立）
);
```

### 10.2 LLM-as-Judge 模型创建

复用 `ChatModelFactory`，可选择不同的 Judge 模型：

```java
// 配置方式（application.yml）
lumina:
  evaluation:
    judge:
      model-type: deepseek              # Judge 用 DeepSeek
      model-name: deepseek-chat
      api-key: ${DEEPSEEK_API_KEY}
      temperature: 0.0                  # 确定性输出
```

```java
// 代码中使用
AgentConfig.LLMConfig judgeConfig = new AgentConfig.LLMConfig();
judgeConfig.setModelType(judgeModelType);    // "deepseek"
judgeConfig.setModelName(judgeModelName);    // "deepseek-chat"
Model judgeModel = chatModelFactory.create(judgeConfig, defaults, apiKey);
```

### 10.3 Embedding 复用

语义相似度评分器直接使用 `OpenAICompatibleEmbeddingModel`：

```java
// 已有的 Embedding 配置
lumina:
  rag:
    embedding:
      api-key: ${RAG_EMBEDDING_API_KEY}
      base-url: https://api.siliconflow.cn/v1
      model: BAAI/bge-large-zh-v1.5

// 评估框架复用同一个 Embedding 模型
OpenAICompatibleEmbeddingModel embedModel = new OpenAICompatibleEmbeddingModel(
    ragProperties.getEmbedding().getBaseUrl(),
    ragProperties.getEmbedding().getApiKey(),
    ragProperties.getEmbedding().getModel()
);
```

### 10.4 流式评估（可选扩展）

未来可支持流式评估，测量 **首字延迟** 和 **Token 生成速率**：

```java
// 使用 executeStream 测量流式指标
Flux<StreamChunk> stream = executionEngine.executeStream(
    agentType, input, null, null
);

long firstTokenMs = 0;
StringBuilder output = new StringBuilder();

stream.doOnNext(chunk -> {
    if (firstTokenMs == 0 && chunk.content() != null && !chunk.content().isEmpty()) {
        firstTokenMs = System.currentTimeMillis() - start;
    }
    output.append(chunk.content());
}).blockLast();

// 指标：firstTokenMs（首字延迟）、tokensPerSecond（生成速率）
```

## 十一、实施计划

| 阶段 | 工作内容 | 预估 |
|------|---------|------|
| 1 | 核心模型 + 数据库 + 数据集 CRUD | 0.5d |
| 2 | 4 个评分器实现 + 单元测试 | 1d |
| 3 | EvaluationRunner + REST API | 0.5d |
| 4 | 前端评估页面 + ECharts 图表 | 1d |
| 5 | 集成测试 + 文档 | 0.5d |
| **合计** | | **3.5d** |

## 十二、竞品对比

| 能力 | Lumina (方案) | LangSmith | Dify | AutoGen |
|------|--------------|-----------|------|---------|
| 数据集管理 | ✅ YAML/JSON | ✅ | ✅ | ❌ |
| 精确匹配 | ✅ | ✅ | ✅ | ❌ |
| 语义相似度 | ✅ Embedding | ✅ | ❌ | ❌ |
| LLM-as-Judge | ✅ | ✅ | ✅ | ❌ |
| 批量执行 | ✅ | ✅ | ✅ | ✅ |
| 分组统计 | ✅ 按 category | ✅ | ❌ | ❌ |
| 历史趋势 | ✅ 多次对比 | ✅ | ❌ | ❌ |
| Token 统计 | ✅ | ✅ | ✅ | ❌ |
| 延迟统计 | ✅ | ✅ | ❌ | ❌ |
| 开源 | ✅ Apache 2.0 | ❌ 付费 | ✅ | ✅ |
| Java 原生 | ✅ | ❌ Python | ❌ Python | ❌ Python |
