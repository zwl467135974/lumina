# C04 — MCP 协议：统一工具接入标准

> **前置要求**：已完成 [C03-内置工具](C03-built-in-tools.md)
> **预计阅读**：20 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

每接一个新工具（如 GitHub、文件系统、数据库），你都要写一个 `@AgentTool` 方法。工具有 100 个就要写 100 个？

**MCP（Model Context Protocol）** 解决这个问题——一个标准协议，装一个 MCP Server，所有支持 MCP 的 AI 客户端都能用它提供的工具。

---

## MCP 是什么？先建立直觉

### 类比：USB 标准

以前每个设备都有自己的接口（键盘 PS/2、鼠标串口、打印机并口……）。后来有了 **USB**——一个标准接口，所有设备都能插。

**MCP 就是 AI 工具的 USB 标准**。你装一个 MCP Server（如 GitHub MCP Server），任何支持 MCP 的 AI 客户端（Claude、Lumina、其他）都能用它，不用每个客户端单独写对接。

---

## MCP 的架构

```
AI 客户端（Lumina Agent）
    │
    │ MCP 协议
    ▼
MCP Server（提供工具）
    │
    ├── GitHub MCP Server → 提供 GitHub 操作工具
    ├── 文件系统 MCP Server → 提供文件读写工具
    └── 数据库 MCP Server → 提供 SQL 查询工具
```

### 三个角色

| 角色 | 职责 | 类比 |
|------|------|------|
| MCP Client（客户端） | 发起工具调用 | 你（用工具的人） |
| MCP Server（服务端） | 提供工具 | 工具箱 |
| MCP 协议 | 通信标准 | USB 接口标准 |

---

## 在 Lumina 里怎么接入 MCP

### 配置 MCP Server

```yaml
# application.yml
lumina:
  mcp:
    enabled: true
    servers:
      - name: filesystem
        transport: stdio                    # 传输方式
        command: npx                        # 启动命令
        args: ["-y", "@modelcontextprotocol/server-filesystem", "/tmp"]
      - name: github
        transport: streamable-http          # 传输方式
        url: https://mcp.github.com/sse
        headers:
          Authorization: "Bearer ghp_xxx"    # 鉴权头
```

### 启动时自动注册

```java
// 文件：lumina-agent-core/.../tool/mcp/McpToolRegistrar.java
// 启动时对每个 MCP Server：
// 1. 连接 MCP Server
// 2. 调 listTools() 获取工具清单
// 3. 把每个工具转成 ToolDefinition 注册到 EnhancedToolManager
// → Agent 就能像用本地工具一样调用了
```

**效果**：Agent 调 `mcp.filesystem.read_file` 和调 `util.getCurrentTime` 完全一样——**Agent 不关心工具是本地的还是远程的**。

---

## 运行时动态注册

Lumina v3.6 支持运行时注册新的 MCP Server（不重启）：

```java
// 文件：McpController.java
@PostMapping("/servers")
public R<Boolean> registerServer(@RequestBody McpServerConfig config) {
    boolean success = clientRegistry.registerServer(config);
    if (success) {
        // 注册成功后自动拉取工具并注册
        int toolCount = toolRegistrar.registerToolsFromServer(config.getName(), client);
    }
    return success ? R.success(true) : R.fail("注册失败");
}
```

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| MCP | AI 工具的 USB 标准 |
| MCP Server | 提供工具的服务（GitHub/文件系统/数据库...） |
| MCP Client | Lumina 作为客户端，连接 Server 获取工具 |
| 自动注册 | 启动时 listTools → 转 ToolDefinition → 注册 |
| 运行时注册 | 不重启动态加新 MCP Server |

> 🚀 [C05 — MCP 传输方式 →](C05-mcp-transports.md)

---

📝 **本篇撰写期间修正的代码**：无。
