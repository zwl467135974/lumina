# I05 — Cron 触发器

> **前置要求**：已完成 [I04-人工审批](I04-human-in-the-loop.md)
> **预计阅读**：12 分钟
> **难度**：⭐⭐☆☆☆

---

## 这节解决什么问题

前面几节讲的工作流都是"用户点一下按钮才跑"。但很多 Agent 任务需要**定时自动执行**：
- 每天早上 8 点生成昨日运营日报，发给管理层
- 每 5 分钟拉一次外部库存数据同步到本地
- 每周一 9 点跑一次数据质量巡检 Agent

Lumina 的 Cron 触发器就是为此而生——给 Agent 挂一个 cron 表达式，剩下交给调度器。这节讲它的工作原理、分布式环境下如何防重复、以及 misfire（漏触发）怎么处理。

---

## 类比：老式机械闹钟 + 值班表

- **cron 表达式** = 闹钟的设定盘（每天 8 点响）
- **`AgentTriggerPoller`** = 值班保安，每 30 秒巡视一次闹钟列表，看哪个该响了
- **Redisson 锁** = 多个保安交接班的"只能一个人按铃"协议——即使部署了 3 个实例，同一个闹钟也只响一次
- **misfire 策略** = 闹钟响了你没醒，是"现在立刻补响"还是"算了跳过去"

---

## 触发链路全景

```
@Scheduled(fixedDelay=30s)  ← Spring 定时任务
    ↓
AgentTriggerPoller.poll()
    ↓
AgentTriggerService.fireDueTriggers()   ← 查 next_fire_at <= now() 的触发器
    ↓
对每个触发器：
    ├─ misfire 判定（超 1 小时未触发？）
    │     ├─ 是 + SKIP 策略 → 只前进 next_fire_at，不执行
    │     └─ 否 / FIRE_ONCE → 继续
    ├─ Redisson 分布式锁 tryLock（防多实例重复）
    ├─ 插入 agent_task 记录（trigger_id 回链）
    └─ agentTaskService.executeTask()   ← 复用完整 Agent 执行管线
          （SSE 进度 / 通知 / 预算 / 限流 / 审计 全自动）
```

---

## 轮询器：每 30 秒扫一次

```java
// 文件：lumina-modules/lumina-business-agent/.../schedule/AgentTriggerPoller.java:22-37
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "lumina.trigger", name = "enabled",
                       havingValue = "true", matchIfMissing = true)
public class AgentTriggerPoller {

    private final AgentTriggerService agentTriggerService;

    @Scheduled(fixedDelayString = "${lumina.trigger.poll-interval-ms:30000}")
    public void poll() {
        try {
            agentTriggerService.fireDueTriggers();
        } catch (Throwable t) {
            log.error("定时触发器扫描异常", t);
        }
    }
}
```

**设计要点**：
- `fixedDelay`（不是 `fixedRate`）—— 上一次执行**结束后**再等 30 秒，避免任务堆积。如果一次扫描耗时 10 秒，下次是 40 秒后开始，不是 30 秒。
- `matchIfMissing = true` —— 配置项没写也默认开启；想关闭某实例的触发器，写 `lumina.trigger.enabled=false`。
- `try { ... } catch (Throwable t)` —— 单次扫描异常不能把整个轮询搞挂，否则后续再也不触发了。

---

## Cron 表达式（Spring 6 字段）

Lumina 用 Spring 的 `CronExpression`，**6 字段**（不是 Unix 的 5 字段）：

```
秒 分 时 日 月 周
0  0  8  *  *  ?    = 每天早 8 点
0  */5 *  *  *  ?    = 每 5 分钟
0  0  9  ?  *  MON   = 每周一 9 点（周字段用 MON，日填 ?）
```

