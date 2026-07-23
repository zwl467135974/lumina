# G01 — 模型价格管理

> **前置要求**：已完成 [模块 F](README.md)
> **预计阅读**：10 分钟
> **难度**：⭐⭐☆☆☆

---

## 这节解决什么问题

成本计算需要模型价格（输入/输出每千 Token 多少钱）。Flyway V44 预置了 18 条模型价格。

---

## 价格表

```sql
-- lumina_model_pricing 表（V44 迁移灌入）
| provider | model_name       | input_price | output_price | currency |
|----------|------------------|-------------|--------------|----------|
| zhipu    | glm-4            | 0.050       | 0.050        | CNY      |
| zhipu    | glm-4-flash      | 0.000       | 0.000        | CNY      |  ← 免费
| dashscope| qwen-plus        | 0.004       | 0.012        | CNY      |
| anthropic| claude-3-opus    | 0.150       | 0.750        | CNY      |
| ollama   | llama3           | 0.000       | 0.000        | CNY      |  ← 本地免费
```

---

## 管理 API

```java
// ModelPricingController.java
GET    /api/v1/model-pricing       // 查全部
POST   /api/v1/model-pricing       // 创建
PUT    /api/v1/model-pricing/{id}  // 更新
DELETE /api/v1/model-pricing/{id}  // 删除
```

前端有管理页面，可以直接改价格。

---

## 价格回退逻辑

```java
// CostServiceImpl.findPricing()
// 1. 先按 provider + modelName 精确查
// 2. 查不到 → 回退到 default/default
// 3. 还查不到 → 用硬编码默认值（0.002/0.006）
```

**三级回退**保证即使没配价格也不会报错。

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| 模型价格表 | provider + model → 输入价 + 输出价 |
| V44 种子 | 18 条预置价格（GLM/DashScope/Claude/Ollama） |
| 三级回退 | 精确 → default → 硬编码 |

> 🚀 [G02 — Token 计费 →](G02-token-billing.md)

---

📝 **本篇撰写期间修正的代码**：无。
