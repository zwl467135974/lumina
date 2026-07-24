# E05 — 跨实例状态共享：AgentStateStore

> **前置要求**：已完成 [E01 短期记忆](E01-short-term-memory.md) ~ [E04 会话生命周期](E04-conversation-lifecycle.md)
> **预计阅读**：20 分钟
> **难度**：⭐⭐⭐⭐☆

---

## 这节解决什么问题

Lumina 支持多实例部署——多个后端节点共同服务用户。但 Agent 的记忆怎么办？

- 实例 A 执行了第一轮对话 → 记忆存在实例 A 的内存里
- 用户第二次请求被负载均衡到实例 B → 实例 B 的内存里没有第一轮的记忆 → Agent 失忆

**AgentStateStore** 就是解决这个问题的：把 Agent 的完整状态（对话历史 + Token 用量 + 会话元数据）持久化到 Redis，任何实例都能读取。

> **这是 AgentScope 2.0 最大的架构改进之一**——1.0.7 时代记忆在 Agent 实例内部（InMemoryMemory），2.0 将其抽到外部 Store，天然支持多实例。

---

## 先建立直觉：共享笔记本

E01 讲的短期记忆像**便签纸**——贴在自己桌上的，别人看不到。

AgentStateStore 像**共享笔记本**——放在公共区域（Redis），任何人都能翻看、续写：

```
实例 A: 用户问 "我叫张三"
  → Agent 回复 "你好张三"
  → AgentState.toJson() → 存入 Redis

实例 B: 用户问 "我叫什么？"
  → AgentState.fromJsonString() → 从 Redis 读取
  → Agent 看到历史: "用户叫张三"
  → 回复 "你叫张三"
```

---

## AgentScope 2.0 的 AgentState

### 什么是 AgentState

AgentState 是 AgentScope 2.0 中 Agent 的**完整运行时状态快照**，包含：

```json
{
  "session_id": "b47850e1-...",
  "user_id": null,
  "summary": "",
  "context": [
    { "role": "USER",      "content": [{"type":"text","text":"What is 3+5?"}] },
    { "role": "ASSISTANT", "content": [{"type":"text","text":"3+5=8"}] },
    { "role": "USER",      "content": [{"type":"text","text":"What about 7*8?"}] },
    { "role": "ASSISTANT", "content": [{"type":"text","text":"7*8=56"}] }
  ],
  "usage": { "input_tokens": 1234, "output_tokens": 56 }
}
```

关键设计：AgentState 内置了 `toJson()` / `fromJsonString()` 序列化方法——任何 Store 实现都只需要处理字符串。

### AgentStateStore 接口

```java
// AgentScope 2.0 SDK 内置接口
public interface AgentStateStore {
    // 保存（每次 Agent 执行后自动调用）
    void save(String userId, String sessionId, String key, State state);

    // 读取（Agent 初始化时自动调用）
    <T extends State> Optional<T> get(String userId, String sessionId, String key, Class<T> type);

    // 删除（会话结束时调用）
    void delete(String userId, String sessionId);

    // 检查是否存在
    boolean exists(String userId, String sessionId);
}
```

---

## Lumina 的 Redis 实现

### RedisAgentStateStore

```java
// 文件：lumina-agent-core/.../memory/RedisAgentStateStore.java
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisAgentStateStore implements AgentStateStore {

    private final RedisCacheManager redisCacheManager;

    private static final String KEY_PREFIX = "lumina:agent:state:";
    private static final Duration TTL = Duration.ofDays(7);  // 7 天过期
    private static final String DEFAULT_STATE_KEY = "agent_state";

    // Key 格式: lumina:agent:state:{userId}:{sessionId}:{stateKey}
    private String buildKey(String userId, String sessionId, String key) {
        return KEY_PREFIX + userId + ":" + sessionId + ":"
                + (key != null ? key : DEFAULT_STATE_KEY);
    }

    @Override
    public void save(String userId, String sessionId, String key, State state) {
        if (state instanceof AgentState agentState) {
            String json = agentState.toJson();          // AgentScope 内置序列化
            redisCacheManager.set(buildKey(userId, sessionId, key), json, TTL);
        }
    }

    @Override
    public <T extends State> Optional<T> get(String userId, String sessionId,
                                              String key, Class<T> type) {
        String json = redisCacheManager.get(buildKey(userId, sessionId, key));
        if (json == null) return Optional.empty();
        return Optional.of(AgentState.fromJsonString(json));  // 反序列化
    }
}
```

### 为什么用 Redis 而不是 MySQL

| 维度 | Redis | MySQL |
|------|-------|-------|
| 读写延迟 | ~1ms | ~5-10ms |
| 序列化 | 原生字符串 | 需要 JSON 列 + ORM 映射 |
| TTL 过期 | 原生支持 | 需要定时清理 |
| 多实例共享 | ✅ 天然支持 | ✅ 但更重 |

AgentState 可能很大（多轮对话 + Token 统计），但 Redis 的内存开销是可接受的——7 天 TTL 自动清理，且单个 AgentState 通常只有几 KB。

---

## 注入到 ReActAgent

引擎层在创建 Agent 时注入 StateStore：

