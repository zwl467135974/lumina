# I04 — 人工审批节点（Human-in-the-Loop）

> **前置要求**：已完成 [I03-Flowable 引擎](I03-flowable-bpmn-engine.md)
> **预计阅读**：15 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

有些工作流步骤需要**人工确认**（如 P0 故障审批）。工作流怎么"暂停等人"，人审批后怎么"恢复继续"？

---

## PAUSED → resume 流程

```
工作流执行到 human 节点
  ↓
状态变 PAUSED，持久化上下文（instance.output = ctx.variables）
  ↓ 等待人审批
人调用 resume API + decision
  ↓
读取 instance.output 恢复上下文
  ↓
继续执行后续节点
```

---

## v3.6 的关键修复

```java
// 文件：WorkflowServiceImpl.java
// PAUSED 时也写入 instance.output（之前只在 COMPLETED 时写）
// → resume 时读不到上下文，之前所有节点产出丢失！
case PAUSED:
    instance.setOutput(objectMapper.writeValueAsString(ctx.getVariables()));  // ← 修复
    break;
```

**这是个 P0 bug**——没修之前，人工审批后工作流的上下文全丢了。

---

## resume API

```java
// WorkflowController.java
@PostMapping("/instances/{instanceId}/resume")
public R<WorkflowInstanceVO> resumeInstance(
    @PathVariable Long instanceId,
    @RequestParam String decision) {      // approve / reject
    return R.success(workflowService.resumeInstance(instanceId, decision));
}
```

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| human 节点 | 工作流暂停等人审批 |
| PAUSED 持久化 | instance.output 存上下文变量 |
| resume | 传 decision（approve/reject）恢复执行 |
| v3.6 修复 | PAUSED 必须写 output（之前漏了） |

> 🚀 [I05 — Cron 触发器 →](I05-cron-trigger.md)

---

📝 **本篇撰写期间修正的代码**：无。
