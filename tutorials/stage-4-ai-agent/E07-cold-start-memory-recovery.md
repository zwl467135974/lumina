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
            ├── DbColdStartMemoryLoader 实现（Mapper-based）
            │   ├── ConversationMapper.selectOne(uuid)         // uuid → conversationId
            │   ├── MessageMapper.selectList(                   // 取最近 N 条
            │   │       eq(conversationId)                      //   orderByDesc(createTime)
            │   │       .orderByDesc(createTime)                //   .last("LIMIT N")
            │   │       .last("LIMIT N"))
            │   ├── Collections.reverse(recent)                 // DESC → ASC（冷启动语义）
            │   ├── MessageDO → Memory 转换（role + content + createTime→timestamp）
            │   └── 返回 List<Memory>
            │
            ▼ Warm-up：批量回填 Redis（3 次往返）
            │   redisCacheManager.pushAllToList(key, dbMemories)  // ① 一次 RPUSH 全部
            │   redisCacheManager.trimList(key, -MAX, -1)         // ② 一次 LTRIM
            │   redisCacheManager.expire(key, ttl)                // ③ 一次 EXPIRE
            │
            ▼ 返回 DB 加载的记忆
            下次请求 Redis 直接命中 ✅
```

> **两个关键语义**（v3.10 修正）：
> 1. **取最近 N 条，不是最早 N 条**：`orderByDesc(createTime).last("LIMIT N")` 取最近，再 `Collections.reverse()` 转升序。冷启动的目的是恢复"最近的对话上下文"，不是从头回放。
> 2. **批量 warm-up，不是逐条回填**：100 条记忆只要 3 次 Redis 往返，而不是逐条 `addMemoryToRedis` 的 300 次（其中 200 次 trim/expire 完全冗余）。

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
// 实现在 business 模块（直接用 Mapper，不经过 ConversationService）
@Service
@RequiredArgsConstructor
public class DbColdStartMemoryLoader implements ColdStartMemoryLoader {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;

    @Override
    public List<MemoryManager.Memory> loadFromDb(String conversationUuid, int limit) {
        // 1. uuid → conversationId
        ConversationDO conv = conversationMapper.selectOne(
                new LambdaQueryWrapper<ConversationDO>()
                        .eq(ConversationDO::getConversationUuid, conversationUuid));
        if (conv == null) return List.of();

        // 2. 取最近 N 条（DESC），再反转为时间升序——这是冷启动语义
        List<MessageDO> recent = messageMapper.selectList(
                new LambdaQueryWrapper<MessageDO>()
                        .eq(MessageDO::getConversationId, conv.getConversationId())
                        .orderByDesc(MessageDO::getCreateTime)
                        .last("LIMIT " + Math.max(1, limit)));
        if (recent.isEmpty()) return List.of();
        Collections.reverse(recent); // DESC → ASC

        // 3. MessageDO → Memory 转换
        List<MemoryManager.Memory> memories = new ArrayList<>(recent.size());
        for (MessageDO msg : recent) {
            long ts = msg.getCreateTime() != null
                    ? msg.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    : System.currentTimeMillis();
            memories.add(new MemoryManager.Memory(msg.getRole(), msg.getContent(), ts));
        }
        return memories;
    }
}
```

> **为什么不走 `ConversationService`？**
> 历史版本曾让 Loader 依赖 `ConversationService.listMessages(uuid, 1, limit)`，但那条链路是按"页码升序"取最早 N 条（语义错），且会再次把 `MemoryManager` 拉进依赖图。v3.10 改为直接用 `ConversationMapper` + `MessageMapper`：
> - 语义正确：可以 `orderByDesc(createTime)` 取最近 N 条；
> - 依赖更干净：Loader 只依赖 Mapper，不再经过 Service 层。

### 循环依赖处理（历史 → 现状）

**历史版本**确实存在循环：`MemoryManager` → `ColdStartLoader` → `ConversationService` → `MemoryManager`。

**v3.10 之后**这条链已经断了：`DbColdStartMemoryLoader` 改为直接注入 `ConversationMapper` + `MessageMapper`，**不再依赖 `ConversationService`**，循环依赖从根上消除。

