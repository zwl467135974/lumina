# E07 — 冷启记忆恢复：Redis 丢了也不怕

> **前置要求**：已完成 [E01 短期记忆](E01-short-term-memory.md) ~ [E06 上下文压缩](E06-context-compression.md)
> **预计阅读**：15 分钟
> **难度**：⭐⭐⭐⭐☆

---

## 这节解决什么问题

E01 讲了短期记忆存在 Redis 里（7 天 TTL）。但如果 Redis 重启、内存满了被淘汰、或者 TTL 到期了——Agent 就**失忆**了，即使每条消息都安全存在 MySQL `lumina_message` 表里。

**这是隐性数据丢失 bug**：用户和 Agent 聊了 3 天，第 4 天 Redis 过期了，Agent 忘了之前所有对话。这不是功能缺失——数据明明在数据库里，只是没去读。

---

## 先建立直觉：手机通讯录同步

你的手机通讯录存在两个地方：
- **本地缓存**（Redis）：打开通讯录秒开，但换手机就没了
- **云端备份**（MySQL）：永久保存，但读取稍慢

换手机时（冷启动），通讯录自动从云端恢复到本地——你不会因为换了手机就丢了所有联系人。

Lumina 的冷启记忆恢复就是这个机制：Redis 空了时自动从 MySQL 恢复。

---

## 问题根因

```java
// 文件：lumina-agent-core/.../manager/MemoryManager.java（修复前）
private List<Memory> getMemoriesFromRedis(String sessionId) {
    List<?> rawList = redisCacheManager.getList(key);

    if (rawList == null || rawList.isEmpty()) {
        return new ArrayList<>();  // ← 直接返回空列表！不查 DB！
    }
    // ...
}
```

Redis 为空 → 返回空列表 → Agent 认为没有历史 → 失忆。

---

## 解决方案：端口接口 + Warm-up

### 架构

```
getMemories(sessionId)
    │
    ▼ 查 Redis
    ├── Redis 有数据 → 直接返回 ✅（正常路径，毫秒级）
    │
    └── Redis 为空 → 冷启恢复
            │
            ▼ 调用 ColdStartMemoryLoader（端口接口）
            │
            ├── DbColdStartMemoryLoader 实现
            │   ├── ConversationService.listMessages(uuid, 1, limit)
            │   ├── MessageDO → Memory 转换（role + content + createTime→timestamp）
            │   └── 返回 List<Memory>
            │
            ▼ Warm-up：回填 Redis
            │   for (Memory m : dbMemories) {
            │       addMemoryToRedis(sessionId, m);  // 回填
            │   }
            │
            ▼ 返回 DB 加载的记忆
            下次请求 Redis 直接命中 ✅
```

### 端口接口模式

为什么用接口而不是直接在 MemoryManager 里查 DB？因为**模块依赖方向**：

```
lumina-agent-core（MemoryManager 所在）
    ↑ 依赖
lumina-business-agent（ConversationService/MessageDO 所在）
```

`lumina-agent-core` 不能引用 `lumina-business-agent` 的类（会循环依赖）。所以：

```java
// 文件：lumina-agent-core/.../service/ColdStartMemoryLoader.java
// 接口定义在 core 模块
public interface ColdStartMemoryLoader {
    List<MemoryManager.Memory> loadFromDb(String conversationUuid, int limit);
}
```

```java
// 文件：lumina-business-agent/.../service/impl/DbColdStartMemoryLoader.java
// 实现在 business 模块（可以访问 ConversationService + MessageDO）
@Service
public class DbColdStartMemoryLoader implements ColdStartMemoryLoader {
    private final ConversationService conversationService;

    @Override
    public List<MemoryManager.Memory> loadFromDb(String conversationUuid, int limit) {
        PageResult<MessageDO> result = conversationService.listMessages(conversationUuid, 1, limit);
        // MessageDO → Memory 转换
        return result.getList().stream()
                .map(msg -> new MemoryManager.Memory(
                        msg.getRole(), msg.getContent(),
                        msg.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()))
                .toList();
    }
}
```

### 循环依赖处理

`MemoryManager` → `ColdStartLoader` → `ConversationService` → `MemoryManager`（循环！）。

用 `@Lazy` 打破：

