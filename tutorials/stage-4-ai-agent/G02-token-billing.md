# G02 — Token 计费与成本归集

> **前置要求**：已完成 [G01-模型价格](G01-model-pricing.md)
> **预计阅读**：10 分钟
> **难度**：⭐⭐☆☆☆

---

## 这节解决什么问题

价格有了，怎么算单次调用成本？怎么按租户/Agent 归集总成本？

---

## 成本计算公式

```java
// 文件：CostServiceImpl.java:38-49
public BigDecimal calculateCost(String provider, String modelName,
                                int promptTokens, int completionTokens) {
    ModelPricingDO pricing = findPricing(provider, modelName);

    BigDecimal inputCost = pricing.getInputPrice()
        .multiply(BigDecimal.valueOf(promptTokens))
        .divide(TOKENS_PER_THOUSAND, 6, RoundingMode.HALF_UP);    // 输入价 × 输入Token/1000

    BigDecimal outputCost = pricing.getOutputPrice()
        .multiply(BigDecimal.valueOf(completionTokens))
        .divide(TOKENS_PER_THOUSAND, 6, RoundingMode.HALF_UP);    // 输出价 × 输出Token/1000

    return inputCost.add(outputCost);
}
```

**一行话**：`成本 = 输入价 × 输入Token/1000 + 输出价 × 输出Token/1000`

---

## 成本归集

```java
// getTenantCostSummary()
// 查当前租户所有 COMPLETED 的 task
// 按 agentId 聚合成本
// 返回: 总Token / 总成本 / TOP10 消费 Agent
```

前端成本仪表盘展示这些数据。

---

## Token 追踪链路

```
LLM 返回 → ChatUsage（promptTokens/completionTokens）
  ↓ extractTokenUsage()
ExecuteResult.TokenUsage
  ↓ recordSyncTask()
lumina_agent_task 表（prompt_tokens / completion_tokens / total_tokens / model_name / provider）
  ↓ calculateCost()
lumina_model_pricing 查价格 → 计算成本
```

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| 公式 | 输入价×输入Token/1000 + 输出价×输出Token/1000 |
| 归集 | 按 tenantId 聚合，TOP10 Agent |
| 持久化 | agent_task 表存 Token + model + provider |

> 🚀 [G03 — 预算管控 →](G03-budget-control.md)

---

📝 **本篇撰写期间修正的代码**：无。