但 `@Lazy` 仍然保留，作为**防御性措施**——将来若 Loader 再被加上对 Service 层的依赖，`@Lazy` 能兜底，避免启动期初始化失败：

```java
// 文件：MemoryManager.java
@Autowired(required = false)
@Lazy  // 防御性保留：历史上曾用于打破 Loader→Service→MemoryManager 循环；现为兜底
private ColdStartMemoryLoader coldStartLoader;
```

> **教训**：直接消灭循环依赖（重构依赖方向）永远优于用 `@Lazy` 掩盖循环。`@Lazy` 应当是"最后的兜底"，而不是"懒得改依赖"的借口。

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

    // Warm-up：批量回填 Redis（3 次往返），下次直接命中
    // 注意：不逐条 addMemoryToRedis，否则 100 条会打 300 次 Redis 调用
    try {
        String key = getRedisKey(sessionId);
        redisCacheManager.pushAllToList(key, dbMemories);     // ① 一次 RPUSH 全部
        redisCacheManager.trimList(key, -MAX_MEMORY_SIZE, -1);// ② 一次 LTRIM
        redisCacheManager.expire(key, Duration.ofSeconds(memoryTtl)); // ③ 一次 EXPIRE
        log.info("冷启记忆 warm-up 完成: sessionId={}, 回填 {} 条到 Redis", sessionId, dbMemories.size());
    } catch (Exception e) {
        log.warn("冷启记忆 warm-up 失败（不影响本次使用）: sessionId={}, error={}", sessionId, e.getMessage());
    }
    return dbMemories;
}
```

> **为什么是 3 次往返 vs 300 次？**
> 旧的逐条回填写法 `for (m : dbMemories) addMemoryToRedis(m)`，每条会调用 `RPUSH + LTRIM + EXPIRE` 共 3 次 Redis 操作。100 条记忆 = 300 次往返，其中 297 次 `LTRIM/EXPIRE` 完全冗余（中间状态没人看）。批量写法只需 `pushAllToList`（1 次 RPUSH）+ 收尾 1 次 `LTRIM` + 1 次 `EXPIRE`，共 3 次。这是 v3.10 的性能修复。

---

## 端到端验证结果

```
步骤1: Redis 有 6 条记忆（正常状态）
步骤2: DEL lumina:agent:memory:{uuid}（模拟 Redis 过期/重启）
步骤3: 执行 Agent → 触发 getMemories → 发现 Redis 空
步骤4: 自动从 DB 加载最近 7 条消息（DESC 取最近，再 reverse 转升序）
步骤5: Warm-up 批量回填 Redis 7 条（3 次 Redis 往返）
步骤6: Agent 正常执行（有上下文，没失忆）

