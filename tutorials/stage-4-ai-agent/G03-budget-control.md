# G03 — 预算管控

> **前置要求**：已完成 [G02-Token 计费](G02-token-billing.md)
> **预计阅读**：10 分钟
> **难度**：⭐⭐☆☆☆

---

## 这节解决什么问题

LLM 调用花钱。怎么防止"某个租户/Agent 烧爆预算"？Lumina 的预算管控：设规则、查在途、超限拦截。

---

## 预算规则

```sql
-- lumina_budget_rule 表
| rule_name | scope | limit_amount | period |
|-----------|-------|-------------|--------|
| 月度限制  | 租户  | 1000.00     | 月     |
| Agent限制 | Agent | 100.00      | 月     |
```

---

## 在途追踪（v3.6 改进）

```java
// BudgetServiceImpl.calculateUsage()
// 不只算 COMPLETED，也算 RUNNING 的（防并发超额）
wrapper.eq(AgentTaskDO::getStatus, "COMPLETED");
// → 改成
wrapper.in(AgentTaskDO::getStatus, "COMPLETED", "RUNNING");
```

**为什么**：如果只算已完成的，10 个并发请求都通过了预算检查（因为都还没完成），集体执行后实际超额了。

---

## 告警去重

```java
// Redis 去重：budget:alert:{ruleId}:{date}
// 同一条规则同一天只告警一次（防止轰炸）
```

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| 预算规则 | 租户/Agent 级别限额 |
| 在途追踪 | RUNNING 也算（防并发超额） |
| 告警去重 | Redis 按规则+日期去重 |

> 🚀 [G04 — 限流与并发 →](G04-rate-limit-concurrency.md)

---

📝 **本篇撰写期间修正的代码**：无。
