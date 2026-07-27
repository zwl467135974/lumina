# I04 — 人工审批节点（Human-in-the-Loop）

> **前置要求**：已完成 [I03-Flowable 引擎](I03-flowable-bpmn-engine.md)
> **预计阅读**：15 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

完全自动化的工作流很爽——但现实里很多步骤**不能让 AI 一锤定音**：
- P0 故障自动诊断后，要不要触发短信告警全公司？
- AI 拟好合同条款后，财务终审签字了吗？
- Agent 想执行一笔大额转账，风控要不要拦一下？

这些场景的共同点是：**工作流跑到某个点必须停下，等人做决定，再带着决定继续跑**。这节讲 Lumina 的 `human` 节点如何实现这种"暂停-恢复"语义。

---

## 类比：流水线上的质检卡点

想象一条汽车装配流水线：
- **agent 节点** = 焊接机器人，自动干活
- **human 节点** = 总装完成后的人工质检站

机器人不会自己开走整辆车——它把车停在质检站，**等质检员盖章**才能放行。如果质检员说"返工"，车走另一条支线；说"通过"，车继续往下走。

Lumina 工作流的 `human` 节点就是这个质检站——执行到这里**暂停整条流水线**，等外部 `resume` 信号带着人的决定回来。

---

## 状态机：PAUSED 的来龙去脉

```
        execute()                  resume(decision)
RUNNING ──────────► PAUSED ──────────────────────► RUNNING ──► COMPLETED
                       │
                       │ (server restart)
                       ▼
                   [DB 持久化] ← 关键！工作流实例不会丢
```

`human` 节点的核心是**状态机切换**：`RUNNING` → `PAUSED` → 等人 → `RUNNING` → 继续。

### Default 引擎的实现：抛异常暂停

```java
// 文件：lumina-agent-core/.../orchestration/engine/HumanNodeExecutor.java:28-32
@Override
public Object execute(WorkflowNode node, WorkflowContext ctx) {
    HumanNode humanNode = (HumanNode) node;
    log.info("人工审批节点暂停: id={}, prompt={}", node.getId(), humanNode.getPrompt());
    // 关键：抛异常打断执行循环
    throw new HumanApprovalRequiredException(
        node.getId(), humanNode.getPrompt(), humanNode.getDecisionVar());
}
```

```java
// 文件：lumina-agent-core/.../orchestration/engine/DefaultWorkflowEngine.java:99-134
while (currentNodeId != null) {
    // ... 正常执行节点 ...
    currentNodeId = executeNode(definition, node, ctx);
}

} catch (HumanNodeExecutor.HumanApprovalRequiredException e) {
    // ① 状态切到 PAUSED
    ctx.setStatus(WorkflowStatus.PAUSED);
    // ② 占位标记（resume 时覆盖为真实 decision）
    ctx.setVariable(e.getDecisionVar(), "__WAITING__");
    // ③ 记住暂停在哪个节点
    ctx.setCurrentNodeId(e.getNodeId());
    log.info("工作流暂停等待人工审批: node={}", e.getNodeId());
}
```

**设计巧思**：用异常做控制流听起来"脏"，但在 DAG 引擎里它能**瞬间跳出多层调用栈**回到引擎主循环，比逐层返回 Optional 干净得多。

### Flowable 引擎的实现：UserTask 原生暂停

Flowable 不用抛异常——它的 BPMN 标准里就有 `UserTask`，遇到时**流程实例自动挂起**：

```java
// 文件：lumina-agent-core/.../orchestration/flowable/FlowableWorkflowEngine.java:203-216
private void handleProcessOutcome(...) {
    List<Task> tasks = taskService.createTaskQuery()
            .processInstanceId(processInstanceId).list();

    if (!tasks.isEmpty()) {
        // 还有未完成的 UserTask → 流程已暂停
        Task task = tasks.get(0);
        ctx.setVariable("__flowable_taskId", task.getId());
        ctx.setCurrentNodeId(task.getTaskDefinitionKey());
        ctx.setStatus(WorkflowStatus.PAUSED);       // 同样切到 PAUSED
        // ...
    } else {
        // 没有 UserTask → 流程已走完
        ctx.setStatus(WorkflowStatus.COMPLETED);
    }
}
```

两种引擎对外暴露的状态机完全一致（`PAUSED`），**上层 `WorkflowServiceImpl` 不用关心底层差异**。

---

## resume：带着决定继续

