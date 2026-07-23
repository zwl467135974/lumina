# C03 — 内置工具系统

> **前置要求**：已完成 [C02-@AgentTool 注解](C02-agenttool-annotation.md)
> **预计阅读**：10 分钟
> **难度**：⭐⭐☆☆☆

---

## 这节解决什么问题

Lumina 自带 4 个通用工具。这节快速过一遍它们的功能和用法。

---

## 四个内置工具

| 工具名 | 功能 | 分类 | 实现位置 |
|--------|------|------|----------|
| `util.getCurrentTime` | 获取当前时间 | util.time | GeneralToolProvider |
| `util.webSearch` | 网络搜索 | util.search | GeneralToolProvider |
| `util.calculate` | 数学计算 | util.math | GeneralToolProvider |
| `util.httpRequest` | HTTP 请求 | util.http | GeneralToolProvider |

> 💡 文件位置：`lumina-agent-core/.../tool/GeneralToolProvider.java`

---

## getCurrentTime（获取时间）

```java
@AgentTool(name = "util.getCurrentTime",
    description = "获取当前日期和时间。返回标准格式的时间字符串和Unix时间戳。")
public Map<String, Object> getCurrentTime() {
    LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
    return Map.of(
        "datetime", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
        "timezone", "Asia/Shanghai (UTC+8)",
        "timestamp", now.atZone(ZoneId.of("Asia/Shanghai")).toEpochSecond()
    );
}
```

**为什么需要**：LLM 不知道实时时间（训练数据有截止日期）。

## webSearch（网络搜索）

```java
@AgentTool(name = "util.webSearch",
    description = "搜索互联网获取实时信息。输入搜索关键词，返回标题、链接和摘要。")
public Map<String, Object> webSearch(String query) {
    // 支持智谱/Tavily/SerpAPI/Brave 四种搜索引擎
    // 通过 lumina.agent.search.provider 配置选择
}
```

**为什么需要**：LLM 不知道实时信息。

## calculate（数学计算）

```java
@AgentTool(name = "util.calculate",
    description = "执行数学表达式计算。支持四则运算、三角函数、对数等。")
```

**为什么需要**：LLM 算数经常错（它是文本预测器不是计算器）。

## httpRequest（HTTP 请求）

```java
@AgentTool(name = "util.httpRequest",
    description = "发起 HTTP 请求，调用外部 API。")
```

**为什么需要**：让 Agent 能调外部系统（如查订单、发通知）。

---

## 小结

四个内置工具覆盖了最常见的"LLM 不会做的事"：看时间、搜网络、算数学、调 API。

> 🚀 [C04 — MCP 协议 →](C04-mcp-protocol.md)

---

📝 **本篇撰写期间修正的代码**：无。
