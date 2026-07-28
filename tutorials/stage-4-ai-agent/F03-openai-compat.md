# F03 — OpenAI 兼容出口：标准 SDK 直接对接

> **前置要求**：已完成 [F02 多模态](F02-multimodal.md)
> **预计阅读**：15 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

你的团队用 LangChain 开发了一个应用，想接入 Lumina 的 Agent。但 LangChain 只认 OpenAI API 格式。难道要重写对接代码？

**不需要。** Lumina 伪装成 OpenAI API，任何支持 OpenAI 的工具都能直接调用。

---

## 先建立直觉：万能充电转换器

不同手机的充电口不一样（Type-C、Lightning、Micro-USB）。但充电宝有一个**万能转换器**——不管什么口，插上就能充。

Lumina 的 OpenAI 兼容出口就是这个转换器：不管客户端是 OpenAI SDK、LangChain、Dify 还是 Cursor，插上 Lumina 就能用。

---

## 架构：协议伪装

```
标准 OpenAI SDK          Lumina OpenAI 兼容层
┌──────────────┐        ┌──────────────────────────┐
│ client.chat   │──HTTP──│ POST /v1/chat/completions │
│ .completions  │        │  → 解析 model=agentId     │
│ .create()     │        │  → 调用 Lumina Agent      │
└──────────────┘        │  → 包装成 OpenAI 响应格式  │
                        └──────────────────────────┘
```

**核心思路**：接收 OpenAI 格式请求 → 提取 `model` 字段作为 Agent ID → 执行 Agent → 把结果包装成 OpenAI 格式返回。

---

## 两个端点

```java
// 文件：lumina-business-agent/.../api/controller/OpenAiCompatController.java
// 以下为概念示意（真实 DTO 名为 ChatCompletionRequest/Response，方法通过 HttpServletResponse 写流）

// 1. 对话端点（支持流式和非流式）
@PostMapping(value = "/v1/chat/completions")
public void chatCompletions(@RequestBody ChatCompletionRequest request,
                            HttpServletResponse response) {
    Long agentId = resolveAgentId(request.getModel());  // model="agent-243" → agentId=243

    if (request.isStream()) {
        writeStream(response, ...);  // 流式 SSE（OpenAI 流式格式 chunk）
    } else {
        writeCompletion(response, ...);  // 非流式 JSON
    }
}

// 2. 模型列表端点（Agent 伪装成 model）
@GetMapping("/v1/models")
public ModelListResponse listModels() {
    // 把 Lumina 的 Agent 列表包装成 OpenAI 的 model 列表
    // 每个 Agent → { id: "agent-{agentId}", object: "model" }
}
```

---

## 使用示例

### Python OpenAI SDK

```python
from openai import OpenAI

client = OpenAI(
    api_key="sk-your-lumina-token",    # Lumina API Token
    base_url="http://lumina-host:8080/v1"
)

# 非流式
response = client.chat.completions.create(
    model="agent-243",                  # Lumina Agent ID
    messages=[{"role": "user", "content": "你好"}]
)
print(response.choices[0].message.content)

# 流式
stream = client.chat.completions.create(
    model="agent-243",
    messages=[{"role": "user", "content": "写一首诗"}],
    stream=True
)
for chunk in stream:
    print(chunk.choices[0].delta.content or "", end="")
```

### cURL

```bash
curl http://lumina-host:8080/v1/chat/completions \
  -H "Authorization: Bearer sk-your-lumina-token" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "agent-243",
    "messages": [{"role": "user", "content": "1+1=?"}]
  }'
```

---

## 认证：API Token

OpenAI 兼容端点不用 JWT（JWT 是给前端用的），而用 **API Token**（`sk-` 格式）：

```java
// 文件：lumina_api_token 表
// token: sk-lumina-xxx（用户在管理后台生成）
// 权限: 绑定到特定 Agent 或全部 Agent
// 有效期: 可设置过期时间

// 请求头验证
Authorization: Bearer sk-lumina-xxx
// → Gateway 的 ApiTokenAuthGlobalFilter 查表验证
// → 验证通过后注入 BaseContext（userId/tenantId）
```

### 生成 API Token

在 Lumina 管理后台 → 系统管理 → API Token → 创建：

| 字段 | 说明 |
|------|------|
| name | Token 名称（如"LangChain 对接"） |
| agentScope | 允许调用的 Agent（可选限定） |
| expiresAt | 过期时间 |

---

## 兼容性说明

| OpenAI 参数 | Lumina 支持 | 说明 |
|-------------|------------|------|
| `model` | ✅ | 映射为 Agent ID |
| `messages` | ✅ | 标准对话格式 |
| `stream` | ✅ | 流式/非流式 |
| `temperature` | ⚠️ | 部分支持（Agent 配置优先） |
| `max_tokens` | ⚠️ | 部分支持 |
| `tools` / `function_calling` | ❌ | 用 Lumina 自己的工具体系 |
| `response_format` | ❌ | 用 Agent 的 structuredOutputMode |

> **注意**：Lumina 不是完整的 OpenAI 替代品——它是"Agent 即 API"。OpenAI 的裸模型调用能力通过 Lumina Agent 的配置实现（选模型、设 Prompt、配工具），而不是通过 OpenAI 参数控制。

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| OpenAI 兼容 | Lumina 伪装成 OpenAI API，标准 SDK 直接调用 |
| model 字段 | `agent-{agentId}` 映射到 Lumina Agent |
| 认证 | `sk-` 格式 API Token（不是 JWT） |
| 价值 | LangChain/Dify/Cursor 等工具零改造对接 |

### 自测题

1. OpenAI SDK 的 `model` 参数在 Lumina 中代表什么？
   <details><summary>答案</summary>model="agent-{agentId}" 映射到 Lumina 的 Agent ID，Lumina 解析后执行对应 Agent。</details>

2. 为什么 OpenAI 兼容端点用 API Token 而不是 JWT？
   <details><summary>答案</summary>JWT 是给浏览器前端用的（有过期刷新机制）；API Token 是给程序对接用的（sk- 格式，长期有效，可绑定 Agent 权限范围）。</details>

3. `tools` 参数为什么不支持？（提示：Lumina 有自己的工具体系）
   <details><summary>答案</summary>Lumina 有自己的工具体系（@AgentTool 注解 + MCP），工具在 Agent 配置中绑定而非请求参数中指定。OpenAI 的 function_calling 格式和 Lumina 的工具调用机制不同。</details>

> 🚀 [F04 — 安全防护 →](F04-security-defense.md)

---

📝 **本篇撰写期间修正的代码**：无。
