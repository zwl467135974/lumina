# J01 — Webhook 与企业微信机器人

> **前置要求**：已完成 [模块 I](README.md)
> **预计阅读**：15 分钟
> **难度**：⭐⭐☆☆☆

---

## 这节解决什么问题

Lumina 产生的通知（预算告警、任务完成、工作流审批、评估回归告警）怎么推到外部系统？用户不会一直盯着 Lumina 的 SSE 推送看——我们需要把消息主动送到他们常用的地方：自定义 HTTP 端点（**Webhook**）或**企业微信群机器人**。

本节拆解这两条出口的实现：签名怎么防伪造、失败怎么自动重试与熔断、跨线程异步怎么不丢租户上下文、企微的 markdown 怎么着色和分片。

---

## 类比：快递上门 + 防伪印章

把通知系统想象成一家快递公司：

- **Webhook URL = 收件地址**。你在 Lumina 注册一个 URL，有事件时 Lumina 就往这个地址 POST 一个包裹（JSON）。
- **HMAC-SHA256 签名 = 防伪印章**。收件人怎么知道包裹是 Lumina 寄的、不是骗子伪造的？每个包裹上盖一个用双方共享密钥生成的印章（`X-Lumina-Signature` 头），收件人用同一把密钥验印。
- **3 次重试 + 指数退避 = 快递员多跑几趟**。第一天送一次没人收，等 1 秒再送，再等 2 秒送第三次。
- **连续失败 5 次自动禁用 = 把"常年不在家"的地址拉黑**。避免一个挂掉的 URL 永远占用发送资源。

---

## 一、Webhook 分发链路全景

```
NotificationEvent 产生
        ↓
WebhookDispatcher.dispatch(event)      ← @Async("webhookExecutor")
        ↓ 手动传播 BaseContext（租户/用户）
        ↓ 查询该用户订阅了哪些 webhook
        ↓ 按 channel 路由
        ├── WEBHOOK  → WebhookSender.send()      ← HMAC + 重试 + 熔断
        ├── WE_COM   → WeComSender.send()        ← markdown + 分片 + 限频
        ├── DINGTALK → DingTalkSender.send()
        ├── FEISHU   → FeishuSender.send()
        └── TELEGRAM → TelegramSender.send()
```

入口是 `WebhookDispatcher`（`lumina-business-notification/.../service/WebhookDispatcher.java`），它按 `NotificationChannel` 枚举把事件路由到对应 Sender。每个 Sender 独立处理自己渠道的协议。

---

## 二、HMAC-SHA256 签名：防伪造

### 为什么需要签名

没有签名的话，任何人只要猜到你的 Webhook URL，就能往你的系统 POST 假消息（比如伪造一条"任务完成"通知诱导下游动作）。签名保证了：**只有持有 secret 的人（Lumina）才能产生合法的 `X-Lumina-Signature` 头**。

### 实现代码

`WebhookSender.java` 第 145-158 行：

```java
private String hmacSha256(String payload, String secret) {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
    StringBuilder hex = new StringBuilder(digest.length * 2);
    for (byte b : digest) {
        hex.append(String.format("%02x", b));   // 十六进制小写
    }
    return hex.toString();
}
```

发送时（第 103-105 行）：

```java
if (secret != null && !secret.isBlank()) {
    builder.header("X-Lumina-Signature", "sha256=" + hmacSha256(payload, secret));
}
```

接收方验签的等价逻辑：用同样的 secret 对收到的 body 做 HMAC-SHA256，把结果和 `X-Lumina-Signature` 头（去掉 `sha256=` 前缀）做**恒等比较**（防时序攻击要用 `MessageDigest.isEqual`，不要用 `equals`）。

---

## 三、3 次重试 + 指数退避

网络抖动、接收方短暂重启是常态，一次失败就放弃太脆弱。`WebhookSender` 的策略（第 95-123 行）：

```java
for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {   // MAX_ATTEMPTS = 3
    try {
        // ... 发送 HTTP 请求 ...
        if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
            return true;
        }
        throw new IOException("HTTP " + resp.statusCode());
    } catch (Exception e) {
        log.warn("Webhook 发送失败（第 {}/{} 次）: ...", attempt, MAX_ATTEMPTS, ...);
        if (attempt < MAX_ATTEMPTS) {
            sleep((long) (1000 * Math.pow(2, attempt - 1)));   // 1s → 2s → (第3次不睡)
        }
    }
}
return false;
```

退避序列：第 1 次失败后等 **1 秒**，第 2 次失败后等 **2 秒**，第 3 次失败就放弃。指数退避（而非固定间隔）能在接收方过载时给足恢复时间，又不会无限等。

---

## 四、连续失败自动禁用（熔断）

