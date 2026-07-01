---
name: lumina_conversation
description: Use this skill when working with Agent conversations, memory, or streaming output. Covers conversationId lifecycle, MemoryManager dual-track memory (Redis hot + DB cold), and executeStream context loading.
---

# Lumina 会话与记忆管理规范

## 功能概述

本技能包用于规范 Lumina 框架的 Agent 多轮对话、会话记忆管理和流式输出，涵盖 conversationId 生命周期、MemoryManager 双轨记忆（Redis 热记忆 + DB 冷存储）、executeStream 上下文加载机制。

## 会话模型

### 数据结构

| 表 | 说明 | 迁移版本 |
|----|------|---------|
| `lumina_conversation` | 会话主表（UUID、标题、Agent 关联） | Flyway V3 |
| `lumina_message` | 消息记录（角色、内容、会话关联） | Flyway V3 |

### conversationId 生命周期

1. **创建**：调用 `POST /api/v1/conversations`，生成 UUID 作为 conversationId
2. **多轮交互**：每次 Agent 执行传入 conversationId，历史消息自动加载为上下文
3. **持久化**：所有 user/assistant 消息持久化到 `lumina_message` 表
4. **回放**：通过 conversationId 查询历史消息，支持前端会话列表和历史回放

## 双轨记忆机制

### Redis 热记忆（MemoryManager）

```java
@Autowired
private MemoryManager memoryManager;

// 加载上下文（从 Redis 热记忆）
List<Msg> context = memoryManager.loadMessages(conversationId);

// 保存消息（写入 Redis 热记忆）
memoryManager.saveMessage(conversationId, Msg.user(task));
memoryManager.saveMessage(conversationId, Msg.assistant(response));
```

| 属性 | 值 |
|------|-----|
| 存储 | Redis |
| 上下文窗口 | `CONTEXT_WINDOW = 20` 条最近消息 |
| 特性 | 低延迟读取，自动滑动窗口裁剪 |
| 用途 | Agent 执行时快速加载对话上下文 |

### DB 冷存储

| 属性 | 值 |
|------|-----|
| 存储 | MySQL `lumina_message` 表 |
| 特性 | 永久持久化，全量消息记录 |
| 用途 | 历史回放、审计、分析 |

### 双轨同步

- 消息同时写入 Redis（热）和 DB（冷）
- Redis 超过窗口自动裁剪（仅保留最近 20 条）
- DB 始终保留全量消息
- Redis 数据丢失时，可从 DB 重建热记忆

## 引擎接入

### 方法签名

```java
// 异步执行
Result<AgentResponse> execute(AgentExecutionRequest request);

// 同步执行
Result<AgentResponse> executeSync(AgentExecutionRequest request);

// 流式执行
SseEmitter executeStream(AgentExecutionRequest request);
```

`AgentExecutionRequest` 包含 `conversationId` 字段，用于关联会话上下文。

### 上下文加载流程

```
1. 接收 conversationId
2. buildContextMessages(conversationId)
   → 从 MemoryManager 加载历史消息（Redis 热记忆，最多 20 条）
   → 映射为 Msg 列表（MsgRole 映射：USER → user, ASSISTANT → assistant, SYSTEM → system）
3. 将当前用户输入追加到 Msg 列表
4. 执行 Agent（传入完整 Msg 列表）
5. 执行完成后，保存 user 消息和 assistant 响应到双轨记忆
```

### MsgRole 映射

| 数据库角色 | AgentScope MsgRole | 说明 |
|-----------|-------------------|------|
| `USER` | `MsgRole.USER` | 用户消息 |
| `ASSISTANT` | `MsgRole.ASSISTANT` | Agent 响应 |
| `SYSTEM` | `MsgRole.SYSTEM` | 系统提示词 |

## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/conversations` | 创建会话 |
| GET | `/api/v1/conversations` | 会话列表 |
| GET | `/api/v1/conversations/{uuid}/messages` | 获取历史消息 |
| POST | `/api/v1/agents/{id}/execute/stream?task=&conversationId=` | 多轮流式执行 |

### 流式执行示例

```
POST /api/v1/agents/{agentId}/execute/stream?task=你好&conversationId=abc-123
Accept: text/event-stream

→ data: {"chunk": "你"}
→ data: {"chunk": "好"}
→ data: {"done": true, "messageId": 456}
```

- 使用 SSE（Server-Sent Events）推送流式响应
- 响应完成后返回最终 messageId 和完整响应

## 前端集成

### AgentChat 组件

- **会话列表**：展示历史会话，支持切换、新建、删除
- **消息回放**：加载 `GET /api/v1/conversations/{uuid}/messages` 回显历史
- **流式渲染**：SSE 接收 chunk，逐字渲染 Agent 响应
- **多轮对话**：每次发送携带当前 conversationId，保持上下文连续

## 最佳实践

1. **conversationId 传递**：多轮对话必须传入 conversationId，否则上下文断裂
2. **双轨写入**：消息同时写 Redis 和 DB，确保数据一致性
3. **窗口管理**：Redis 热记忆自动裁剪，不要手动管理窗口大小
4. **流式异常处理**：SSE 连接断开时，已生成的内容仍需持久化
5. **消息顺序**：按时间戳排序，保证对话逻辑连贯
