# G01 — 模型价格管理：AI 的计价表

> **前置要求**：已完成 [模块 F 输出交互](README.md)
> **预计阅读**：15 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

LLM 按 Token 计费，不同模型价格天差地别：GLM-4-Flash 免费，Claude-3-Opus 输出 $0.75/千 Token。要算成本，先得有**价格表**。这节讲 Lumina 的模型价格管理机制。

---

## 先建立直觉：超市价签

你去超市买东西，每件商品都有**价签**——标明单价。收银台扫描时按价签算总价。

Lumina 的 `lumina_model_pricing` 表就是 AI 模型的"价签"：每个模型的输入/输出单价，Agent 执行后按价签算总花费。

---

## 价格表结构

```sql
-- 文件：lumina-business-base/.../db/migration/V44__add_glm_model_pricing.sql
CREATE TABLE lumina_model_pricing (
    provider         VARCHAR(50),    -- 模型提供商（zhipu/dashscope/anthropic/ollama）
    model_name       VARCHAR(100),   -- 模型名（glm-4-flash / claude-3-opus）
    input_price      DECIMAL(10,4),  -- 输入价格（每千 Token，CNY）
    output_price     DECIMAL(10,4),  -- 输出价格（每千 Token，CNY）
    currency         VARCHAR(10),    -- 币种（CNY/USD）
    is_active        TINYINT         -- 是否启用
);
```

### V44 预置的 18 条价格

| provider | model_name | input_price | output_price | 说明 |
|----------|-----------|-------------|--------------|------|
| zhipu | glm-4 | 0.050 | 0.050 | 标准版 |
| zhipu | **glm-4-flash** | **0.000** | **0.000** | **免费** |
| dashscope | qwen-plus | 0.004 | 0.012 | 通义千问 |
| anthropic | claude-3-opus | 0.150 | 0.750 | 最贵 |
| ollama | llama3 | 0.000 | 0.000 | 本地部署免费 |

> **价格差异**：同样 1000 Token 输出，GLM-4-Flash 免费，Claude-3-Opus 花 ¥0.75——**差了无穷大倍**。

---

## 成本计算公式

```java
// 文件：lumina-business-agent/.../service/impl/CostServiceImpl.java:38-48
public BigDecimal calculateCost(String provider, String modelName,
                                 int promptTokens, int completionTokens) {
    ModelPricingDO pricing = findPricing(provider, modelName);

    // 成本 = (输入Token / 1000) × 输入单价 + (输出Token / 1000) × 输出单价
    BigDecimal inputCost = pricing.getInputPrice()
            .multiply(BigDecimal.valueOf(promptTokens))
            .divide(TOKENS_PER_THOUSAND, 6, RoundingMode.HALF_UP);

    BigDecimal outputCost = pricing.getOutputPrice()
            .multiply(BigDecimal.valueOf(completionTokens))
            .divide(TOKENS_PER_THOUSAND, 6, RoundingMode.HALF_UP);

    return inputCost.add(outputCost);
}
```

**为什么用 BigDecimal？** `double` 有浮点精度问题（0.1 + 0.2 ≠ 0.3）。涉及钱必须用 BigDecimal，6 位小数 + HALF_UP 四舍五入。

---

## 三级价格回退

查不到价格怎么办？三级回退保证不报错：

```java
// 文件：CostServiceImpl.java:170+
private ModelPricingDO findPricing(String provider, String modelName) {
    // 1. 精确匹配：provider + modelName
    ModelPricingDO pricing = mapper.selectOne(provider, modelName);
    if (pricing != null) return pricing;

    // 2. 回退：default provider + default model
    pricing = mapper.selectOne("default", "default");
    if (pricing != null) return pricing;

    // 3. 硬编码兜底：0.002/0.006（比免费贵但不会算 0）
    return new ModelPricingDO("default", "default",
            new BigDecimal("0.002"), new BigDecimal("0.006"), "CNY");
}
```

| 级别 | 查找方式 | 场景 |
|------|---------|------|
| ① 精确 | provider=modelName | 正常情况 |
| ② default | default/default | 新模型未配价格 |
| ③ 硬编码 | 0.002/0.006 | 连 default 都没配 |

> **为什么不用 0 兜底？** 如果成本算成 0，预算管控失效——用户可以无限调用。用一个保守的非零值确保预算仍有意义。

---

## 管理 API

```java
// 文件：ModelPricingController.java（v3.10：已抽 Service 层，Controller 不再直连 Mapper）
GET    /api/v1/model-pricing       // 查全部价格 → 返回 List<ModelPricingVO>
POST   /api/v1/model-pricing       // 创建 → ModelPricingService.create(dto)，返回 ModelPricingVO
PUT    /api/v1/model-pricing/{id}  // 更新 → ModelPricingService.update(id, dto)
DELETE /api/v1/model-pricing/{id}  // 删除 → ModelPricingService.delete(id)
```

> 💡 **v3.10 架构合规**：Controller 现在只调 `ModelPricingService`，业务逻辑（默认值 currency=CNY/isActive=1、时间戳）下沉到 `ModelPricingServiceImpl`，返回 `ModelPricingVO`（非 DO），DO 不出 API 边界。

前端有管理页面，厂商调价后直接在 UI 改，不需要重启服务。

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| 价格表 | provider + model_name → input_price + output_price |
| V44 种子 | 18 条预置价格（含 GLM-4-Flash 免费） |
| 计算公式 | (Token/1000) × 单价，BigDecimal 6 位精度 |
| 三级回退 | 精确 → default → 硬编码（不用 0） |

### 自测题

1. 为什么成本计算用 BigDecimal 而不是 double？
   <details><summary>答案</summary>浮点精度问题（0.1+0.2≠0.3）。涉及钱必须精确计算，BigDecimal 保证精度，6 位小数 HALF_UP 四舍五入。</details>

2. 三级回退为什么最后用硬编码的 0.002 而不是 0？
   <details><summary>答案</summary>如果成本算 0，预算管控失效——用户可以无限免费调用。用保守的非零值确保预算仍有意义。</details>

3. GLM-4-Flash 价格为 0，那用它执行 Agent 成本就是 0 吗？
   <details><summary>答案</summary>对，价格表中 input_price=0 且 output_price=0，calculateCost 返回 BigDecimal.ZERO。但 RAG Embedding 和向量检索等其他环节可能有成本。</details>

> 🚀 [G02 — Token 计费 →](G02-token-billing.md)

---

📝 **本篇撰写期间修正的代码**：无。
