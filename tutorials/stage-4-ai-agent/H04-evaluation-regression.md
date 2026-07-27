# H04 — 评估回归

> **前置要求**：已完成 [H03-A/B Testing](H03-ab-testing.md)
> **预计阅读**：15 分钟
> **难度**：⭐⭐☆☆☆

---

## 这节解决什么问题

你优化了 Prompt（让回答更简洁），上线后发现：简洁是简洁了，但原来能正确回答的"多步推理"题现在答错了。这种**改了 A 却弄坏了 B**的现象叫**回归（Regression）**。

光靠肉眼"看几个例子觉得没问题"是不够的——你需要一套机制：先把当前版本的效果**快照**下来（基线），改完后再跑一遍同样的测试集，**逐题对比**哪些题从"通过"变成了"失败"。这就是评估回归。本节拆解 Lumina 的实现：基线标记、批量回归、回归判定、告警触发。

---

## 类比：学生换学习方法后的期末重考

把 Prompt/模型比作一个学生的学习方法，把评估数据集比作期末考试卷。

- **基线（Baseline）**：学生用旧方法考了一次期末卷，得了 85 分，其中第 3、7、12 题答对了。这次成绩就是基线快照。
- **回归测试**：学生换了新学习方法（改 Prompt），用**同一张卷子**重考。如果这次第 3、7 题变成答错了——这就是回归：新方法弄坏了原来会的题。
- **回归阈值**：允许偶尔波动（比如最多退步 2 题），超过阈值才告警——避免因为 1 道题的边界 case 反复报警。
- **CI 门禁**：考试退步超过阈值 → 不允许毕业（阻断上线）。

关键原则：**基线和回归必须用同一张卷子（同一数据集）**，否则分数没有可比性。

---

## 一、回归规则实体

每个数据集可配一条回归规则（`EvaluationRegressionRuleDO`，`lumina-business-agent/.../infrastructure/entity/EvaluationRegressionRuleDO.java`）：

```java
@TableName("lumina_evaluation_regression_rule")
public class EvaluationRegressionRuleDO {
    private Long id;
    private String name;            // 规则名称
    private Long datasetId;         // 关联的数据集（"哪张卷子"）
    private Long baselineRunId;     // 基线 run ID（"上次考试成绩"）
    private Integer maxRegressed;   // 允许的最大回归用例数（"容忍退步几道题"）
    private String alertWebhook;    // 告警 webhook URL（超阈值时通知）
    private Integer enabled;        // 是否启用
    private Long tenantId;
}
```

一个规则绑定一个数据集，指定了：拿哪个 run 当基线、最多容忍退步几道题、退步超限往哪发告警。

---

## 二、标记基线

基线不是凭空指定的——它是某次真实评估 run 的结果。`markBaseline`（`EvaluationServiceImpl.java` 第 595-614 行）把指定的 run 标记为基线：

```java
@Transactional(rollbackFor = Exception.class)
public void markBaseline(Long runId) {
    EvaluationRunDO run = runMapper.selectById(runId);
    // 清除同数据集的其他基线标记（一个数据集只能有一个基线）
    LambdaQueryWrapper<EvaluationRunDO> clearWrapper = new LambdaQueryWrapper<>();
    clearWrapper.eq(EvaluationRunDO::getDatasetId, run.getDatasetId())
                .eq(EvaluationRunDO::getIsBaseline, 1);
    EvaluationRunDO clearUpdate = new EvaluationRunDO();
    clearUpdate.setIsBaseline(0);
    runMapper.update(clearUpdate, clearWrapper);

    // 标记当前 run 为基线
    run.setIsBaseline(1);
    runMapper.updateById(run);
}
```

关键点：**同一数据集只能有一个基线**。标记新基线前先清除旧基线（`is_baseline=0`），再设新的（`is_baseline=1`）。这保证了对比基准的唯一性——否则系统不知道该拿哪个 run 当参照。

什么时候标记基线？在当前版本（Prompt/模型）效果稳定且准备发版前，跑一次评估，把这次 run 标为基线。之后任何改动都用它当参照。

---

## 三、批量回归测试

`runBatchRegression`（第 617-699 行）是回归测试的主入口，支持一次跑多个数据集：

```java
public Map<String, Object> runBatchRegression(BatchRegressionDTO dto) {
    for (Long datasetId : dto.getDatasetIds()) {
        // 1. 对每个数据集执行一次评估（用新的 Prompt/模型）
        RunReport report = runEvaluation(datasetId, runDto);

        // 2. 查回归规则，确定基线
        EvaluationRegressionRuleDO rule = regressionRuleMapper.selectOne(...);
        Long baselineRunId = dto.getBaselineRunId() != null ? dto.getBaselineRunId()
                : (rule != null ? rule.getBaselineRunId() : null);

        // 3. 与基线对比，算回归用例数
        if (baselineRunId != null) {
            int regressed = compareRegressed(baseline, latestRun);
            dsResult.put("regressed", regressed);
            // 4. 回归超阈值 → 触发告警
            triggerRegressionAlert(rule, datasetId, latestRun, regressed);
        }
    }
    report.put("pass", totalRegressed == 0);   // 全部数据集 0 回归才算 pass
    return report;
}
```

