# G03 — 预算管控：防烧钱的最后一道防线

> **前置要求**：已完成 [G02 Token 计费](G02-token-billing.md)
> **预计阅读**：18 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

G01 算出了价格，G02 追踪了 Token。但如果某个 Agent 失控跑了一晚上，可能烧掉几千块。**预算管控**在 Agent 执行前检查"还有没有额度"，超限直接拒绝。

---

## 先建立直觉：信用卡额度

你的信用卡有额度限制：单月消费不超过 10000 元。每次刷卡时银行检查"已用 + 本次 < 额度"。

Lumina 的预算管控一样：每次 Agent 执行前检查"已花费（含在途）+ 预估成本 < 预算上限"。

---

## 预算规则：三维配置

```sql
-- 文件：lumina_business_base/.../db/migration/V12__add_budget_rule.sql
CREATE TABLE lumina_budget_rule (
    rule_name       VARCHAR(100),   -- 规则名（如"月度限制"）
    scope_type      VARCHAR(20),    -- 范围：TENANT / AGENT / USER
    scope_id        BIGINT,         -- 范围 ID（租户 ID / Agent ID / 用户 ID）
    limit_amount    DECIMAL(12,4),  -- 预算上限（CNY）
    period_type     VARCHAR(10),    -- 周期：DAILY / MONTHLY
    alert_threshold INT DEFAULT 80, -- 告警阈值（默认 80%）
    status          TINYINT         -- 1=启用 0=禁用
);
```

### 三种范围 + 两种周期

| 范围 | 场景 | 示例 |
|------|------|------|
| TENANT | 租户总预算 | A 公司每月最多花 ¥10000 |
| AGENT | 单 Agent 预算 | 数据分析 Agent 每月最多 ¥500 |
| USER | 单用户预算 | 张三每天最多 ¥10 |

| 周期 | 重置频率 | 适合 |
|------|---------|------|
| DAILY | 每天归零 | 防突发滥用 |
| MONTHLY | 每月归零 | 月度预算管控 |

---

## 在途追踪：防并发超额

### 问题

```
预算上限：¥100
已花费（COMPLETED）：¥95
剩余：¥5

10 个请求同时到达 → 每个都检查"¥95 < ¥100，通过" → 全部执行
→ 实际花费 ¥95 + 10×¥8 = ¥175 → 超额 ¥75！
```

### 解决方案：把 RUNNING 也算进去

```java
// 文件：lumina-business-agent/.../service/impl/BudgetServiceImpl.java
public BudgetCheckResult checkBudget(String scopeType, Long scopeId, BigDecimal estimatedCost) {
    // 查当前周期内已花费（COMPLETED + RUNNING）
    BigDecimal spent = calculateUsage(scopeType, scopeId);
    //                                    ↑
    //     关键：不只算 COMPLETED，也算 RUNNING 的在途消耗
    //     wrapper.in(AgentTaskDO::getStatus, "COMPLETED", "RUNNING");

    BigDecimal limit = getRuleLimit(scopeType, scopeId);
    BigDecimal remaining = limit.subtract(spent);

    if (remaining.compareTo(estimatedCost) < 0) {
        // 预算不足，拒绝执行
        return BudgetCheckResult.rejected("预算不足", remaining, limit);
    }

    // 告警阈值检查（默认 80%）
    if (spent.divide(limit, 4, RoundingMode.HALF_UP)
            .compareTo(BigDecimal.valueOf(alertThreshold / 100.0)) >= 0) {
        triggerAlert(scopeType, scopeId, spent, limit);
    }

    return BudgetCheckResult.approved(remaining);
}
```

---

## 告警去重：防消息轰炸

预算到了 80% 阈值要告警，但不能每次请求都发一条。用 Redis 去重：

```java
// 文件：BudgetServiceImpl.java
private void triggerAlert(String scopeType, Long scopeId, BigDecimal spent, BigDecimal limit) {
    String date = LocalDate.now().toString();
    String dedupKey = "budget:alert:" + scopeType + ":" + scopeId + ":" + date;

    // Redis SETNX：同一天同一条规则只告警一次
    if (redisCacheManager.setIfAbsent(dedupKey, "1", ttlByPeriod())) {
        // 发送告警通知
        notificationService.sendBudgetAlert(scopeType, scopeId, spent, limit);
    }
}

// TTL 按周期设置：
// DAILY → 25 小时（跨天后自动过期，第二天可再次告警）
// MONTHLY → 31 天
private Duration ttlByPeriod() {
    return periodType.equals("DAILY") ? Duration.ofHours(25) : Duration.ofDays(31);
}
```

| 周期 | 去重 TTL | 效果 |
|------|---------|------|
| DAILY | 25 小时 | 每天最多告警 1 次 |
| MONTHLY | 31 天 | 每月最多告警 1 次 |

---

## 预算检查在执行链中的位置

```
请求进入
    │
    ▼ ① 限流（RateLimiter）：每分钟最多 N 次
    │
    ▼ ② 并发（ConcurrencyLimiter）：同时最多 N 个
    │
    ▼ ③ 预算检查（BudgetService）：还有没有额度？  ← 本节
    │     ├── 通过 → 继续
    │     └── 不足 → 拒绝 + 告警
    │
    ▼ Agent 执行
    │
    ▼ ④ 成本归集（CostService）：记录实际花费
```

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| 预算规则 | 三维（TENANT/AGENT/USER）× 两周期（DAILY/MONTHLY） |
| 在途追踪 | COMPLETED + RUNNING 都算（防并发超额） |
| 告警阈值 | 默认 80%，Redis 去重防轰炸 |
| 告警 TTL | DAILY=25h，MONTHLY=31d（跨周期自动过期） |

### 自测题

1. 为什么预算检查要把 RUNNING 状态的任务也算进去？
   <details><summary>答案</summary>防止并发超额。如果只算 COMPLETED，10 个并发请求都通过了检查（因为还没完成），执行后实际花费远超预算。</details>

2. 告警去重的 TTL 为什么 DAILY 设 25 小时而不是 24 小时？
   <details><summary>答案</summary>25 小时确保跨天（午夜后）key 已过期，第二天可以再次触发告警。如果是 24 小时，在 23:59 触发的告警会在第二天 23:59 才过期——第二天整天都不会告警。</details>

3. 告警阈值默认 80%，为什么不是 100%？
   <details><summary>答案</summary>80% 时告警给运维人员预留 20% 的缓冲时间处理——联系客户加预算或排查异常 Agent。到 100% 再告警就来不及了，Agent 已经被拦截了。</details>

> 🚀 [G04 — 限流与并发 →](G04-rate-limit-concurrency.md)

---

📝 **本篇撰写期间修正的代码**：无。
