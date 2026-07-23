# C05 — MCP 三种传输方式

> **前置要求**：已完成 [C04-MCP 协议](C04-mcp-protocol.md)
> **预计阅读**：10 分钟
> **难度**：⭐⭐☆☆☆

---

## 这节解决什么问题

MCP Client 和 Server 之间怎么通信？Lumina 支持三种传输方式，各有适用场景。

---

## 三种传输方式对比

| 传输方式 | 通信机制 | 适用场景 | 示例 |
|----------|----------|----------|------|
| **stdio** | 标准输入输出 | 本地命令行工具 | `npx @modelcontextprotocol/server-filesystem` |
| **http (SSE)** | HTTP + Server-Sent Events | 远程 Server（单向流） | 旧版远程 MCP Server |
| **streamable-http** | HTTP 双向流 | 远程 Server（新版推荐） | `https://mcp.github.com/sse` |

### stdio（标准输入输出）

```
Lumina 进程 ──启动子进程──► MCP Server（如 npx ...）
            ◄──stdout── 工具结果
            ──stdin──► 工具调用
```

**适合**：本地工具（文件系统、shell 命令）。Lumina 启动一个子进程，通过标准输入输出通信。

### http / streamable-http（网络）

```
Lumina ──HTTP POST──► 远程 MCP Server
       ◄──SSE 流── 工具结果
```

**适合**：远程工具（GitHub、数据库 API）。通过网络通信，支持鉴权（headers）。

---

## 配置示例

```yaml
lumina:
  mcp:
    servers:
      # stdio 方式：本地文件系统
      - name: filesystem
        transport: stdio
        command: npx
        args: ["-y", "@modelcontextprotocol/server-filesystem", "/tmp"]

      # streamable-http 方式：远程 GitHub
      - name: github
        transport: streamable-http
        url: https://mcp.github.com/sse
        headers:
          Authorization: "Bearer ghp_xxx"
```

---

## 健康检查与重连

```java
// 文件：lumina-agent-core/.../tool/mcp/McpClientRegistry.java
// MCP Server 可能断线，Lumina 自动重连 + 健康检查

public boolean checkHealth(String serverName) {
    // 调 listTools ping 检查存活
}

public boolean reconnect(String serverName) {
    // 指数退避重连
}
```

---

## 小结

| 传输 | 机制 | 场景 |
|------|------|------|
| stdio | 子进程 stdin/stdout | 本地工具 |
| http | HTTP + SSE | 远程（旧版） |
| streamable-http | HTTP 双向流 | 远程（推荐） |

---

## 🎉 模块 C 完成

你已经学完了工具系统（C01-C05），现在你懂得：
- Function Calling 协议（LLM 输出指令，代码执行）
- @AgentTool 注解（普通方法变工具）
- 内置工具（时间/搜索/计算/HTTP）
- MCP 协议（USB 式统一标准）
- 三种传输方式

---

## 下一步

进入 **[模块 D：RAG 知识库](README.md)**（8 篇）——重点中的重点！从"开卷考试"类比理解 RAG 全链路。

> 🚀 [D01 — RAG 从零理解 →](D01-rag-from-scratch.md)

---

📝 **本篇撰写期间修正的代码**：无。