基线来源有两级回退：DTO 显式指定的 `baselineRunId` 优先，没指定就用规则里配的 `baselineRunId`。这样既能临时指定基线跑一次性回归，也能靠规则配置实现 CI 自动化回归。

### `BatchRegressionDTO`

```java
public class BatchRegressionDTO {
    private List<Long> datasetIds;     // 要回归的数据集列表
    private Long agentId;              // 被测 Agent
    private ScoringMethod scoringMethod;
    private Double threshold;
    private String promptName;         // 被测 Prompt 名称
    private Integer promptVersion;     // 被测 Prompt 版本
    private Long baselineRunId;        // 基线 run ID（可选，自动对比用）
}
```

---

## 四、回归判定逻辑

`compareRegressed`（第 738-749 行）是简化的判定：**passRate 下降超过 5 个百分点就算回归**：

```java
private int compareRegressed(EvaluationRunDO baseline, EvaluationRunDO current) {
    if (baseline.getPassRate() != null && current.getPassRate() != null) {
        double diff = baseline.getPassRate().doubleValue() - current.getPassRate().doubleValue();
        if (diff > 5.0) {   // passRate 下降超过 5%
            int baselinePassed = baseline.getPassedCases();
            int currentPassed = current.getPassedCases();
            return Math.max(0, baselinePassed - currentPassed);   // 回归用例数
        }
    }
    return 0;
}
```

为什么是"简化版"？因为它基于 `passRate` 整体变化判定，而不是**逐题对比**（case 级 diff）。理想情况下应该对比"每个 case 在基线和当前 run 的通过状态"，统计从通过变失败的 case 数。当前实现用 passRate 差值近似——passRate 下降超过 5% 时，用 `baselinePassed - currentPassed` 估算回归用例数。这是个工程权衡：逐题对比需要 case 级结果持久化（开销大），passRate 近似成本低且够用。

---

## 五、告警触发与 CI 门禁

回归用例数超过规则的 `maxRegressed` 阈值时，`triggerRegressionAlert`（第 705-733 行）同时走两条告警通道：

```java
private void triggerRegressionAlert(EvaluationRegressionRuleDO rule, Long datasetId,
                                    EvaluationRunDO run, int regressed) {
    int maxRegressed = rule.getMaxRegressed() != null ? rule.getMaxRegressed() : 0;
    if (regressed <= maxRegressed) {
        return;   // 未超阈值，不告警
    }
    NotificationEvent event = new NotificationEvent(
            BaseContext.getUserId(), "EVALUATION",
            "评估回归告警: " + rule.getName(),
            String.format("数据集 %d 回归用例数 %d 超过阈值 %d（run=%d）",
                    datasetId, regressed, maxRegressed, run.getId()),
            "WARN", "evaluation_run", String.valueOf(run.getId()), currentTenantId());
    // 通道1：直发规则配置的 alert_webhook（复用 WebhookSender 统一出口）
    if (webhookSender != null && StringUtils.hasText(rule.getAlertWebhook())) {
        webhookSender.sendToUrl(rule.getAlertWebhook(), null, event);
    }
    // 通道2：站内通知（SSE 推送 + 持久化）
    notificationEventPublisher.publish(event);
}
```

两个细节：
- **直连 webhook**：`sendToUrl` 是 `WebhookSender` 的"不更新 DB 状态"版本，专门给评估回归这种**直连场景**复用（不像订阅式 webhook 需要管理失败计数和熔断）。
- **告警失败不阻断**：两个通道都 try-catch 包裹，失败只 warn——回归告警本身不能再制造新的故障。

### CI 门禁

`runBatchRegression` 返回的 `report` 里有 `pass` 字段（`totalRegressed == 0`）。CI 流水线调用批量回归接口后，检查 `pass`：
- `pass=true` → 全部数据集零回归 → 允许上线。
- `pass=false` → 有回归 → 阻断上线，人工排查。

这就是用评估回归做 **CI 门禁**的标准模式：每次 Prompt/模型变更的 PR 都自动跑回归，回归就拦住。

---

## 六、Prompt 版本对比

除了跑回归，还可以**直接对比两个 Prompt 版本的内容差异**（`comparePromptVersions`，第 751 行起）：

```
GET /evaluations/prompts/compare?name=customer-service&vA=1&vB=2
```

返回行级 diff，让你直观看到"新版本到底改了哪些行"。这在排查回归原因时很有用——先看 diff 知道改了什么，再看回归报告知道影响了哪些题，两者结合定位根因。

---

## 小结

