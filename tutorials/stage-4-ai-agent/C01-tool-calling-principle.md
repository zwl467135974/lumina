# C01 — 工具调用原理：Function Calling

> **前置要求**：已完成 [模块 B 全部](README.md)
> **预计阅读**：20 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

ReAct Agent 能"用工具"——但 LLM 本身只是个文本预测器，它怎么知道有哪些工具？怎么决定调哪个？参数怎么传？

这节讲清 **Function Calling**（工具调用）的原理。

---

## 工具调用是什么？先建立直觉

### 类比：实习生有工具箱

LLM 本身只会"说话"——它没有手、不能上网、不能算数。但如果你给它一个**工具箱**，告诉它"这里有什么工具、怎么用"，它就能"说话→指令→你执行→把结果告诉它→它继续说"。

```
工具箱: {
  "getCurrentTime": "获取当前时间",
  "webSearch": "搜索互联网",
  "calculate": "数学计算"
}

用户: "现在几点了？"

LLM 思考: "用户问时间，我有 getCurrentTime 工具。"
LLM 输出: "我要调用 getCurrentTime 工具，不需要参数。"

你的代码: 执行 getCurrentTime() → 返回 "14:30"

LLM 看到 "14:30" 后回答: "现在是下午 2:30。"
```

**这就是 Function Calling**——LLM 不直接执行代码，而是输出"我想调什么工具+什么参数"，你的代码执行后把结果喂回去。

---

## LLM 怎么知道有哪些工具

你在调 LLM API 时，把工具清单作为参数传入：

```java
// 简化示意
ChatModel.call(
    messages,        // 对话历史
    tools            // 工具清单（name + description + parameters）
)
```

工具清单长这样（JSON Schema 格式）：
```json
[
  {
    "name": "getCurrentTime",
    "description": "获取当前日期和时间",
    "parameters": {}
  },
  {
    "name": "webSearch",
    "description": "搜索互联网获取实时信息",
    "parameters": {
      "type": "object",
      "properties": {
        "query": { "type": "string", "description": "搜索关键词" }
      },
      "required": ["query"]
    }
  }
]
```

LLM 看了这个清单，就知道"有什么工具可用、每个工具怎么调"。

---

## 一次完整的工具调用循环

```
Round 1:
  User: "北京今天天气怎么样？"
  LLM 看到: tools=[webSearch, getCurrentTime]
  LLM 输出: {"tool": "webSearch", "args": {"query": "北京今天天气"}}
  （LLM 不执行，只输出指令）

  你的代码: 执行 webSearch("北京今天天气") → "28°C 晴"
  把结果喂回去: Tool result: "28°C 晴"

Round 2:
  LLM 看到: 用户问题 + 上一步调了 webSearch 得到 "28°C 晴"
  LLM 输出: "北京今天 28°C，晴天。"
  （这次没有调工具，直接回答 → 循环结束）
```

---

## 在 Lumina 里怎么实现

### @AgentTool 注解

```java
// 文件：lumina-agent-core/.../tool/AgentTool.java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AgentTool {
    String name();                  // 工具名称（如 util.getCurrentTime）
    String description() default ""; // 工具描述（给 LLM 看的）
    String category() default "";    // 分类（管理用）
    boolean enabled() default true;  // 是否启用
}
```

### 一个普通方法变成工具

```java
// 文件：lumina-agent-core/.../tool/GeneralToolProvider.java:125-143
@AgentTool(
    name = "util.getCurrentTime",
    description = "获取当前日期和时间。返回标准格式的时间字符串和Unix时间戳。",
    category = "util.time"
)
public Map<String, Object> getCurrentTime() {
    LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
    Map<String, Object> result = new HashMap<>();
    result.put("datetime", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    result.put("timestamp", now.atZone(ZoneId.of("Asia/Shanghai")).toEpochSecond());
    return result;
}
```

**这就是全部**——一个普通的 Java 方法，加了 `@AgentTool` 注解，就变成了 Agent 可调用的工具。**description 是关键**——LLM 靠它判断"什么时候该用这个工具"。

---

## 工具注册：自动扫描

```java
// 文件：lumina-agent-core/.../manager/EnhancedToolManager.java
// 启动时扫描所有 @AgentTool 注解的方法，自动注册
public void scanAndRegisterTools() {
    // 反射扫描所有 Bean 的方法
    // 发现有 @AgentTool 注解的 → 注册为 ToolDefinition
}
```

你不用手动注册——**加注解就行，框架自动发现**。

---

## 工具配置：白名单

不是所有工具都给所有 Agent。Agent 可以配工具白名单：

```java
// AgentConfig
private ToolConfig toolConfig;
// toolConfig 里指定这个 Agent 能用哪些工具
```

**安全设计**：默认不给危险工具（如 code.execute），按需配置。

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| Function Calling | LLM 输出"调什么工具+参数"，你的代码执行 |
| @AgentTool | 普通方法加注解就变工具 |
| description | 最重要——LLM 靠它判断何时用这个工具 |
| 自动扫描 | EnhancedToolManager 反射扫描 @AgentTool |
| 工具白名单 | Agent 可配置只允许用某些工具 |

> 🚀 [C02 — @AgentTool 注解 →](C02-agenttool-annotation.md)

---

📝 **本篇撰写期间修正的代码**：无。