如果某个 URL 长期不可达（比如服务下线、域名过期），无脑重试只会浪费线程池资源。`WebhookSender` 实现了自动熔断（第 130-140 行）：

```java
private static final int MAX_FAIL_COUNT = 5;

private void recordFailure(WebhookDO webhook, String error) {
    int newFailCount = (webhook.getFailCount() != null ? webhook.getFailCount() : 0) + 1;
    boolean autoDisable = newFailCount >= MAX_FAIL_COUNT;
    webhookMapper.updateStatus(webhook.getId(), "FAILED",
            truncate(error, MAX_ERROR_LENGTH),
            autoDisable ? 0 : newFailCount,        // 禁用时计数清零
            autoDisable ? 0 : null);               // status 字段
    if (autoDisable) {
        log.warn("Webhook [{}] 连续失败 {} 次已自动禁用", webhook.getId(), MAX_FAIL_COUNT);
    }
}
```

关键细节：
- **失败计数是"连续"的**——只要中间有一次成功（`send` 方法第 67-69 行），`failCount` 立即重置为 0。
- 禁用后该 webhook 不再被 `findEnabledForEvent` 查出来，彻底停止推送。
- 管理员需要在后台手动重新启用（或修复 URL 后重新启用）。

---

## 五、异步分发与上下文传播

### 为什么要异步

通知主链路（持久化 + SSE 推送）必须快。Webhook 外发是 IO 密集型操作，单条最长可能 13 秒（3 次重试 × 超时 + 退避），绝不能阻塞主链。所以 `dispatch` 方法标注了 `@Async("webhookExecutor")`。

### 专用线程池

`NotificationAsyncConfig.java` 定义了独立的 `webhookExecutor` 线程池：

```java
@Bean("webhookExecutor")
public Executor webhookExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(200);
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    // ...
}
```

**与其他线程池隔离**——避免外发慢请求拖垮审计/Agent 任务线程。队列打满时用 `CallerRunsPolicy` 降级（在调用线程同步执行），保证不丢消息。

### 手动传播 BaseContext

`@Async` 切到新线程后，`BaseContext`（ThreadLocal）里的租户 ID、用户 ID 就丢了。但 MyBatis-Plus 的 `TenantLineHandler` 依赖 `BaseContext.getTenantId()` 来自动拼接 `WHERE tenant_id = ?`——丢了就会查不到数据或越权。

`WebhookDispatcher.dispatch` 第 63-88 行手动传播：

```java
@Async("webhookExecutor")
public void dispatch(NotificationEvent event) {
    Long prevUserId = BaseContext.getUserId();      // 保存原线程上下文
    Long prevTenantId = BaseContext.getTenantId();
    try {
        BaseContext.setUserId(event.getUserId());   // 设置事件所属上下文
        BaseContext.setTenantId(event.getTenantId() != null ? event.getTenantId() : 0L);
        // ... 查询并发送 webhook ...
    } finally {
        BaseContext.clear();                        // 清理当前线程
        if (prevUserId != null) BaseContext.setUserId(prevUserId);        // 恢复原上下文
        if (prevTenantId != null) BaseContext.setTenantId(prevTenantId); // （CallerRunsPolicy 降级时重要）
    }
}
```

为什么要恢复原上下文？因为 `CallerRunsPolicy` 降级时 `dispatch` 会在**调用线程**执行，如果不恢复，调用线程后续的 DB 查询就会带着错误的租户 ID。

---

## 六、企业微信机器人：markdown 渲染

`WeComSender.java` 把通知事件转成企微 markdown 消息。核心是 severity 着色（第 147-162 行）：

```java
private String buildMarkdown(NotificationEvent event) {
    String severity = event.getSeverity() != null ? event.getSeverity() : "INFO";
    String color = switch (severity) {
        case "ERROR" -> "warning";   // 红色——预算超限、任务失败
        case "WARN"  -> "comment";   // 灰色——回归告警
        default      -> "info";      // 绿色——任务完成、审批通过
    };
    return String.format(
        "## %s%n> **类别**: %s%n> **严重度**: <font color=\"%s\">%s</font>%n%n%s%n%n---%n_Lumina 通知中心_",
        event.getTitle(), event.getCategory(), color, severity, truncate(content, MAX_BODY_CHARS));
}
```

企微只支持 `<font color="warning|comment|info">` 这三种颜色（不是标准 HTML 颜色），所以这里做了映射。

### 三个工程细节

1. **4096 字节分片**：企微单条 markdown 上限 4096 字节。`AbstractNotificationSender.chunkByBytes` 按 **UTF-8 字节**切分，且用 `codePointAt` 保证不把一个多字节字符（如中文）切成两半。
2. **限频 20 条/分钟**：企微官方限制。`acquireRateQuota` 用 Redis `INCR` 计数，首次自增时设置 60 秒过期。超限**丢弃但不计失败**（不是目标端故障，不该触发熔断）。
3. **errcode=0 才算成功**：企微返回 HTTP 200 不代表成功，body 里的 `errcode` 字段才是准的（如 `errcode=45009` 是限频）。

