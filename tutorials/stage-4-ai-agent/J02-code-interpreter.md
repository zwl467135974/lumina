# J02 — Code Interpreter 代码沙箱

> **前置要求**：已完成 [J01-Webhook](J01-webhook-wechat-bot.md)
> **预计阅读**：10 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

Agent 遇到计算/数据分析任务时，让 LLM"写代码并运行"。但运行代码有安全风险——**沙箱隔离**保证它搞不坏宿主机。

---

## 两种模式

```yaml
lumina:
  agent:
    code-interpreter:
      mode: process    # process（本地进程）/ docker（Docker 隔离）
```

### process 模式（简单）

```java
// 文件：CodeInterpreterToolProvider.java
// ProcessBuilder 启动 python3/java 进程执行代码
ProcessBuilder pb = new ProcessBuilder("python3", scriptFile);
Process process = pb.start();
process.waitFor(10, TimeUnit.SECONDS);    // 10 秒超时
```

**风险**：代码在宿主机直接跑——不安全。

### docker 模式（安全）

```java
// Docker 容器里执行，资源隔离
// - 内存限制：256MB
// - CPU 限制：1 核
// - 禁网络
// - 容器池复用（不用每次启动新容器）
```

---

## @AgentTool 注解

```java
@AgentTool(
    name = "code.execute",
    description = "执行 Python/JavaScript 代码。用于数学计算、数据分析。",
    category = "code"
)
public Map<String, Object> execute(String language, String code) { ... }
```

---

## 小结

| 模式 | 安全 | 适合 |
|------|------|------|
| process | 低 | 开发/演示 |
| docker | 高（资源限制+禁网络） | 生产 |

> 🚀 [J03 — Lumina AI 架构全景 →](J03-lumina-ai-architecture.md)

---

📝 **本篇撰写期间修正的代码**：无。
