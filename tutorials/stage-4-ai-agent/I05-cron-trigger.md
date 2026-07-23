# I05 — Cron 触发器

> **前置要求**：已完成 [I04-人工审批](I04-human-in-the-loop.md)
> **预计阅读**：10 分钟
> **难度**：⭐⭐☆☆☆

---

## 这节解决什么问题

Agent 不只能手动调——还能**定时自动执行**（如每天早上 8 点生成日报）。这节讲 Cron 触发器。

---

## 怎么用

```yaml
# 创建触发器
POST /api/v1/agents/triggers
{
  "name": "每日报告",
  "agentId": 1,
  "cronExpr": "0 0 8 * * ?",    # 每天早上 8 点
  "task": "生成今日运营报告"
}
```

Cron 表达式 `0 0 8 * * ?` = 秒0 分0 时8 每天 每月 任意周 = 每天早 8 点。

---

## 防重复执行（分布式锁）

> 📖 详见[第一阶 09-Redis 在 Lumina](../stage-1-foundation/09-redis-in-lumina.md)的"分布式锁"章节。

```java
// AgentTriggerServiceImpl.fireWithLock()
// 多实例部署时，Redis 分布式锁保证只触发一次
RLock lock = redissonClient.getLock("lumina:trigger:fire:" + triggerId);
if (!lock.tryLock(0, 300, SECONDS)) return false;  // 没抢到=另一实例在处理
```

---

## 生命周期

```
创建（ACTIVE）→ 暂停（PAUSED）→ 恢复（ACTIVE）→ 删除
                    ↑ 手动暂停      ↑ 手动恢复
```

还可以 `triggerNow` 手动立即触发一次。

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| Cron 触发器 | Agent 定时执行 |
| Cron 表达式 | 秒 分 时 日 月 周 |
| 分布式锁 | tryLock 防多实例重复 |
| 生命周期 | 创建→暂停→恢复→删除 |

---

## 🎉 模块 I 完成

> 🚀 [J01 — Webhook + 企业微信 →](J01-webhook-wechat-bot.md)

---

📝 **本篇撰写期间修正的代码**：无。
