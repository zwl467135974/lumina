# F05 — 结构化输出：让 Agent 返回可靠 JSON

> **前置要求**：已完成 [B03 AgentScope SDK](B03-agentscope-sdk.md)
> **预计阅读**：15 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

Agent 默认返回**自然语言文本**。但很多场景需要**机器可读的结构化数据**：

- 用户问天气 → Agent 返回 `{"city":"北京","temp":28,"condition":"晴"}` 而非 "北京今天 28 度晴天"
- 调用方需要把 Agent 结果传给下游 API → 必须是合法 JSON
- 表单自动填充 → 需要 `{name, phone, address}` 精确字段

**问题**：LLM 返回的 JSON 经常格式不对——多了说明文字、字段名拼错、嵌套层级混乱。

---

## 先建立直觉：填表格 vs 写作文

自然语言输出 = **写作文**（自由发挥，格式不固定）
结构化输出 = **填表格**（字段固定，格式严格）

AgentScope 2.0 通过 `GenerateOptions.responseFormat` 让 LLM "填表格"而非"写作文"。

---

## AgentScope 2.0 的 ResponseFormat

```java
import io.agentscope.core.formatter.ResponseFormat;

// 三种模式
ResponseFormat.text()           // 普通文本（默认）
ResponseFormat.jsonObject()     // 强制返回合法 JSON 对象
ResponseFormat.jsonSchema(schema) // 按指定 Schema 返回 JSON
```

### 通过 GenerateOptions 传入

```java
GenerateOptions options = GenerateOptions.builder()
    .responseFormat(ResponseFormat.jsonObject())  // 强制 JSON 输出
    .build();

ReActAgent agent = ReActAgent.builder()
    .model(model)
    .generateOptions(options)   // 设置输出格式
    .build();
```

---

## Lumina 的实现

### 配置

```java
// AgentConfig 新增字段（@since 3.8.0）
private String structuredOutputMode;  // JSON_OBJECT / TEXT / null
```

### 引擎层注入

```java
// 文件：DefaultAgentExecutionEngine.java createReActAgent()
if (config.getStructuredOutputMode() != null) {
    ResponseFormat format;
    String mode = config.getStructuredOutputMode().toUpperCase();
    if ("JSON_OBJECT".equals(mode)) {
        format = ResponseFormat.jsonObject();
    } else {
        format = ResponseFormat.text();
    }

    GenerateOptions options = GenerateOptions.builder()
            .responseFormat(format)
            .build();
    agentBuilder.generateOptions(options);
}
```

---

## 使用场景

### 场景 1：天气查询 API

Agent 配置 `structuredOutputMode=JSON_OBJECT`，Prompt 要求返回天气 JSON：

```
System: 你是天气助手，返回 JSON 格式：{"city":"城市","temp":温度,"condition":"天气"}

User: 北京今天天气怎么样？
Agent: {"city":"北京","temp":28,"condition":"晴"}
```

### 场景 2：信息提取

从非结构化文本提取结构化数据：

```
User: 从这段话提取联系人信息："张三，手机13800138000，邮箱zhangsan@test.com"
Agent: {"name":"张三","phone":"13800138000","email":"zhangsan@test.com"}
```

### 场景 3：API 联动

Agent 结果直接传给下游系统：

```java
// Agent 返回 JSON → 直接反序列化为 DTO
String agentResult = agentService.executeAgent(agentId, task, null);
WeatherDTO weather = objectMapper.readValue(agentResult, WeatherDTO.class);
// 直接使用，不需要正则解析
```

---

## 注意事项

| 限制 | 说明 |
|------|------|
| 模型支持 | 不是所有 LLM 都支持 JSON Mode（GLM/OpenAI/Claude 支持，部分小模型不支持） |
| Prompt 配合 | 即使开了 JSON Mode，Prompt 里也要明确说明返回格式 |
| 流式输出 | JSON Mode + 流式输出可能导致 JSON 不完整（分片返回） |
| 兜底 | LLM 仍可能偶尔返回非法 JSON，调用方需要 try-catch |

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| responseFormat | GenerateOptions 中约束 LLM 输出格式的字段 |
| JSON_OBJECT | 强制 LLM 返回合法 JSON（不是文本里包含 JSON） |
| AgentConfig.structuredOutputMode | per-Agent 配置结构化输出模式 |

### 自测题

1. 为什么不能只靠 Prompt 说"返回 JSON"来保证输出格式？
   <details><summary>答案</summary>LLM 可能加说明文字（"好的，这是 JSON: {...}"）、字段名拼错、JSON 格式不合法。responseFormat 在模型层面强制约束，比 Prompt 提醒可靠得多。</details>

2. ResponseFormat.jsonObject() 和 ResponseFormat.text() 有什么区别？
   <details><summary>答案</summary>jsonObject 强制模型返回合法 JSON 对象（API 层面约束）；text 是普通文本输出（默认）。前者调用方可以直接 JSON.parse，后者需要自己提取。</details>

3. 哪些场景不适合用结构化输出？
   <details><summary>答案</summary>创意写作（诗歌/故事）、对话聊天、需要自然语言解释的场景。结构化输出会限制表达的灵活性。</details>

> 🚀 返回 [AI 专项导读](README.md)

---

📝 **本篇撰写期间修正的代码**：无（结构化输出能力为本次新增）。