```java
// 文件：lumina-agent-core/.../orchestration/engine/DefaultWorkflowEngine.java:73-87
@Override
public WorkflowContext resume(WorkflowDefinition definition,
                              WorkflowContext pausedCtx, String decision) {
    String humanNodeId = pausedCtx.getCurrentNodeId();
    WorkflowNode node = definition.findNode(humanNodeId);
    HumanNode humanNode = (HumanNode) node;

    // ① 注入人的决定（"approve" / "reject"）到上下文
    pausedCtx.setVariable(humanNode.getDecisionVar(), decision);
    pausedCtx.setStatus(WorkflowStatus.RUNNING);

    // ② 根据 decision 决定下一个节点（不同分支）
    String nextNodeId = determineNextNode(definition, node, decision, pausedCtx);
    log.info("工作流恢复执行: 从节点 {} 继续", nextNodeId);

    // ③ 从下一个节点继续 doExecute 循环
    return doExecute(definition, pausedCtx, nextNodeId);
}
```

Flowable 版的 resume 更简单——`taskService.complete(taskId, {decisionVar: decision})` 让 Flowable 自己往下走：

```java
// 文件：FlowableWorkflowEngine.java:170-201
public WorkflowContext resume(WorkflowDefinition definition,
                              WorkflowContext pausedCtx, String decision) {
    String processInstanceId = pausedCtx.getVariable("__flowable_pid");
    String taskId = pausedCtx.getVariable("__flowable_taskId");

    WorkflowNode node = definition.findNode(pausedCtx.getCurrentNodeId());
    String decisionVar = "decision";
    if (node instanceof HumanNode hn && hn.getDecisionVar() != null) {
        decisionVar = hn.getDecisionVar();           // 用 YAML 配置的变量名
    }
    pausedCtx.setStatus(WorkflowStatus.RUNNING);

    taskService.complete(taskId, Map.of(decisionVar, decision));  // 关键
    handleProcessOutcome(definition, pausedCtx, processInstanceId);
    return pausedCtx;
}
```

---

## decisionVar：人工决定的"变量名"

```yaml
- id: approve-deployment
  type: human
  prompt: "检测到 P0 故障，是否触发告警？"
  options: ["approve", "reject"]
  decisionVar: approval_decision    # ← 决定存到这个变量名
```

`decisionVar` 是 human 节点最重要的配置——它定义了**人的决定在上下文里叫什么名字**。下游 condition 节点用这个名字路由：

```yaml
- id: route-by-approval
  type: condition
  expression: "#approval_decision == 'approve'"
  trueBranch: send-alert
  falseBranch: log-only
```

如果 human 节点没配 `decisionVar`，默认用 `"decision"`。

---

## 持久化：跨重启不丢上下文

人工审批可能跨**几分钟到几天**——审批人可能下班了、服务器可能重启了。Lumina 必须把暂停状态持久化到数据库。

### v3.6 的关键修复

```java
// 文件：lumina-modules/lumina-business-agent/.../service/impl/WorkflowServiceImpl.java:404-452
private void executeWorkflow(WorkflowDefinition definition,
                             WorkflowInstanceDO instance, ...) {
    // ...
    WorkflowContext ctx = workflowEngine.execute(definition, inputs);

    if (ctx.getStatus() == WorkflowStatus.COMPLETED) {
        instance.setOutput(objectMapper.writeValueAsString(ctx.getVariables()));
    } else if (ctx.getStatus() == WorkflowStatus.PAUSED) {
        // 🔑 v3.6 修复：PAUSED 时也必须写入 instance.output
        // 之前这里漏写了，resume 时读不到上下文 → 所有上游节点的产出全丢！
        instance.setOutput(objectMapper.writeValueAsString(ctx.getVariables()));
    }
    // ...
}
```

**修复前**：`PAUSED` 状态没有持久化 `variables` → 人审批后 `resume`，`DefaultWorkflowEngine.resume()` 拿到的 `pausedCtx` 是空的 → 之前的 Agent 分析结果、数据转换结果全部消失，工作流像从零开始。这是个**P0 级 bug**。

### resume 时的上下文重建

```java
// 文件：WorkflowServiceImpl.java:294-360
public WorkflowInstanceDO resumeInstance(Long instanceId, String decision) {
    WorkflowInstanceDO instance = instanceMapper.selectById(instanceId);
    // 校验状态必须是 PAUSED
    if (!"PAUSED".equals(instance.getStatus())) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "工作流实例不在暂停状态");
    }

    WorkflowContext ctx = new WorkflowContext();
    ctx.setCurrentNodeId(instance.getCurrentNodeId());

    // 🔑 从 instance.output 反序列化之前保存的所有变量
    Map<String, Object> savedVars = objectMapper.readValue(
        instance.getOutput(), Map.class);
    ctx.getVariables().putAll(savedVars);

    // 把 decision 交给引擎，引擎注入并继续执行
    WorkflowContext resultCtx = workflowEngine.resume(definition, ctx, decision);
    // ...
}
```

