# A01 — LLM 大模型基础

> **前置要求**：无（AI 入门第一篇）
> **预计阅读**：25 分钟
> **难度**：⭐⭐☆☆☆

---

## 这节解决什么问题

Lumina 是 AI Agent 平台，整个项目围绕"大模型"运转。但什么是大模型？它怎么工作的？为什么有时候聪明有时候蠢？

这节用大白话讲清 LLM（Large Language Model，大语言模型）的本质。**如果你完全不懂 AI，从这里开始。**

---

## LLM 是什么？先建立直觉

### 类比：一个读过全网书的高材生

想象一个高材生，他读完了互联网上几乎所有公开文本——书、论文、新闻、代码、对话。你问他问题，他根据"读过的记忆"生成一个最合理的回答。

**但他有几个特点**：
1. **不是搜索**——他不翻书查原文，而是根据记忆"生成"回答（可能记错）
2. **不知道实时信息**——培训完后发生的事他不知道（除非你告诉他）
3. **有时候自信地说错**——这叫"幻觉"（Hallucination）
4. **按字收费**——你问多少字、答多少字，都要钱

**LLM 就是这个高材生**——一个用海量文本训练出来的"下一个字预测器"。

---

## 它到底在做什么：下一个 Token 预测

### 核心原理（极简版）

LLM 的本质是一个数学模型：**给定前面的文字，预测下一个字是什么。**

```
输入："今天天气真"
模型思考：根据训练数据，"今天天气真"后面最可能跟什么？
预测："好"（概率 80%）> "不错"（15%）> "热"（4%）...
输出："好"

输入："今天天气真好"
预测："，"（60%）> "我们"（20%）...
输出："，"
```

一个字一个字地预测，连起来就是一段话。**这就是 AI "生成"回答的原理。**

### 为什么它看起来很聪明？

因为训练数据足够多（几万亿字），它学到了语言的模式、知识的关系、推理的逻辑。当你说"写一个冒泡排序"，它根据见过的无数代码示例，生成一段最合理的代码。

> ⚠️ **关键理解**：LLM 不是"查找答案"，是"生成最可能的文本"。这意味着它可能编造不存在的东西（幻觉）。

---

## Token：LLM 的计价单位

### 什么是 Token

LLM 不按"字"或"词"处理文本，而是按 **Token**。Token 是模型处理文本的最小单位：

| 语言 | 大约比例 | 示例 |
|------|----------|------|
| 英文 | 1 Token ≈ 0.75 个单词 | "hello" = 1 Token |
| 中文 | 1 Token ≈ 0.5-1 个汉字 | "你好" ≈ 2 Token |
| 代码 | 不固定 | `function()` ≈ 3-4 Token |

### 为什么 Token 重要

**LLM 按 Token 收费**——输入有输入价，输出有输出价：

```
输入 1000 Token × 0.01 元/千Token = 0.01 元
输出 500 Token × 0.03 元/千Token = 0.015 元
本次调用总计 = 0.025 元
```

> 📖 Token 计费的详细实现在 [AI 模块 G02-Token 计费](G02-token-billing.md)。

---

## 上下文窗口：LLM 的"短期记忆"

### 什么是上下文窗口

LLM 一次能"看到"的文本长度有上限——叫**上下文窗口**（Context Window），单位是 Token。

```
模型 A：4K Token 窗口   → 一次最多看约 3000 字
模型 B：32K Token 窗口  → 一次最多看约 24000 字
模型 C：128K Token 窗口 → 一次最多看约 96000 字（一整本书）
```

### 超了会怎样

如果你发的消息 + 历史对话超过窗口大小，**最早的消息会被"挤出去"**——模型就忘了之前说过什么。

> 📖 怎么管理对话上下文、怎么裁剪见 [AI 模块 E03-多轮上下文](E03-multiturn-context.md)。

---

## 主流大模型

