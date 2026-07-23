# 14 — 工作流引擎原理

> **前置要求**：已完成 [13-容错模式](13-resilience-pattern.md)
> **预计阅读**：20 分钟
> **难度**：⭐⭐⭐⭐⭐

---

## 面试题引入

> **"面试官：DAG 是什么？Lumina 的工作流引擎怎么执行 DAG？Flowable 和自研引擎有什么区别？"**

---

## 深层原理

### DAG（有向无环图）

```
     collect
       │
       ▼
    analyze
       │
       ▼
  check-severity（condition）
    /         \
  P0          P1-P3
  │              │
human         auto-report
  │              │
  ▼              ▼
send-alert    ──end──
```

**有向**：边有方向（从→到）。**无环**：不能循环回去（否则死循环）。

### 执行算法（拓扑遍历）

```java
// DefaultWorkflowEngine.java 的 doExecute 核心逻辑（简化）
while (有待执行节点) {
    Node node = 取下一个节点;
    switch (node.type) {
        case AGENT:    result = agentNodeExecutor.execute(node); break;
        case CONDITION:
            boolean cond = spEL.eval(node.expression);
            下一节点 = cond ? node.trueBranch : node.falseBranch;
            continue;
        case PARALLEL:  并行执行所有分支; break;
        case LOOP:      循环执行; break;
        case HUMAN:     暂停(PAUSED); return;
        case TRANSFORM: 变量转换; break;
    }
    存变量: context.put(node.outputVar, result);
    下一节点 = 按 edge 找后继;
}
```

---

## 节点执行器模式

```
WorkflowNode（抽象）
  ├── AgentNode         → AgentNodeExecutor
  ├── ConditionNode     → ConditionNodeExecutor
  ├── LoopNode          → LoopNodeExecutor
  ├── ParallelNode      → ParallelNodeExecutor
  ├── TransformNode     → TransformNodeExecutor
  └── HumanNode         → HumanNodeExecutor
```

每种节点有专门的执行器——**多态分发**。

---

## 自研 vs Flowable

| | DefaultWorkflowEngine（自研） | FlowableWorkflowEngine |
|---|---|---|
| 依赖 | 无 | Flowable 7.0.1 |
| 存储 | 内存 | 数据库（BPMN 表） |
| 持久化 | 无 | 完整历史记录 |
| 适合 | 开发/演示 | 生产环境 |
| 切换 | @ConditionalOnBean 自动选择 |

### YAML → BPMN 转换

```
用户写 YAML → FlowableBpmnConverter → BPMN 2.0 XML → Flowable 部署执行
```

---

## 并行节点怎么实现

```java
// ParallelNodeExecutor
List<CompletableFuture<Void>> futures = branches.stream()
    .map(branch -> CompletableFuture.runAsync(() -> executeBranch(branch)))
    .toList();
CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
// 所有分支并行执行，全部完成才继续
```

---

## 常见追问

### Q：为什么叫"有向无环"？有环会怎样？

**A**：有环=死循环（A→B→A→B...）。LOOP 节点是"受控的循环"（有终止条件），不是图的环。

### Q：工作流怎么持久化中间状态？

**A**：每个节点执行后，`outputVar` 和当前节点位置存入 `instance.output`（JSON）。PAUSED 时暂停，resume 时从 JSON 恢复上下文继续。

---

## 小结

| 知识点 | 一句话 |
|--------|--------|
| DAG | 有向无环图 |
| 执行 | 拓扑遍历 + 按节点类型分发 |
| 节点执行器 | 多态（每种节点一个 Executor） |
| 并行 | CompletableFuture.allOf |
| 持久化 | instance.output 存上下文 JSON |
| 双引擎 | @ConditionalOnBean 自动切换 |

---

📝 **本篇撰写期间修正的代码**：无。
