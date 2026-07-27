# I02 — 六种节点类型：工作流的积木块

> **前置要求**：已完成 [I01 工作流编排](I01-workflow-orchestration.md)
> **预计阅读**：25 分钟
> **难度**：⭐⭐⭐⭐☆

---

## 这节解决什么问题

I01 讲了工作流的整体概念。这节深入每种节点类型的**内部实现**——特别是 parallel 节点的 **Virtual Thread 并行执行** 和 context 的 **深拷贝隔离**，这是 JDK 21 + 工作流引擎的精华。

---

## 六种节点全景

| 节点 | 作用 | 类比 | 执行器 |
|------|------|------|--------|
| **agent** | 执行一个 Agent | 流水线工人 | AgentNodeExecutor |
| **condition** | 条件判断，走不同分支 | 质检分流 | ConditionNodeExecutor |
| **loop** | 循环执行子图 | 批量处理 | LoopNodeExecutor |
| **parallel** | 并行执行多分支 | 多工位同时开工 | ParallelNodeExecutor |
| **transform** | 数据转换（SpEL） | 加工改型 | TransformNodeExecutor |
| **human** | 人工审批（暂停） | 人工审核 | HumanNodeExecutor |

---

## 1. agent 节点：执行 AI 任务

```yaml
- id: analyze-data
  type: agent
  agentId: 2              # 执行哪个 Agent
  inputVar: raw_data       # 输入变量（引用上游结果）
  outputVar: analysis      # 输出变量名（下游用 #analysis 引用）
```

```java
// 文件：lumina-agent-core/.../orchestration/engine/AgentNodeExecutor.java:43
// 每个 AgentNode 调用一次真实的 Lumina Agent
public String execute(WorkflowNode node, WorkflowContext ctx) {
    Long agentId = node.getAgentNode().getAgentId();
    String task = resolveVariable(ctx, node.getAgentNode().getInputVar());
    String conversationUuid = ctx.getInstanceId();

    // 调用 Agent 执行引擎（走完整的 ReAct/PlanAndExecute 循环）
    String result = agentHandler.executeAgent(agentId, task, conversationUuid);
    return result;
}
```

**关键**：agent 节点不是一个简单的函数调用——它走完整的 Lumina Agent 执行链（记忆加载 → LLM 调用 → 工具执行 → 结果返回）。

---

## 2. condition 节点：条件分支

```yaml
- id: check-severity
  type: condition
  expression: "#analysis.contains('P0')"
  trueBranch: human-approval    # P0 → 人工审批
  falseBranch: auto-report      # 非 P0 → 自动报告
```

```java
// 文件：DefaultWorkflowEngine.java（路由协议）
// condition 节点返回 "route:targetId" 字符串，引擎解析前缀路由
String routeSignal = executor.execute(node, ctx);
if (routeSignal.startsWith("route:")) {
    String targetId = routeSignal.substring("route:".length());
    return targetId;  // 跳转到目标节点
}
```

**设计亮点**：condition 节点不直接跳转，而是返回 `route:targetId` 信号——引擎统一解析路由，节点本身保持简单。

---

## 3. parallel 节点：Virtual Thread 并行 ⭐

这是最有深度的节点——用 **JDK 21 虚拟线程**实现 fan-out/fan-in。

```yaml
- id: parallel-inspection
  type: parallel
  branches:
    - [check-cpu, check-memory]       # 分支 A：CPU + 内存检查
    - [check-disk, check-network]     # 分支 B：磁盘 + 网络检查
  waitAll: true                        # true=全等完，false=任一完成即可
```

### Virtual Thread 执行

```java
// 文件：DefaultWorkflowEngine.java:212-244
private String executeParallelBranches(WorkflowDefinition definition,
                                        WorkflowNode node,
                                        ParallelSignal signal,
                                        WorkflowContext ctx) {
    // 🔑 JDK 21 虚拟线程执行器——每个分支一个虚拟线程
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {

        var futures = signal.branches().stream()
            .map(branch -> CompletableFuture.supplyAsync(() -> {
                // 每个分支获得一份独立的上下文快照
                WorkflowContext branchCtx = copyContext(ctx);
                executeChain(definition, branch.startNode(), branchCtx);
                return Map.entry(branch, branchCtx);
            }, executor))
            .toList();

        // fan-in：等待所有分支（或任一）完成
        if (signal.waitAll()) {
            CompletableFuture.allOf(futures.toArray(...)).join();
        } else {
            CompletableFuture.anyOf(futures.toArray(...)).join();
        }

        // 合并各分支结果到主上下文
        Map<String, Object> merged = new LinkedHashMap<>();
        for (var f : futures) {
            var entry = f.join();
            merged.put(entry.getKey().name(), entry.getValue().getNodeResult(...));
        }
        ctx.setVariable(node.getId() + "_result", merged);
    }
}
```

### 为什么用虚拟线程

| 维度 | 平台线程（传统） | 虚拟线程（JDK 21） |
|------|----------------|-------------------|
| 创建成本 | ~1MB 栈空间 | ~KB 级 |
| 100 并发分支 | 可能 OOM | 轻松支持 |
| IO 阻塞 | 占住线程 | 自动让出 |
| 代码改动 | 无（同一 API） | 无 |

工作流分支的典型瓶颈是 **Agent 执行（IO 密集型，等 LLM 响应）**——虚拟线程在等 LLM 时自动让出 CPU，不浪费线程资源。

### Context 深拷贝隔离

每个并行分支获得一份**独立的上下文快照**，互不干扰：