---

## 小结

| 机制 | 实现位置 | 作用 |
|------|---------|------|
| HMAC-SHA256 签名 | `WebhookSender.hmacSha256` | 防伪造，`X-Lumina-Signature: sha256=...` |
| 3 次指数退避重试 | `WebhookSender.sendToUrl` | 1s → 2s → 放弃，应对网络抖动 |
| 连续失败 5 次熔断 | `WebhookSender.recordFailure` | 自动禁用长期不可达的 URL，成功重置计数 |
| `@Async("webhookExecutor")` | `WebhookDispatcher.dispatch` | 独立线程池，不阻塞通知主链 |
| 手动 BaseContext 传播 | `WebhookDispatcher.dispatch` | 跨线程不丢租户/用户上下文 |
| 企微 severity 着色 | `WeComSender.buildMarkdown` | ERROR=红、WARN=灰、其他=绿 |
| 4096 字节 UTF-8 分片 | `AbstractNotificationSender.chunkByBytes` | 不截断多字节字符 |
| Redis 限频 20/min | `AbstractNotificationSender.acquireRateQuota` | 软限频，Redis 故障降级放行 |

**真实场景**：预算消耗到 95% 时触发 `BUDGET` 类别通知 → 推到用户配置的企微群机器人（红色告警）；长耗时工作流完成 → 推到 Webhook 触发下游 CI 流水线（带签名验签）。

---

## 自测

<details>
<summary><b>1. 为什么 Webhook 要用 HMAC 签名而不是只靠 HTTPS？</b></summary>

HTTPS 防的是传输链路上的窃听和篡改（中间人攻击），但**防不了接收方自己被伪造**。比如有人猜到你的 Webhook URL，直接往那个地址 POST 假消息，接收方无法区分这是 Lumina 发的还是攻击者发的。HMAC 签名用双方共享的 secret 生成，攻击者不知道 secret 就伪造不出合法的 `X-Lumina-Signature`，接收方验签即可确认来源。HTTPS 是"传输层信任"，HMAC 是"应用层身份信任"，两者互补。
</details>

<details>
<summary><b>2. 一个 Webhook 连续失败了 3 次，第 4 次成功了，此时 <code>failCount</code> 是多少？下次失败会怎样？</b></summary>

`failCount` 会重置为 **0**。因为 `send` 方法在成功时调用 `updateStatus(..., "SUCCESS", null, 0, null)`，直接把计数清零。所以第 4 次成功后，计数从 3 回到 0。下次（第 5 次）失败时，`recordFailure` 算出 `newFailCount = 0 + 1 = 1`，远未达到 `MAX_FAIL_COUNT = 5`，不会禁用。这就是"连续失败"的语义——中间有任何一次成功就重新计数。
</details>

<details>
<summary><b>3. <code>webhookExecutor</code> 线程池为什么用 <code>CallerRunsPolicy</code> 而不是 <code>AbortPolicy</code>？为什么要在 <code>finally</code> 里恢复原上下文？</b></summary>

`CallerRunsPolicy`：队列满时在**调用线程**同步执行任务，而不是抛异常丢弃。通知是"宁可慢不能丢"的消息，丢一条预算告警可能造成实际经济损失。`AbortPolicy` 会直接拒绝并抛 `RejectedExecutionException`，通知就没了。

恢复原上下文的原因：`CallerRunsPolicy` 降级时 `dispatch` 在调用线程跑，期间 `BaseContext` 被改成了事件的用户/租户。如果不恢复，调用线程后续的所有 DB 查询都会带着错误的 `tenantId`，导致数据越权或查不到数据。`finally` 块里先 `clear` 再恢复 `prevUserId/prevTenantId` 就是处理这个边界。
</details>

<details>
<summary><b>4. 企微机器人限频 20 条/分钟，超频时为什么"丢弃但不计失败"？</b></summary>

因为限频是**发送方（Lumina）侧的速率约束**，不是接收方（企微）故障。如果计失败，连续超频 5 次就会把一个正常的 webhook 熔断禁用，这是误伤。超频的正确处理是丢掉本条（等下个窗口再发），不影响该 webhook 的健康状态。代码里 `acquireRateQuota` 返回 false 后直接 `return false`，不调用 `recordResult`，所以不触发失败计数。
</details>

---

> 🚀 [J02 — Code Interpreter →](J02-code-interpreter.md)

---

📝 **本篇撰写期间修正的代码**：无。