| 概念 | 一句话记忆 | 代码位置 |
|------|-----------|---------|
| 回归规则 | 数据集 + 基线 + 阈值 + 告警 webhook | `EvaluationRegressionRuleDO` |
| 标记基线 | 一个数据集只能有一个基线，先清旧再标新 | `markBaseline` 第 595-614 行 |
| 批量回归 | 一次跑多个数据集，返回 `pass` 字段 | `runBatchRegression` 第 617-699 行 |
| 回归判定 | passRate 下降 >5% 算回归（简化版） | `compareRegressed` 第 738-749 行 |
| 告警触发 | 超阈值 → 直发 webhook + 站内通知 | `triggerRegressionAlert` 第 705-733 行 |
| CI 门禁 | `pass=false` 阻断上线 | 调用方检查 `report.pass` |
| Prompt 版本对比 | 行级 diff，定位回归根因 | `comparePromptVersions` 第 751 行起 |

**真实场景**：你把客服 Prompt 从 v1 升到 v2（加了"回答要简洁"）。CI 自动触发批量回归 → 发现"多步推理"数据集 passRate 从 90% 掉到 82%（回归 8 题，超过阈值 3）→ 触发企微告警 + CI 阻断 → 你查看 Prompt diff 发现 v2 删了"请逐步推理"那行 → 补回来重跑，回归消失，允许上线。

---

## 自测

<details>
<summary><b>1. 为什么标记基线时要"先清除同数据集的其他基线标记"？一个数据集有两个基线会怎样？</b></summary>

因为回归对比需要一个**唯一参照**。如果同一数据集有两个基线（比如 run A 和 run B 都标了 `is_baseline=1`），`runBatchRegression` 查基线时不知道该拿哪个当参照（`selectOne` 会报错或随机取一个），对比结果就不可靠。先清旧再标新保证唯一性。从语义上，基线代表"当前稳定版本的效果快照"，同一时刻只能有一个稳定版本，所以基线必须唯一。
</details>

<details>
<summary><b>2. <code>compareRegressed</code> 用 passRate 差值判定回归，这种"简化版"有什么局限？理想的逐题对比怎么做？</b></summary>

**局限**：passRate 是整体指标，它下降 5% 可能是"少数题大幅退步 + 多数题不变"，也可能是"很多题各退步一点"——无法区分。而且 `baselinePassed - currentPassed` 只算出"少通过了几题"，不知道**具体是哪几题**退步了（无法定位根因）。另外，passRate 可能因为新版本"多做对了几道新题"而持平，掩盖了部分题的退步。

**理想的逐题对比**：需要持久化每个 case 在每次 run 的通过状态（case 级结果表，含 `run_id + case_id + passed`）。对比时 join 基线 run 和当前 run 的 case 级结果，找出 `baseline.passed=true AND current.passed=false` 的 case——这些就是精确的回归 case。Lumina 当前用 passRate 近似是工程权衡（case 级持久化成本高），未来可扩展。
</details>

<details>
<summary><b>3. 回归告警的 <code>sendToUrl</code> 和普通 Webhook 订阅的 <code>send</code> 有什么区别？为什么评估回归要用 <code>sendToUrl</code>？</b></summary>

`send`（普通订阅）：会更新 webhook 的 DB 状态（失败计数、自动禁用），因为它面向"用户订阅的长期 webhook"，需要管理健康状态和熔断。

`sendToUrl`（直连）：**只发 HTTP 请求，不更新 DB 状态**，不管理失败计数和熔断。它面向"一次性直连场景"——评估回归规则配置的 `alertWebhook` 是临时的、不需要订阅管理的一个 URL。

评估回归用 `sendToUrl` 的原因：回归告警的 webhook 不是"用户订阅的长期出口"，而是"规则配置的一次性告警地址"，不需要（也不应该）因为告警发送失败而触发熔断禁用——否则告警地址被禁用后，后续的回归就再也告警不出来了。
</details>

<details>
<summary><b>4. <code>runBatchRegression</code> 返回的 <code>report.put("pass", totalRegressed == 0)</code>，这个 <code>pass</code> 字段在 CI 流水线里怎么用？如果只有 1 个数据集回归了 1 题，<code>pass</code> 是什么？</b></summary>

`pass` 是 CI 门禁的布尔判断：**所有数据集的回归用例数总和为 0 才算 pass**。CI 流水线调用 `runBatchRegression` 后检查 `report.pass`：true 允许上线，false 阻断上线。

如果只有 1 个数据集回归了 1 题，`totalRegressed = 1`，`pass = (1 == 0) = false`——会阻断上线。这看起来严格，但这是"零回归"策略：任何回归都意味着"有原来能做对的题现在做错了"，是真实的质量退化，应该先排查再上线。如果想容忍少量回归，可以用规则的 `maxRegressed` 字段控制**是否告警**（不阻断），但 `pass` 字段本身是严格的零回归判断，CI 层面用它做硬门禁。
</details>

---

## 🎉 模块 H 完成

> 🚀 [I01 — 工作流编排 →](I01-workflow-orchestration.md)

---

📝 **本篇撰写期间修正的代码**：无。