```java
// 文件：DefaultWorkflowEngine.java:311-327
private WorkflowContext copyContext(WorkflowContext source) {
    WorkflowContext copy = new WorkflowContext();
    // 用 Jackson 做深拷贝（避免分支间共享引用导致竞态）
    copy.setVariables(JsonUtils.OBJECT_MAPPER.convertValue(
            source.getVariables(), new TypeReference<Map<String, Object>>() {}));
    copy.setNodeResults(JsonUtils.OBJECT_MAPPER.convertValue(
            source.getNodeResults(), new TypeReference<Map<String, Object>>() {}));
    // ...其他字段
    return copy;
}
```

**为什么深拷贝？** 如果分支 A 和 B 共享同一个 `variables` Map 引用，A 写入 `cpu_result` 时可能和 B 写入 `disk_result` 竞态。深拷贝确保每个分支独立操作自己的 Map，最后再合并。

### 合并策略

```
分支 A 执行后：{ cpu_result: "80%", memory_result: "60%" }
分支 B 执行后：{ disk_result: "45%", network_result: "正常" }
                ↓ 合并
主上下文：parallel_result: {
    "branch_A": "80%",
    "branch_B": "45%"
}
```

---

## 4. loop 节点：循环执行

```yaml
- id: batch-process
  type: loop
  loopTarget: process-single     # 要循环执行的子图入口
  items: "#data_array"           # 遍历的集合
  itemVar: current_item          # 当前元素变量名
  exitTarget: summary            # 循环结束后跳转
```

```java
// 文件：DefaultWorkflowEngine.java:336-361
private String executeLoop(WorkflowDefinition definition, WorkflowNode loopNode,
                           LoopSignal signal, WorkflowContext ctx) {
    List<Object> items = signal.items();  // 要遍历的集合

    for (int i = 0; i < items.size(); i++) {
        // 每轮设置当前元素和索引
        ctx.setVariable(signal.itemVar(), items.get(i));
        ctx.setVariable("_loopIndex", i);

        // 执行子图
        executeChain(definition, signal.loopTarget(), ctx);
    }

    // 循环结束，跳转到出口
    return signal.exitTarget();
}
```

**支持两种循环**：
- **集合遍历**：`items` 是一个 List，逐个处理
- **条件循环**：类似 while，满足条件继续

---

## 5. transform 节点：数据转换

```yaml
- id: format-date
  type: transform
  expression: "#raw_date.format('yyyy-MM-dd')"   # SpEL 表达式
  outputVar: formatted_date
```

```java
// 文件：TransformNodeExecutor.java
// 用 Spring Expression Language (SpEL) 执行转换
ExpressionParser parser = new SpelExpressionParser();
Expression exp = parser.parseExpression(node.getTransformNode().getExpression());
Object result = exp.getValue(ctx.getVariables());
```

**用途**：日期格式化、JSON 字段提取、字符串拼接、数学计算——不需要调 LLM 的简单转换。

---

## 6. human 节点：人工审批

```yaml
- id: approve-deployment
  type: human
  prompt: "检测到 P0 故障，是否触发告警？"
  options: ["approve", "reject"]
  decisionVar: approval_decision
```

```java
// 执行到 human 节点时：
// 1. 工作流实例状态改为 PAUSED
// 2. 记录当前节点 ID
// 3. 返回暂停信号（不继续执行）
// 4. 等待外部调用 resume(instanceId, decision)

// 外部审批后调用：
workflowEngine.resume(instanceId, "approve");
// → 工作流从 human 节点恢复，继续执行下一个节点
```

**这是工作流引擎最复杂的状态管理**——需要持久化 PAUSED 状态，支持跨进程恢复（审批可能在不同时间、不同会话完成）。

---

## 小结

| 节点 | 核心实现 | 最值得学的点 |
|------|---------|------------|
| agent | 调用完整 Agent 执行链 | 不是简单函数调用 |
| condition | route:targetId 信号协议 | 节点不跳转，引擎统一路由 |
| parallel | Virtual Thread + 深拷贝隔离 | JDK 21 虚拟线程 + 竞态避免 |
| loop | for 循环 + itemVar 注入 | 集合遍历 vs 条件循环 |
| transform | SpEL 表达式 | 无需 LLM 的简单转换 |
| human | PAUSED 状态 + resume | 跨进程状态恢复 |

### 自测题

1. parallel 节点为什么用虚拟线程而不是普通线程池？
   <details><summary>答案</summary>工作流分支是 IO 密集型（等 Agent/LLM 响应），虚拟线程在 IO 阻塞时自动让出 CPU，不浪费线程资源。且创建成本极低（KB 级 vs MB 级），轻松支持大量并发分支。</details>

2. 为什么每个并行分支需要深拷贝 Context？
   <details><summary>答案</summary>避免分支间共享 Map 引用导致写入竞态。分支 A 写 cpu_result 和分支 B 写 disk_result 可能同时操作同一个 HashMap 导致数据丢失。深拷贝确保各分支独立操作，最后合并。</details>

3. condition 节点返回 `route:targetId` 而不是直接跳转有什么好处？
   <details><summary>答案</summary>节点保持简单（只返回信号），跳转逻辑集中在引擎层。这便于引擎做统一的环检测、最大步数限制等安全控制。</details>

4. human 节点暂停后，工作流怎么知道恢复时从哪继续？
   <details><summary>答案</summary>暂停时持久化当前节点 ID 到工作流实例记录。resume 时读取实例记录，从该节点继续执行。</details>

> 🚀 [I03 — Flowable 引擎 →](I03-flowable-bpmn-engine.md)

---

📝 **本篇撰写期间修正的代码**：无。