---

## 完整调用链（HTTP 视角）

```java
// 文件：lumina-modules/lumina-business-agent/.../controller/WorkflowController.java
@PostMapping("/instances/{instanceId}/resume")
public R<WorkflowInstanceVO> resumeInstance(
    @PathVariable Long instanceId,
    @RequestParam String decision) {      // "approve" / "reject"
    return R.success(workflowService.resumeInstance(instanceId, decision));
}
```

```
HTTP POST /instances/123/resume?decision=approve
    ↓
WorkflowServiceImpl.resumeInstance(123, "approve")
    ↓
读 instance.output 重建 ctx
    ↓
workflowEngine.resume(definition, ctx, "approve")
    ↓
引擎注入 decision 到 decisionVar，从下一个节点继续
    ↓
写回 instance（COMPLETED / PAUSED / FAILED）
```

---

## 真实场景示例

### 场景 1：P0 故障审批

```yaml
nodes:
  - id: diagnose
    type: agent
    agentId: 1                     # 监控 Agent 诊断
    outputVar: diagnosis
  - id: check-severity
    type: condition
    expression: "#diagnosis.contains('P0')"
    trueBranch: human-approval
    falseBranch: auto-ticket
  - id: human-approval
    type: human
    prompt: "P0 故障：${diagnosis}。是否触发短信告警全公司？"
    decisionVar: alert_decision
  - id: send-alert
    type: agent
    agentId: 2
    inputVar: "已审批通过：${diagnosis}"
```

### 场景 2：大额转账风控

```yaml
  - id: ai-prepare-transfer
    type: agent
    outputVar: transfer_plan
  - id: risk-approve
    type: human
    prompt: "转账 ${transfer_plan.amount} 元到 ${transfer_plan.to}，请风控审批"
    decisionVar: risk_decision
  - id: execute-transfer
    type: agent
    inputVar: "执行转账：${transfer_plan}（已${risk_decision}）"
```

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| `human` 节点 | 工作流暂停等人审批 |
| Default 引擎暂停方式 | 抛 `HumanApprovalRequiredException` 跳出循环 |
| Flowable 引擎暂停方式 | UserTask 自动挂起流程实例 |
| `PAUSED` 持久化 | `instance.output` 必须存上下文变量 |
| `resume(instanceId, decision)` | 注入决定后从下一节点继续 |
| `decisionVar` | 决定存到哪个变量名（供下游 condition 路由） |
| v3.6 修复 | PAUSED 必须写 output（之前漏了，丢上下文） |

### 自测题

1. Default 引擎为什么用"抛异常"的方式暂停，而不是让 `execute()` 返回一个 PAUSED 状态？
   <details><summary>答案</summary>因为 human 节点可能在多层嵌套调用栈深处（如 parallel 分支或 loop 子链里）。抛异常能瞬间跳出多层调用回到引擎主循环的 catch 块，干净利落。如果用返回值，需要每一层都检查状态、逐层 return，代码冗余且容易漏检。</details>

2. v3.6 修复的 bug 是什么？不修会怎样？
   <details><summary>答案</summary>`WorkflowServiceImpl.executeWorkflow()` 之前只在 `COMPLETED` 时写 `instance.output`，`PAUSED` 时没写。后果：人审批后 `resume`，从数据库读 `instance.output` 反序列化上下文，读到空对象——所有上游 Agent 分析结果、transform 转换结果全部丢失，工作流像从零开始。这是 P0 级数据丢失 bug。</details>

3. 同一份 YAML，Default 引擎和 Flowable 引擎的 human 节点行为一样吗？
   <details><summary>答案</summary>对上层调用方完全一致——都暴露 `PAUSED` 状态、都需要 `resume(instanceId, decision)`、都把 decision 存到 `decisionVar`。底层实现不同：Default 用抛异常跳出循环；Flowable 用 BPMN 标准 UserTask 让流程实例挂起。YAML 不用为引擎差异做任何适配。</details>

4. 为什么 `resumeInstance` 要校验 `instance.getStatus() == "PAUSED"`？
   <details><summary>答案</summary>防止重复 resume 或对正在运行 / 已完成的实例误操作。如果一个实例已经在 RUNNING，意味着别的请求正在执行它，重复注入 decision 会导致状态错乱；对 COMPLETED 实例 resume 则是无意义的。校验保证状态机转换的合法性。</details>

> 🚀 [I05 — Cron 触发器 →](I05-cron-trigger.md)

---

📝 **本篇撰写期间修正的代码**：无。