```java
// 文件：lumina-agent-core/.../engine/impl/DefaultAgentExecutionEngine.java:869-889
ReActAgent.Builder agentBuilder = ReActAgent.builder()
        .name(config.getAgentName())
        .sysPrompt(config.getPromptTemplate())
        .model(model)
        .toolkit(toolkit);

// 注入 Redis AgentStateStore
if (redisAgentStateStore != null) {
    agentBuilder.stateStore(redisAgentStateStore);
    String sessionId = BaseContext.getConversationId();
    if (sessionId != null) {
        agentBuilder.defaultSessionId(sessionId);  // 绑定到当前会话
    }
}

return agentBuilder.build();
```

注入后，AgentScope 内部自动处理：
1. **Agent 初始化时** → 调用 `stateStore.get()` 从 Redis 加载历史状态
2. **Agent 执行完每一步** → 调用 `stateStore.save()` 更新 Redis
3. **下一轮对话** → 新的 Agent 实例从 Redis 读取之前的状态

---

## 与 MemoryManager 的关系

Lumina 有两套记忆机制，分工不同：

```
┌──────────────────────────────────────────────────┐
│              Agent 执行流程                        │
│                                                   │
│  1. buildContextMessages()                        │
│     ├── MemoryManager.getRecentMemories()         │  ← Redis List（Lumina 自研）
│     │   取最近 N 条消息（滑动窗口）                  │    用于构建 LLM 的 messages 数组
│     └── 注入到 contextMessages                     │
│                                                   │
│  2. agent.call(contextMessages)                   │
│     └── AgentScope 内部：                          │
│         ├── stateStore.get()   ← 读取 AgentState  │  ← Redis String（AgentScope 2.0）
│         │   完整历史（含 Token 用量）                │    用于 Agent 内部状态管理
│         ├── 执行 ReAct 循环                         │
│         └── stateStore.save()  ← 保存 AgentState  │
│                                                   │
│  3. memoryManager.addMemory()                     │
│     存当前轮的 user/assistant 消息                  │  ← Redis List
└──────────────────────────────────────────────────┘
```

| 机制 | 数据结构 | 用途 | 管理者 |
|------|---------|------|--------|
| MemoryManager | Redis List | 滑动窗口构建 LLM messages | Lumina 引擎层 |
| AgentStateStore | Redis String (JSON) | Agent 完整状态（含 context + usage） | AgentScope SDK 内部 |

两者**互补不冲突**：MemoryManager 控制"给 LLM 看多少历史"，AgentStateStore 负责"Agent 自身状态跨实例不丢"。

---

## 多轮上下文验证

实际测试中，同一 conversationId 的多轮对话：

```
第 1 轮: "Hello, what is 3+5?"
  → AgentState 保存到 Redis，context 有 2 条消息

第 2 轮: "What was the math question I just asked?"
  → AgentState 从 Redis 读取（context 已有历史）
  → Agent 回忆起 "3+5" ← 跨轮记忆生效！

Redis 中 context 从 2 条增长到 4 条（每轮 +USER +ASSISTANT）
```

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| AgentState | Agent 完整运行时状态（context + usage + summary） |
| AgentStateStore | AgentScope 2.0 的状态持久化接口（save/get/delete） |
| RedisAgentStateStore | Lumina 的 Redis 实现，Key = `lumina:agent:state:{userId}:{sessionId}:{key}` |
| 跨实例共享 | 多实例部署时，任何节点都能从 Redis 读取 Agent 状态 |
| vs MemoryManager | StateStore 管 Agent 自身状态；MemoryManager 管给 LLM 看的窗口 |

### 自测题

1. AgentScope 1.0.7 的 `.memory()` 和 2.0 的 `.stateStore()` 有什么本质区别？
   <details><summary>答案</summary>.memory() 是 Agent 内部的 InMemoryMemory，实例间不共享；.stateStore() 是外部 Store 接口，可存 Redis 实现跨实例共享。</details>

2. RedisAgentStateStore 的 Key 格式是什么？为什么用 `:` 分隔？
   <details><summary>答案</summary>Key = lumina:agent:state:{userId}:{sessionId}:{stateKey}。用 : 分隔是 Redis 的命名惯例（可视化工具按 : 分层展示）。</details>

3. AgentState 和 MemoryManager 管理的记忆有什么分工差异？
   <details><summary>答案</summary>MemoryManager 管"给 LLM 看多少历史"（滑动窗口构建 messages）；AgentStateStore 管"Agent 自身状态跨实例不丢"（完整 context + usage）。</details>

4. 多实例部署时，如果没有 AgentStateStore 会发生什么？
   <details><summary>答案</summary>实例 A 执行第一轮后记忆存在 A 内存里；用户第二次请求被负载均衡到实例 B，B 没有第一轮记忆，Agent 失忆。</details>

5. 为什么 TTL 设为 7 天而不是永久？
   <details><summary>答案</summary>对话数据有时效性，7 天前的对话通常不再需要。永久存储会导致 Redis 内存无限增长。</details>

> 🚀 [F01 — 流式输出 →](F01-streaming-sse.md)

---

📝 **本篇撰写期间修正的代码**：无（代码已在 AgentStateStore 开发期间全部完成，本篇是教学同步）。