日志：
冷启记忆恢复: conversationUuid=b47850e1..., 从 DB 加载最近 7 条消息
冷启记忆 warm-up 完成: sessionId=b47850e1..., 回填 7 条到 Redis
```

---

## 设计要点

| 决策 | 原因 |
|------|------|
| 端口接口（不直接查 DB） | 模块依赖方向：core 不能引用 business |
| Loader 直接用 Mapper（不经 Service） | 能 `orderByDesc` 取最近 N 条；断开与 ConversationService 的依赖链 |
| `@Lazy` 注入 | 历史上打破 Loader→Service→MemoryManager 循环；现作防御性兜底 |
| 批量 warm-up（`pushAllToList`） | 3 次 Redis 往返而非逐条回填的 300 次 |
| 取最近 N 条（DESC + reverse） | 冷启动要恢复"最近上下文"，不是"最早 N 条" |
| Warm-up 回填 | 只冷启一次，后续直接命中 Redis（不需要每次查 DB） |
| `@Autowired(required=false)` | standalone 模式（无 DB）时 MemoryManager 仍可用 |
| 对调用方透明 | DefaultAgentExecutionEngine 无需改动 |

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| 冷启恢复 | Redis 空时从 MySQL lumina_message 表加载**最近 N 条**历史 |
| 端口接口 | ColdStartMemoryLoader 在 core 定义，DbColdStartMemoryLoader 在 business 实现 |
| Mapper-based | Loader 直接用 ConversationMapper/MessageMapper，不再依赖 ConversationService |
| 批量 warm-up | `pushAllToList` 一次写完，3 次 Redis 往返（而非逐条的 300 次） |
| @Lazy | 历史上打破循环；现 Loader 已不经 Service，@Lazy 作防御性保留 |

### 自测题

1. 为什么 MemoryManager 不能直接查 MySQL？
   <details><summary>答案</summary>模块依赖方向：lumina-agent-core 不能引用 lumina-business-agent 的类（ConversationMapper/MessageMapper/MessageDO 等），否则循环依赖。用端口接口模式解决：接口定义在 core，实现在 business。</details>

2. Warm-up 回填后，下次请求还会查 DB 吗？
   <details><summary>答案</summary>不会。Warm-up 把 DB 数据回填到 Redis，后续请求 Redis 直接命中。冷启恢复只发生在 Redis 为空时（首次访问或过期后）。</details>

3. `@Lazy` 在这里还必要吗？
   <details><summary>答案</summary>v3.10 后不再是"必需"。历史上 Loader 依赖 ConversationService，而 ConversationService 又用到 MemoryManager，形成 MemoryManager↔(Loader→Service) 循环，必须 @Lazy 打破。现在 Loader 改用 Mapper，循环链已断；但 @Lazy 作为防御性兜底保留，避免将来误加依赖导致启动失败。</details>

4. 对调用方（DefaultAgentExecutionEngine）透明是什么意思？
   <details><summary>答案</summary>引擎层只调 memoryManager.getRecentMemories()，不需要知道冷启恢复的存在。恢复逻辑完全封装在 MemoryManager 内部。</details>

5. 为什么用 `orderByDesc(createTime).last("LIMIT N")` 再 `reverse()`，而不是 `listMessages(uuid, 1, N)` 升序取前 N 条？
   <details><summary>答案</summary>升序取前 N 条拿到的是**最早的** N 条（页码 1 的开头），但冷启动要恢复的是"最近的对话上下文"。DESC + LIMIT 取最近 N 条，再 reverse 转升序，才能让最近的消息出现在列表末尾（靠近当前提问），符合 LLM 上下文顺序。这是 v3.10 的语义修复。</details>

6. 100 条记忆的 warm-up，批量写法为什么比逐条快得多？
   <details><summary>答案</summary>逐条 `addMemoryToRedis` 每条都做 RPUSH + LTRIM + EXPIRE（3 次 Redis 往返），100 条 = 300 次，其中 297 次 LTRIM/EXPIRE 是冗余的中间状态。批量写法用 `pushAllToList` 一次 RPUSH 全部，再补 1 次 LTRIM + 1 次 EXPIRE，共 3 次往返。这是 v3.10 的性能修复。</details>

> 🚀 返回 [AI 专项导读](README.md)

---

📝 **本篇撰写期间修正的代码**（v3.10，两处）：

1. **语义修复 — 取最近 N 条而非最早 N 条**：
   `DbColdStartMemoryLoader` 原先依赖 `ConversationService.listMessages(uuid, 1, limit)`，按"页码升序"取到的是**最早的** limit 条，与冷启动"恢复最近上下文"的目的相悖。改为直接用 `MessageMapper` 的 `LambdaQueryWrapper.orderByDesc(createTime).last("LIMIT N")` 取最近 N 条，再 `Collections.reverse()` 转为时间升序。同时把 `ConversationService` 的依赖也一并摘掉，改用 `ConversationMapper.selectOne(uuid)` 解析 conversationId。

2. **性能修复 — 批量 warm-up**：
   `MemoryManager.loadFromDbAndWarmUp` 原先逐条 `addMemoryToRedis(sessionId, m)` 回填 Redis，每条 3 次往返（RPUSH + LTRIM + EXPIRE），100 条记忆就是 300 次 Redis 调用，其中 297 次 LTRIM/EXPIRE 完全冗余。改为 `pushAllToList` 一次 RPUSH 全部 + 收尾各 1 次 LTRIM/EXPIRE，共 3 次往返。

   顺带：循环依赖叙述更新——Loader 已不依赖 `ConversationService`，MemoryManager↔ConversationService 的循环链不再成立，`@Lazy` 由"必需"转为"防御性保留"。