| 字段 | 取值 | 特殊字符 |
|------|------|---------|
| 秒 | 0-59 | `, - * /` |
| 分 | 0-59 | `, - * /` |
| 时 | 0-23 | `, - * /` |
| 日 | 1-31 | `, - * ? /` |
| 月 | 1-12 或 JAN-DEC | `, - * /` |
| 周 | 1-7 或 SUN-SAT | `, - * ? /` |

**坑**：日和周不能同时用 `*`，其中一个必须填 `?`（表示"不限制"）。

```java
// 文件：lumina-modules/lumina-business-agent/.../service/impl/AgentTriggerServiceImpl.java:345-352
private CronExpression parseCron(String cronExpr) {
    try {
        return CronExpression.parse(cronExpr);
    } catch (IllegalArgumentException e) {
        throw new BusinessException(ErrorCode.BAD_REQUEST,
            "cron 表达式非法（需 Spring 6 字段格式：秒 分 时 日 月 周）: " + e.getMessage());
    }
}
```

---

## nextFireAt：下次触发时间

每次触发器被创建、触发成功、或恢复时，都会用 `CronExpression.next(now)` 算出**下一次该触发的时间**，存到 `next_fire_at` 字段：

```java
// 文件：AgentTriggerServiceImpl.java:139（创建时）
trigger.setNextFireAt(cron.next(LocalDateTime.now()));

// 文件：AgentTriggerServiceImpl.java:310-314（触发成功后）
private void onFireSuccess(AgentTriggerDO trigger) {
    LocalDateTime next = nextFireAtSafe(trigger);
    agentTriggerMapper.updateFired(trigger.getId(), STATUS_SUCCESS, next);
}

// 文件：AgentTriggerServiceImpl.java:357-364（容错：表达式损坏返回 null）
private LocalDateTime nextFireAtSafe(AgentTriggerDO trigger) {
    try {
        return CronExpression.parse(trigger.getCronExpr()).next(LocalDateTime.now());
    } catch (IllegalArgumentException e) {
        log.error("cron 表达式损坏: id={}, cron={}", trigger.getId(), trigger.getCronExpr());
        return null;   // 返回 null 后不再调度，避免无限报错
    }
}
```

**数据库查询**就靠这个字段：`SELECT * FROM lumina_agent_trigger WHERE next_fire_at <= NOW() AND enabled = 1`。

---

## 分布式锁：多实例只触发一次

生产环境通常部署多个实例做高可用——如果 3 个实例同时扫到同一个触发器到期，会触发 3 次。Redisson 锁解决这个问题：

```java
// 文件：AgentTriggerServiceImpl.java:233-264
private void fireWithLock(AgentTriggerDO trigger) {
    // ... misfire 判定 ...

    RLock lock = redissonClient.getLock("lumina:trigger:fire:" + trigger.getId());
    boolean acquired;
    try {
        // ① 非阻塞获取：waitTime=0（抢不到立刻返回）
        // ② leaseTime=60s（防实例宕机死锁，60s 后自动释放）
        acquired = lock.tryLock(0, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
    }
    if (!acquired) {
        log.debug("定时触发器 {} 已被其他实例处理，跳过", trigger.getId());
        return;   // 没抢到 = 别的实例在处理，本实例跳过
    }
    try {
        fireInternal(trigger);
    } finally {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
```

**为什么 waitTime=0？** 轮询线程不能阻塞等锁——等下去会拖慢整个扫描周期。抢不到就直接跳过，反正抢到的实例会处理。

**为什么 leaseTime=60s？** 防止实例崩溃后锁永远不释放（Redisson 看门狗续期机制只对带 waitTime 的请求生效，这里用固定过期时间更可控）。

> 📖 想深入了解 Redisson 锁原理？看 [第一阶 09-Redis 在 Lumina](../stage-1-foundation/09-redis-in-lumina.md) 的"分布式锁"章节。

---

## Misfire 策略：漏触发怎么办

服务器宕机 2 小时，恢复后发现有 6 个该触发的任务堆积——补跑还是跳过？由 `misfire_policy` 决定：

