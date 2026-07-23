# F02 — 多模态

> **前置要求**：已完成 [F01-流式输出](F01-streaming-sse.md)
> **预计阅读**：10 分钟
> **难度**：⭐⭐☆☆☆

---

## 这节解决什么问题

不只输入文字——还能输入图片、PDF、Word，让 AI "看图说话"或"读文档"。

---

## 多模态是什么

一条消息里混合多种内容形态：

```java
// 文件：lumina-agent-core/.../model/MultimodalContent.java
// sealed interface（密封接口）统一图片和文档
public sealed interface MultimodalContent
    permits MultimodalImage, MultimodalDocument {}
```

---

## 怎么用

```java
// 前端先上传文件拿 fileUuid，再调多模态接口
POST /api/v1/agents/{id}/execute/multimodal
{
  "task": "这张图里是什么？",
  "fileUuids": ["uuid-1", "uuid-2"],
  "conversationId": "conv-xxx"
}
```

后端把文件转成 `MultimodalImage`（Base64）或 `MultimodalDocument`（提取文本），拼到消息里发给 LLM。

---

## 消息构造

```java
// DefaultAgentExecutionEngine.buildContextMessages()
// 一条消息里同时有 TextBlock + ImageBlock
List<Block> blocks = new ArrayList<>();
blocks.add(new TextBlock(task));                  // 文字
for (MultimodalImage img : images) {
    blocks.add(new ImageBlock(img.getBase64()));  // 图片
}
Msg multimodalMsg = Msg.of(USER, blocks);
```

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| 多模态 | 文字 + 图片 + 文档混合输入 |
| MultimodalContent | sealed interface 统一图片和文档 |
| ImageBlock | 图片转 Base64 塞进消息 |

> 🚀 [F03 — OpenAI 兼容 →](F03-openai-compat.md)

---

📝 **本篇撰写期间修正的代码**：无。
