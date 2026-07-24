# F02 — 多模态：让 AI 看图说话、读文档

> **前置要求**：已完成 [F01 流式输出](F01-streaming-sse.md)
> **预计阅读**：15 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

传统 Agent 只能处理文字。但现实中用户会发截图问"这个报错怎么解决"、上传 PDF 问"帮我总结这份合同"。**多模态**（Multimodal）让 Agent 同时理解文字、图片和文档。

---

## 先建立直觉：快递包裹

用户发一条消息，就像寄一个**快递包裹**。包裹里可以有：
- 📝 文字（TextBlock）——说明书
- 🖼️ 图片（ImageBlock）——照片
- 📄 文档（TextBlock from PDF/Word）——把文档内容提取成文字

Agent 拆开包裹后，把所有内容"铺在桌上"一起看——就像人一边看图一边读说明。

---

## 数据结构：密封接口

Lumina 用 Java 的 `sealed interface` 统一管理多模态内容：

```java
// 文件：lumina-agent-core/.../model/MultimodalContent.java
public sealed interface MultimodalContent
    permits MultimodalImage, MultimodalDocument {}

// 图片：Base64 编码的二进制数据
public record MultimodalImage(String mediaType, String data) implements MultimodalContent {}

// 文档：已提取的文本内容（PDF/Word 由 OCR 或解析器转换）
public record MultimodalDocument(String text, String sourceFileName) implements MultimodalContent {}
```

**为什么用 sealed interface？** 编译器保证 `MultimodalContent` 只有这两种实现——未来加音频（MultimodalAudio）时，所有 switch 分支都会编译报错提醒你处理新类型。

---

## 使用方式

### API 调用

```http
# 步骤 1：上传文件，获取 fileUuid
POST /api/v1/files/upload
Content-Type: multipart/form-data
→ {"fileUuid": "abc-123", "fileName": "screenshot.png"}

# 步骤 2：多模态执行
POST /api/v1/agents/{id}/execute/multimodal
{
  "task": "这个报错怎么解决？",
  "fileUuids": ["abc-123"],
  "conversationId": "conv-xxx"
}
```

### 引擎层处理

```java
// 文件：DefaultAgentExecutionEngine.java executeMultimodalSync()
// 1. 根据 fileUuid 加载文件
List<MultimodalContent> contents = fileUuids.stream()
    .map(uuid -> {
        FileDO file = fileService.getByUuid(uuid);
        if (file.isImage()) {
            return new MultimodalImage(file.getMediaType(), file.getBase64Data());
        } else {
            // PDF/Word → 提取文本
            String text = documentParser.extract(file);
            return new MultimodalDocument(text, file.getFileName());
        }
    }).toList();

// 2. 传给引擎执行
return executeSyncInternal(businessType, task, contents, config, conversationId);
```

---

## 消息构造：ContentBlock

AgentScope 2.0 的 `Msg` 支持混合内容块（ContentBlock）：

```java
// 文件：DefaultAgentExecutionEngine.java buildContextMessages()
if (contents != null && !contents.isEmpty()) {
    List<ContentBlock> blocks = new ArrayList<>();

    // 文字部分
    blocks.add(TextBlock.builder().text(currentPrompt).build());

    for (MultimodalContent content : contents) {
        if (content instanceof MultimodalImage image) {
            // 图片 → ImageBlock（Base64 编码）
            blocks.add(ImageBlock.builder()
                    .source(Base64Source.builder()
                            .mediaType(image.getMediaType())
                            .data(image.getData())
                            .build())
                    .build());
        } else if (content instanceof MultimodalDocument doc) {
            // 文档 → TextBlock（已提取的文本）
            if (doc.text() != null && !doc.text().isBlank()) {
                blocks.add(TextBlock.builder()
                        .text("[文档内容: " + doc.sourceFileName() + "]\n" + doc.text())
                        .build());
            }
        }
    }

    messages.add(Msg.builder().role(MsgRole.USER).content(blocks).build());
}
```

**关键区别**：
- 图片 → `ImageBlock`（LLM 的视觉模型直接"看"图）
- 文档 → `TextBlock`（提取文字后作为上下文，LLM"读"文本）

---

## Token 成本影响

多模态输入的 Token 消耗远高于纯文本：

| 输入类型 | Token 消耗 | 原因 |
|---------|-----------|------|
| 纯文字 "你好" | ~2 Token | 文字编码效率高 |
| 一张 1024x1024 图片 | ~765 Token | 图片被切成 patches，每个 patch 约 0.75 Token |
| 10 页 PDF（提取文本） | ~3000-5000 Token | 取决于文本密度 |

> **成本建议**：图片输入按需使用，PDF 优先提取文本而非整页传图。Lumina 默认走文本提取路径（`MultimodalDocument`），只有用户明确传图片才走 `ImageBlock`。

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| 多模态 | 文字 + 图片 + 文档混合输入 |
| sealed interface | 编译器保证类型安全，新增类型时强制处理 |
| ImageBlock | 图片 Base64 编码，LLM 视觉模型直接处理 |
| TextBlock from Document | PDF/Word 提取文本后作为上下文 |
| Token 成本 | 图片 > 文档 > 纯文字 |

### 自测题

1. 为什么 PDF 不直接传图片给 LLM？（提示：Token 成本）
   <details><summary>答案</summary>一张图 ~765 Token，10 页 PDF 传图 ~7650 Token；提取文本只要 ~3000-5000 Token。文本路径成本低 2-3 倍。</details>

2. sealed interface 相比普通 interface 有什么好处？
   <details><summary>答案</summary>编译器保证只有指定的实现类，新增类型时所有 switch/pattern matching 分支会编译报错，强制处理新类型，避免遗漏。</details>

3. `MultimodalDocument` 和 `MultimodalImage` 在消息构造时有什么区别？
   <details><summary>答案</summary>Document 转为 TextBlock（提取的文字作为上下文），Image 转为 ImageBlock（Base64 图片让 LLM 视觉模型直接处理）。</details>

> 🚀 [F03 — OpenAI 兼容 →](F03-openai-compat.md)

---

📝 **本篇撰写期间修正的代码**：无。
