# 生产修复方案 C：异常处理与资源泄漏（P1）

## 改动文件清单（互不冲突，不碰方案 A/B 的文件）

### C1. QdrantRestStore 异常处理改进
文件：`lumina-agent-core/src/main/java/io/lumina/agent/rag/QdrantRestStore.java`
- 第 102, 165, 238, 261 行有 4 处 catch 只 log 不 rethrow
- 修改策略（不是全部 rethrow，因为有些是清理操作）：
  - **add/delete 操作失败**：增加 metrics 计数（`qdrant.error.count`），然后 rethrow（让调用者知道入库失败）
  - **search 操作失败**：返回空结果 + log.error（搜索失败不应阻断 Agent 执行）
  - **连接/初始化失败**：rethrow
- 具体修改：给每个 catch 块加判断
  - add: `throw new BusinessException(ErrorCode.RAG_EMBEDDING_FAILED, "向量入库失败", e);`
  - search: 保持返回空，但 log.error 改为更详细的错误信息
  - delete: rethrow（删除失败不应静默，会导致孤儿数据）

### C2. DocumentIngestConsumer 源文件删除时机修复
文件：`lumina-modules/lumina-business-agent/src/main/java/io/lumina/agent/mq/DocumentIngestConsumer.java`
- 当前第 122 行：`Files.deleteIfExists(filePath)` 在入库成功之前就执行了
- 问题：如果入库失败需要重试（MQ 重试机制），源文件已经删了
- 修复：把 `Files.deleteIfExists(filePath)` 移到入库成功之后
- 具体做法：在 onMessage 方法的成功路径末尾删除，在 catch 块中不删除（让 MQ 重试时文件还在）
- 注意：如果 MQ 最终重试失败（死信），需要一个清理任务删除残留文件。简单版：在 catch 块中也删除（避免磁盘泄漏），因为 MQ 重试 16 次后不再重试

### C3. Code Interpreter 进程泄漏修复
文件：`lumina-agent-core/src/main/java/io/lumina/agent/tool/CodeInterpreterToolProvider.java`
- 第 212-226 行 executeInProcess 方法：异常路径不 destroy 子进程
- 修复：把 Process 声明在 try 外面，finally 块中 destroyForcibly
```java
Process process = null;
try {
    process = pb.start();
    // ... existing logic ...
} finally {
    if (process != null && process.isAlive()) {
        process.destroyForcibly();
    }
}
```
- 第 392-397 行 Docker 容器池超时后归还问题：超时的容器不应归还到池中
- 修复：超时后调用 `container.stop()` 或标记为不可用，不归还

### C4. MemoryManager 异常处理改进
文件：`lumina-agent-core/src/main/java/io/lumina/agent/manager/MemoryManager.java`
- 第 111, 157, 176 行有 3 处 Redis 操作失败静默降级
- 修复策略：
  - addMemory 失败：保持静默降级到 Caffeine（不能让记忆写入失败影响 Agent 执行）
  - getMemories 失败：保持静默返回空列表
  - clearMemories 失败：保持静默（清理操作失败不影响主流程）
  - 但在每处 catch 中增加 `log.warn` → `log.error`（提升日志级别，运维能监控到 Redis 故障）
- 不改 rethrow 策略（记忆是非关键路径），只提升日志级别

### C5. KnowledgeServiceImpl 知识删除孤儿向量修复
文件：`lumina-modules/lumina-business-agent/src/main/java/io/lumina/agent/service/impl/KnowledgeServiceImpl.java`
- 问题：Qdrant 删除失败被吞后仍删 MySQL 记录 → 孤儿向量
- 修复：Qdrant 删除失败时中止删除操作，抛异常给用户
```java
// 删除向量数据时
try {
    knowledge.deleteByDocId(docUuid).block();
} catch (Exception e) {
    log.error("删除向量数据失败，中止删除: docUuid={}", docUuid, e);
    throw new BusinessException(ErrorCode.INTERNAL_ERROR, 
        "删除知识库文档失败（向量数据清理失败），请重试或联系管理员", e);
}
// Qdrant 删除成功后才删 MySQL
```

## 验证
mvn compile -q
mvn test -pl lumina-agent-core,lumina-modules/lumina-business-agent -am -Dtest="CodeInterpreterToolProviderTest,CodeInterpreterToolProviderExecTest,DocumentIngestConsumerTest" -Dsurefire.failIfNoSpecifiedTests=false
