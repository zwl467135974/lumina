# F07 — 自动会话管理

> **前置要求**：已完成 [E04 会话生命周期](E04-conversation-lifecycle.md)、[F01 流式输出](F01-streaming-sse.md)
> **预计阅读**：10 分钟
> **难度**：⭐⭐☆☆☆

---

## 这节解决什么问题

Lumina 早期的对话端点是 `POST /api/v1/agents/{id}/execute`——它的 `conversationId` 是**必填的**（不传就没有多轮上下文）。这逼着前端干一堆脏活：

1. 用户首次发消息前，先调一个"创建会话"接口拿到 `conversationId`
2. 把 `conversationId` 存在前端 state 里
3. 每次发消息都带上它
4. 用户刷新页面后还要从 localStorage 恢复
5. 多个聊天窗口时要维护多个 `conversationId`

这套逻辑每个前端都得抄一遍。Lumina v3.x 加了一个**"拨号即通话"**的端点，把这些状态管理搬到了后端。

---

## 先建立直觉：对讲机 vs 电话

- **对讲机模式（旧的 /execute）**：你要自己管频道——先按下"频道 1"建立连接，每次说话都按住频道键，断了得自己重连。
- **电话模式（新的 /chat）**：你只要拨号（发消息），对方自动接通；挂了想再说，回拨同一个号码（带 conversationId）就接着上次的对话。

**核心差别**：电话模式让前端**不需要知道频道的存在**——你只管发消息，连接建立是对方（后端）的事。第一次拨号时后端自动给你分配号码（conversationId），后续回拨只要带上这个号码。

---

## 新端点：POST /api/v1/agents/{id}/chat

```java
// 文件：lumina-modules/lumina-business-agent/.../api/controller/AgentController.java（约 497 行）

@Audit(module = "agent", action = "CHAT", description = "对话")
@Operation(summary = "对话（自动会话管理，无需手动管理 conversationId）")
@PostMapping("/{id}/chat")
public R<Map<String, String>> chat(
        @PathVariable("id") Long id,
        @RequestBody Map<String, String> body) {
    String message = body.get("message");
    String conversationId = body.get("conversationId");

    if (message == null || message.trim().isEmpty()) {
        throw new BusinessException(ErrorCode.AGENT_TASK_EMPTY);
    }

    // 无 conversationId → 自动创建新会话
    if (conversationId == null || conversationId.isBlank()) {
        ConversationDO conv = conversationService.createConversation(id, null);
        conversationId = conv.getConversationUuid();
        log.info("自动创建会话: agentId={}, conversationId={}", id, conversationId);
    }

    log.info("对话: agentId={}, conversationId={}, message={}", id, conversationId, message);

    // 执行 Agent
    String reply = agentService.executeAgent(id, message, conversationId);

    return R.success(Map.of(
            "conversationId", conversationId,
            "reply", reply != null ? reply : ""
    ));
}
```

### 两种调用形态

| 调用方式 | 请求 | 后端行为 | 返回 |
|---------|------|---------|------|
| **首聊**（不带 conversationId） | `{"message": "你好"}` | 自动 `createConversation` 分配新 UUID | `{conversationId: "abc-123", reply: "..."}` |
| **续聊**（带 conversationId） | `{"message": "继续", "conversationId": "abc-123"}` | 复用已有会话，自动加载历史上下文 | `{conversationId: "abc-123", reply: "..."}` |

**一个端点搞定两种场景**——后端用 `conversationId` 是否为空来区分。前端代码因此变得极简。

---

## 与旧端点 /execute 的对比

```java
// 旧端点：conversationId 是可选的，但不传就没有多轮上下文
@PostMapping("/{id}/execute")
public R<String> executeAgent(
        @PathVariable("id") Long id,
        @RequestBody Map<String, String> body) {
    String task = body.get("task");
    String conversationId = body.get("conversationId");   // 前端必须自己管
    ...
    return R.success(result);   // ← 返回里没有 conversationId
}
```

| 维度 | /execute（旧） | /chat（新） |
|------|--------------|------------|
| 字段名 | `task` | `message`（更像聊天） |
| conversationId 处理 | 前端手动管 | 后端自动创建/复用 |
| 返回 | 只有结果字符串 | `{conversationId, reply}` |
| 适合场景 | 任务型（"帮我查 X"） | 对话型（持续多轮聊天） |

两个端点**不冲突，可以共存**——`/execute` 适合一次性任务，`/chat` 适合持续对话场景。

---

## 前端集成示例

```javascript
// 第一次发消息：不带 conversationId
async function sendMessage(text) {
  const resp = await fetch(`/api/v1/agents/${agentId}/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message: text })
    // 注意：首聊不传 conversationId
  });
  const data = await resp.json();
  // 保存返回的 conversationId，后续都带上
  currentConversationId = data.data.conversationId;
  return data.data.reply;
}

// 后续消息：带上保存的 conversationId 续聊
async function continueChat(text) {
  const resp = await fetch(`/api/v1/agents/${agentId}/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      message: text,
      conversationId: currentConversationId   // ← 续聊
    })
  });
  const data = await resp.json();
  return data.data.reply;
}
```

**前端要做的事**就三步：第一次调用不传 conversationId → 把返回的 conversationId 存起来 → 后续调用都带上它。比旧方案省掉了"主动创建会话""刷新恢复""多窗口管理"等一大堆状态逻辑。

> **小贴士**：如果用户刷新页面，前端可以选择丢弃 conversationId（开启全新对话），或者从 localStorage 恢复（继续上次对话）——这个决策权交给了前端，但**后端的接口契约**永远简单：带就续、不带就新建。

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| /chat 端点 | "拨号即通话"——前端不用手动管 conversationId |
| 首聊 | 不传 conversationId，后端自动创建并返回 |
| 续聊 | 传 conversationId，后端复用并加载历史上下文 |
| 返回格式 | `{conversationId, reply}`，conversationId 必回 |
| 与 /execute 区别 | execute 是任务型返回纯字符串，chat 是对话型自动管会话 |

### 自测题

1. POST /api/v1/agents/{id}/chat 在请求不带 conversationId 时会做什么？
   <details><summary>答案</summary>调用 <code>conversationService.createConversation(id, null)</code> 自动创建一个新会话，拿到新生成的 UUID 作为 conversationId，然后用它执行 Agent，最后在响应体里把 conversationId 一起返回给前端。前端拿到后保存用于后续续聊。</details>

2. 为什么 /chat 的响应里必须带回 conversationId，而旧的 /execute 不带？
   <details><summary>答案</summary>因为 /chat 的核心价值是"自动会话管理"——首聊时 conversationId 是后端临时分配的，前端如果不从响应里拿到它就丢了，后续无法续聊。/execute 假设前端已经自己管好了 conversationId（要么不传走单轮，要么前端早就创建好传进来），所以响应里不需要回带。</details>

3. 前端集成 /chat 端点的基本流程是什么？
   <details><summary>答案</summary>三步：(1) 第一次发消息时不传 conversationId，后端自动创建；(2) 从响应的 <code>data.conversationId</code> 取出并保存到前端 state/localStorage；(3) 后续所有消息都把这个 conversationId 带上以续聊同一会话。如果用户想开新对话，清空保存的 conversationId 即可。</details>

---

> 🚀 [G01 — 模型价格管理 →](G01-model-pricing.md)

---

📝 **本篇撰写期间修正的代码**：无（/chat 端点为既有实现，本节仅做解读）。
