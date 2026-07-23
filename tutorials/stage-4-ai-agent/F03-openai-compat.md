# F03 — OpenAI 兼容出口

> **前置要求**：已完成 [F02-多模态](F02-multimodal.md)
> **预计阅读**：10 分钟
> **难度**：⭐⭐☆☆☆

---

## 这节解决什么问题

Lumina 的 Agent 能力不仅前端能用——还能伪装成 **OpenAI API**，让标准 OpenAI SDK 直接调用。这样其他工具（如 Dify、LangChain）可以直接对接 Lumina。

---

## 怎么用

```python
# 标准 OpenAI SDK，base_url 指向 Lumina
from openai import OpenAI

client = OpenAI(
    api_key="sk-你的-lumina-api-token",   # Lumina 的 API Token
    base_url="http://lumina-host/v1"       # 指向 Lumina
)

response = client.chat.completions.create(
    model="agent-1",     # Lumina 的 Agent ID 伪装成 model
    messages=[{"role": "user", "content": "你好"}]
)
```

**效果**：对 OpenAI SDK 来说，Lumina 就是一个"OpenAI API"——但背后执行的是 Lumina Agent。

---

## 两个端点

```java
// 文件：OpenAiCompatController.java
POST /v1/chat/completions    // 对话（stream 字段区分流式/非流式）
GET  /v1/models              // 模型列表（Agent 伪装成 model）
```

---

## 认证：API Token

不用 JWT，用 `sk-xxx` 格式的 API Token：

```java
// Gateway 的 ApiTokenAuthGlobalFilter 或 StandaloneJwtFilter 处理
// sk- 开头的 token → 查 lumina_api_token 表验证
```

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| OpenAI 兼容 | Lumina 伪装成 OpenAI API |
| 价值 | 标准 SDK / 第三方工具直接对接 |
| 认证 | sk-xxx API Token（不是 JWT） |

> 🚀 [F04 — 安全防护 →](F04-security-defense.md)

---

📝 **本篇撰写期间修正的代码**：无。