| 厂商 | 模型 | 特点 |
|------|------|------|
| **智谱** | GLM-4 / GLM-4-Flash | 国产，Flash 版免费 |
| **阿里** | qwen-max / qwen-plus（通义千问） | 国产，DashScope 平台 |
| **OpenAI** | GPT-4 / GPT-4o | 能力强，贵 |
| **Anthropic** | Claude-3 / Claude-3.5 | 长文本擅长 |
| **月之暗面** | Moonshot/Kimi | 长上下文 |
| **本地** | Ollama（Llama/Qwen 本地版） | 免费，数据不出内网 |

### 在 Lumina 里怎么选

```java
// 文件：lumina-agent-core/.../model/AgentConfig.java:71-103
@Data
public static class LLMConfig implements Serializable {
    private String modelType;       // dashscope/openai/anthropic/ollama
    private String modelName;       // qwen-max/gpt-4/claude-3-opus
    private String apiKey;          // API 密钥
    private Double temperature;     // 温度（创造力）
    private Integer maxTokens;      // 最大输出长度
    private String baseUrl;         // API 地址（OpenAI 兼容场景）
}
```

---

## 关键参数：Temperature（温度）

```java
private Double temperature;    // 0.0 - 1.0
```

Temperature 控制"创造力"——

| Temperature | 效果 | 适用场景 |
|-------------|------|----------|
| 0.0 | 每次回答几乎一样（最确定） | 事实问答、代码生成、JSON 输出 |
| 0.7 | 有一定变化（平衡） | 日常对话（默认） |
| 1.0 | 回答很多样（最有创意） | 创意写作、头脑风暴 |

**类比**：Temperature 像手握的松紧。握得紧（0.0）只抓最确定的东西；握得松（1.0）什么都可能抓到。

---

## 在 Lumina 里怎么调 LLM

```java
// 文件：lumina-agent-core/.../model/ChatModelFactory.java
public ChatModel create(AgentConfig.LLMConfig config) {
    return switch (config.getModelType()) {
        case "dashscope" -> createDashScope(config);    // 通义千问
        case "openai" -> createOpenAI(config);          // GPT/DeepSeek/GLM
        case "anthropic", "claude" -> createClaude(config);
        case "gemini" -> createGemini(config);
        case "ollama" -> createOllama(config);          // 本地
        default -> throw new BusinessException(...);
    };
}
```

Lumina 用**工厂模式**封装——配什么 `modelType` 就创建对应厂商的客户端。你不用关心各家 API 差异。

---

## 动手试试

1. **打开 `AgentConfig.java`**：看 `LLMConfig` 内嵌类有哪些字段
2. **打开 `ChatModelFactory.java`**：看 switch 分发逻辑
3. **在 Lumina 前端创建一个 Agent**：选模型类型、填 API Key，理解你在配什么

---

## 小结

| 概念 | 一句话记忆 | 类比 |
|------|-----------|------|
| LLM | 海量文本训练的"下一个字预测器" | 读过全网书的高材生 |
| Token | LLM 处理和计价的最小单位 | 按字收费的计量单位 |
| 上下文窗口 | 一次能看多少 Token | 高材生的短期记忆容量 |
| Temperature | 创造力参数（0 确定 → 1 创意） | 手握松紧 |
| 幻觉 | 自信地说错 | 高材生记错了 |

---

## 下一步

下一篇 [Token 与上下文窗口](A02-token-context-window.md)——深入讲 Token 计量和关键参数。

> 🚀 [A02 — Token 与上下文 →](A02-token-context-window.md)

---

## 自测题

1. **LLM 是"搜索答案"还是"生成答案"？有什么区别？**
   <details><summary>答案</summary>生成。LLM 根据训练数据预测下一个字，不是从数据库查找。所以它可能编造不存在的答案（幻觉）。</details>

2. **为什么 LLM 不知道"今天的新闻"？**
   <details><summary>答案</summary>训练数据有截止日期。训练完后发生的事它不知道。要让它知道实时信息，需要用工具（如搜索）或 RAG。</details>

3. **Temperature 设 0 和设 1 有什么区别？**
   <details><summary>答案</summary>0=最确定（每次几乎一样），适合代码/事实问答；1=最有创意（回答很多样），适合创意写作。</details>

---

📝 **本篇撰写期间修正的代码**：无。
