# G02 — Token 计费与成本归集：从 LLM 到账单

> **前置要求**：已完成 [G01 模型价格管理](G01-model-pricing.md)
> **预计阅读**：15 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

价格表有了（G01），但一次 Agent 执行的 Token 消耗怎么追踪？怎么从单次调用的 Token 记录变成租户级的成本报表？

---

## 先建立直觉：话费账单

你的手机每次通话都记录**时长**，月底按**单价 × 时长**算出话费，然后汇总成账单。

Lumina 的 Token 计费一样：
- 每次调用 = 一次通话（记录 promptTokens + completionTokens）
- 月底汇总 = 成本归集（按租户/Agent 聚合总 Token × 单价）

---

## Token 追踪链路

```
Agent 执行
    │
    ▼ LLM 返回 ChatUsage（promptTokens + completionTokens）
    │
    ▼ DefaultAgentExecutionEngine.extractTokenUsage()
    │   → ExecuteResult.TokenUsage
    │
    ▼ AgentServiceImpl.recordSyncTask() / recordAsyncTask()
    │   → 写入 lumina_agent_task 表
    │   （prompt_tokens, completion_tokens, total_tokens, model_name, provider）
    │
    ▼ CostServiceImpl.calculateCost()
    │   → 查 lumina_model_pricing 获取价格
    │   → BigDecimal 精确计算
    │
    ▼ 前端成本仪表盘
        → 按租户/Agent/日期 聚合展示
```

**关键**：Token 不是估算的——是 LLM API 返回的**实际用量**（ChatUsage），确保计费精确。

---

## 成本归集：按租户聚合

```java
// 文件：CostServiceImpl.java（概念示意：真实签名无参，内部从 BaseContext 取 tenantId，返回 Map）
public TenantCostSummary getTenantCostSummary() {
    Long tenantId = BaseContext.getTenantId();
    // 查当前租户所有 COMPLETED 的任务记录
    List<AgentTaskDO> tasks = taskMapper.selectCompletedByTenant(tenantId);

    BigDecimal totalCost = BigDecimal.ZERO;
    Map<Long, BigDecimal> agentCostMap = new HashMap<>();  // Agent → 成本

    for (AgentTaskDO task : tasks) {
        String provider = task.getProvider() != null ? task.getProvider() : "default";
        String model = task.getModelName() != null ? task.getModelName() : "default";

        // 按实际 Token 用量 + 模型价格 计算单次成本
        BigDecimal taskCost = calculateCost(provider, model,
                task.getPromptTokens(), task.getCompletionTokens());

        totalCost = totalCost.add(taskCost);
        agentCostMap.merge(task.getAgentId(), taskCost, BigDecimal::add);
    }

    // 返回：总成本 + TOP10 消费 Agent
    return new TenantCostSummary(totalCost, topNAgents(agentCostMap, 10));
}
```

---

## 日维度成本趋势

```java
// 文件：CostServiceImpl.java:110-140
public Map<String, BigDecimal> getDailyCostTrend(Long tenantId, int days) {
    Map<String, BigDecimal> dailyCost = new TreeMap<>();  // 按日期排序

    for (AgentTaskDO task : tasks) {
        String date = task.getCreateTime().toLocalDate().toString();
        BigDecimal cost = calculateCost(...);
        dailyCost.merge(date, cost, BigDecimal::add);  // 同日累加
    }

    return dailyCost;  // { "2026-07-25": 1.23, "2026-07-26": 0.85, ... }
}
```

前端用这个数据画**折线图**，直观展示每日花费趋势。

---

## Token 持久化字段

```sql
-- lumina_agent_task 表的关键字段
prompt_tokens      INT  -- 输入 Token 数（用户消息 + 历史 + 系统 Prompt）
completion_tokens  INT  -- 输出 Token 数（LLM 生成的回复）
total_tokens       INT  -- 总 Token（= prompt + completion）
model_name         VARCHAR(100)  -- 使用的模型（glm-4-flash / gpt-4 等）
provider           VARCHAR(50)   -- 提供商（zhipu / openai 等）
```

> **为什么存 model_name + provider？** 同一个 Agent 可能因为 A/B 测试或 Failover 用不同模型执行。存下来才能按实际使用的模型计费，而不是按配置的模型。

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| Token 来源 | LLM API 返回的 ChatUsage（实际用量，非估算） |
| 计算公式 | 输入价×输入Token/1000 + 输出价×输出Token/1000 |
| 持久化 | agent_task 表存 Token + model + provider |
| 归集维度 | 租户/Agent/日期 |
| BigDecimal | 6 位精度 + HALF_UP，不用 double |

### 自测题

1. Token 用量是估算的还是实际的？
   <details><summary>答案</summary>实际的。LLM API 返回的 ChatUsage 包含精确的 promptTokens 和 completionTokens，不是 Lumina 自己估算的。</details>

2. 为什么 agent_task 表要存 model_name 和 provider？
   <details><summary>答案</summary>同一个 Agent 可能因 A/B 测试或 Failover 用不同模型执行。存下来才能按实际使用的模型计费，而不是按配置的默认模型。</details>

3. 日维度成本趋势为什么用 TreeMap？
   <details><summary>答案</summary>TreeMap 按键（日期字符串）自动排序，前端拿到的数据天然按时间有序，不需要额外排序。</details>

> 🚀 [G03 — 预算管控 →](G03-budget-control.md)

---

📝 **本篇撰写期间修正的代码**：无。