| 策略 | 含义 | 适用场景 |
|------|------|---------|
| `FIRE_ONCE`（默认） | 立刻补触发一次 | 日报、巡检——少做一次比丢数据严重 |
| `SKIP` | 跳过本次，前进到下个时间点 | 同步任务——补一次可能造成数据重复 |

```java
// 文件：AgentTriggerServiceImpl.java:60-81
private static final String MISFIRE_FIRE_ONCE = "FIRE_ONCE";
private static final String MISFIRE_SKIP = "SKIP";
private static final long LOCK_LEASE_SECONDS = 60;
private static final Duration MISFIRE_THRESHOLD = Duration.ofHours(1);
// 超过该阈值未触发视为 misfire → 按 misfire_policy 处理

// 文件：AgentTriggerServiceImpl.java:233-242
private void fireWithLock(AgentTriggerDO trigger) {
    if (isMisfired(trigger) && MISFIRE_SKIP.equals(trigger.getMisfirePolicy())) {
        // 跳过：只前进 next_fire_at，不执行
        LocalDateTime next = parseCron(trigger.getCronExpr()).next(LocalDateTime.now());
        agentTriggerMapper.updateNextFireAt(trigger.getId(), next);
        recordMetric("skipped_misfire");
        return;
    }
    // FIRE_ONCE 或未超阈值：正常触发
    // ...
}

private boolean isMisfired(AgentTriggerDO trigger) {
    return trigger.getNextFireAt() != null
        && trigger.getNextFireAt().isBefore(LocalDateTime.now().minus(MISFIRE_THRESHOLD));
}
```

**判定逻辑**：`next_fire_at` 比当前时间早超过 1 小时 = misfire。例如设定 8 点触发，10 点才恢复服务 → 超过 1 小时阈值。

---

## 自动禁用 + 告警

触发器连续失败 5 次会自动禁用，并发通知：

```java
// 文件：AgentTriggerServiceImpl.java:317-338
private void onFireFailure(AgentTriggerDO trigger, String errorMessage) {
    int failCount = (trigger.getFailCount() != null ? trigger.getFailCount() : 0) + 1;
    boolean disable = failCount >= MAX_FAIL_COUNT;   // MAX_FAIL_COUNT = 5
    // ...
    agentTriggerMapper.updateFireFailed(trigger.getId(), failCount, error, next,
                                        disable ? 0 : 1);
    if (disable) {
        notificationEventPublisher.publish(new NotificationEvent(
            trigger.getCreateBy(), "TRIGGER",
            "定时触发器已禁用: " + trigger.getName(),
            "连续失败 " + failCount + " 次已自动禁用，最近错误: " + error,
            "ERROR", "agent_trigger", String.valueOf(trigger.getId()),
            trigger.getTenantId()));
    }
}
```

**为什么自动禁用？** 故障的触发器如果不禁用，每 30 秒都会失败一次，刷屏日志、占用资源、可能把下游 Agent 拖垮。自动禁用 + 通知 = 让人介入修。

---

## 生命周期 & API

```
创建（ACTIVE, next_fire_at=now+下一次cron） 
    ↓
触发成功 → next_fire_at 前进
触发失败 → failCount++（达到 5 次自动 DISABLED）
    ↓
手动 pause(id)  → enabled=0，停止触发
手动 resume(id) → enabled=1，从 now 重算 next_fire_at（不补历史积压）
    ↓
triggerNow(id)  → 立即触发一次，不走锁，不更新 next_fire_at
    ↓
delete(id)      → 软删除
```

**resume 的细节**：恢复时 `next_fire_at = cron.next(now)`，**从当前时间往后算**，不补之前暂停期间漏掉的触发。这避免了暂停一周后恢复瞬间触发 7 次日报的尴尬。