```java
// 文件：MemoryManager.java
@Autowired(required = false)
@Lazy  // ← 关键：延迟注入，打破循环依赖
private ColdStartMemoryLoader coldStartLoader;
```

### MemoryManager 改造

```java
// 文件：MemoryManager.java（修复后）
private List<Memory> getMemoriesFromRedis(String sessionId) {
    List<?> rawList = redisCacheManager.getList(key);

    if (rawList == null || rawList.isEmpty()) {
        // Redis 为空 → 冷启恢复：从 DB 加载 + 回填 Redis
        return loadFromDbAndWarmUp(sessionId);  // ← 新增
    }
    // ...正常 Redis 路径
}

private List<Memory> loadFromDbAndWarmUp(String sessionId) {
    if (coldStartLoader == null) return new ArrayList<>();  // standalone 模式无 DB

    List<Memory> dbMemories = coldStartLoader.loadFromDb(sessionId, MAX_MEMORY_SIZE);
    if (dbMemories.isEmpty()) return dbMemories;

    // Warm-up：回填 Redis，下次直接命中
    for (Memory m : dbMemories) {
        addMemoryToRedis(sessionId, m);
    }
    log.info("冷启记忆 warm-up: 回填 {} 条到 Redis", dbMemories.size());
    return dbMemories;
}
```

---

## 端到端验证结果

```
步骤1: Redis 有 6 条记忆（正常状态）
步骤2: DEL lumina:agent:memory:{uuid}（模拟 Redis 过期/重启）
步骤3: 执行 Agent → 触发 getMemories → 发现 Redis 空
步骤4: 自动从 DB 加载 7 条消息
步骤5: Warm-up 回填 Redis 7 条
步骤6: Agent 正常执行（有上下文，没失忆）

日志：
冷启记忆恢复: conversationUuid=b47850e1..., 从 DB 加载 7 条消息
冷启记忆 warm-up 完成: sessionId=b47850e1..., 回填 7 条到 Redis
```

---

## 设计要点

| 决策 | 原因 |
|------|------|
| 端口接口（不直接查 DB） | 模块依赖方向：core 不能引用 business |
| `@Lazy` 注入 | 打破 MemoryManager↔ConversationService 循环依赖 |
| Warm-up 回填 | 只冷启一次，后续直接命中 Redis（不需要每次查 DB） |
| `@Autowired(required=false)` | standalone 模式（无 DB）时 MemoryManager 仍可用 |
| 对调用方透明 | DefaultAgentExecutionEngine 无需改动 |

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| 冷启恢复 | Redis 空时从 MySQL lumina_message 表加载历史 |
| 端口接口 | ColdStartMemoryLoader 在 core 定义，DbColdStartMemoryLoader 在 business 实现 |
| Warm-up | 加载后回填 Redis，下次直接命中 |
| @Lazy | 打破循环依赖（MemoryManager↔ConversationService） |

### 自测题

1. 为什么 MemoryManager 不能直接查 MySQL？
   <details><summary>答案</summary>模块依赖方向：lumina-agent-core 不能引用 lumina-business-agent 的类（ConversationService/MessageDO），否则循环依赖。用端口接口模式解决。</details>

2. Warm-up 回填后，下次请求还会查 DB 吗？
   <details><summary>答案</summary>不会。Warm-up 把 DB 数据回填到 Redis，后续请求 Redis 直接命中。冷启恢复只发生在 Redis 为空时（首次访问或过期后）。</details>

3. `@Lazy` 在这里解决什么问题？
   <details><summary>答案</summary>MemoryManager 注入 ColdStartLoader → ColdStartLoader 注入 ConversationService → ConversationService 注入 MemoryManager，形成循环。@Lazy 让 Spring 延迟创建 ColdStartLoader 的代理，打破初始化时的循环。</details>

4. 对调用方（DefaultAgentExecutionEngine）透明是什么意思？
   <details><summary>答案</summary>引擎层只调 memoryManager.getRecentMemories()，不需要知道冷启恢复的存在。恢复逻辑完全封装在 MemoryManager 内部。</details>

> 🚀 返回 [AI 专项导读](README.md)

---

📝 **本篇撰写期间修正的代码**：无（冷启记忆恢复代码为 v3.9.0 新增）。