```java
// 文件：AgentTriggerServiceImpl.java:190-201
public void resume(Long id) {
    AgentTriggerDO trigger = getOwnedTrigger(id);
    CronExpression cron = parseCron(trigger.getCronExpr());
    trigger.setEnabled(1);
    // 从当前时间重算，避免恢复后立即补触发历史积压
    trigger.setNextFireAt(cron.next(LocalDateTime.now()));
    trigger.setFailCount(0);
    agentTriggerMapper.updateById(trigger);
}
```

---

## 真实场景示例

```bash
# 1. 每日早报：每天 8 点
POST /api/v1/agents/triggers
{
  "name": "每日运营早报",
  "agentId": 1,
  "cronExpr": "0 0 8 * * ?",
  "inputText": "生成昨日运营报告并推送到管理层群",
  "misfirePolicy": "FIRE_ONCE"
}

# 2. 库存同步：每 5 分钟
POST /api/v1/agents/triggers
{
  "name": "外部库存同步",
  "agentId": 2,
  "cronExpr": "0 */5 * * * ?",
  "inputText": "拉取外部库存增量数据",
  "misfirePolicy": "SKIP"
}

# 3. 周报：每周一 9 点
POST /api/v1/agents/triggers
{
  "name": "周度数据巡检",
  "agentId": 3,
  "cronExpr": "0 0 9 ? * MON",
  "inputText": "执行本周数据质量巡检"
}

# 手动立即触发一次（测试用）
POST /api/v1/agents/triggers/{id}/trigger-now
```

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| Cron 触发器 | 给 Agent 挂定时任务 |
| `AgentTriggerPoller` | `@Scheduled(fixedDelay=30s)` 轮询 |
| Cron 表达式 | Spring 6 字段：秒 分 时 日 月 周 |
| `next_fire_at` | 触发器到期判断字段 |
| Redisson 锁 | `tryLock(0, 60s)` 多实例只触发一次 |
| Misfire 策略 | `FIRE_ONCE`（补触发）/ `SKIP`（跳过） |
| 自动禁用 | 连续失败 5 次禁用 + 发通知 |
| `triggerNow` | 手动立即触发，不走锁 |

---

## 🎉 模块 I 完成

> 🚀 [J01 — Webhook + 企业微信 →](J01-webhook-wechat-bot.md)

### 自测题

1. 为什么用 `fixedDelay` 而不是 `fixedRate`？
   <details><summary>答案</summary>`fixedDelay` 是上一次执行<strong>结束后</strong>再等指定时间，避免任务堆积；`fixedRate` 是固定间隔触发，如果一次扫描耗时超过间隔会重叠或堆积。轮询场景下 fixedDelay 更安全——宁可慢一点也不要并发扫描。</details>

2. 多实例部署时，同一个触发器会被触发多次吗？
   <details><summary>答案</summary>不会。每个触发器触发前先抢 Redisson 锁 `lumina:trigger:fire:{triggerId}`，`tryLock(0, 60s)` 非阻塞——只有第一个抢到锁的实例执行触发，其他实例抢不到直接跳过。锁 60 秒自动释放防止宕机死锁。</details>

3. `FIRE_ONCE` 和 `SKIP` 两种 misfire 策略分别适合什么场景？
   <details><summary>答案</summary><code>FIRE_ONCE</code>（默认）：服务器恢复后立刻补触发一次，适合"少做一次比丢数据严重"的场景，如日报、巡检。<code>SKIP</code>：跳过漏掉的次数，前进到下一个时间点，适合"补一次可能造成数据重复"的场景，如增量数据同步。判定阈值是 1 小时——超过 1 小时未触发才算 misfire。</details>

4. 触发器连续失败会怎样？
   <details><summary>答案</summary>每次失败 <code>failCount++</code>，达到 5 次（<code>MAX_FAIL_COUNT</code>）自动设 enabled=0 禁用，并通过 <code>NotificationEventPublisher</code> 发 ERROR 级通知给创建者。禁用后不再触发，避免刷屏日志和拖垮下游。恢复时手动调用 <code>resume()</code>，会重置 failCount=0。</details>

---

📝 **本篇撰写期间修正的代码**：无。
