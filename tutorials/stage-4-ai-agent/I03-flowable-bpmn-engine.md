# I03 — Flowable BPMN 引擎

> **前置要求**：已完成 [I02-六种节点](I02-six-node-types.md)
> **预计阅读**：10 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

Lumina 有两个工作流引擎：自研的 `DefaultWorkflowEngine`（轻量 DAG）和 `FlowableWorkflowEngine`（BPMN 标准）。为什么两个？怎么切换？

---

## 双引擎设计

```java
// 文件：FlowableWorkflowEngine.java
@Primary                                    // 有 Flowable 时优先用它
@ConditionalOnBean(RepositoryService.class)  // 有 Flowable 依赖才装配
public class FlowableWorkflowEngine implements WorkflowEngine { ... }

// 文件：DefaultWorkflowEngine.java
// 没有 @Primary，作为 fallback
public class DefaultWorkflowEngine implements WorkflowEngine { ... }
```

**优雅降级**：引入 Flowable 依赖 → 用 Flowable；不引入 → 用自研引擎。同一套 YAML 定义，底层引擎透明切换。

---

## 为什么有 Flowable

| 引擎 | 特点 |
|------|------|
| DefaultWorkflowEngine | 轻量、无额外依赖、内存执行 |
| FlowableWorkflowEngine | BPMN 2.0 标准、支持持久化/历史/可视化 |

生产环境推荐 Flowable（有完整的历史记录和可视化），开发/演示用自研（简单快速）。

---

## YAML → BPMN 自动转换

Lumina 写的是 YAML，但 Flowable 用 BPMN 2.0 XML。有个 `FlowableBpmnConverter` 自动转换：

```
YAML 工作流定义
  ↓ FlowableBpmnConverter
BPMN 2.0 XML
  ↓ Flowable 部署
可执行流程
```

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| 双引擎 | DefaultWorkflowEngine（轻量）+ Flowable（标准） |
| @Primary | 有 Flowable 优先用它 |
| @ConditionalOnBean | 有 Flowable 依赖才装配 |
| YAML→BPMN | 自动转换，用户不感知 |

> 🚀 [I04 — 人工审批 →](I04-human-in-the-loop.md)

---

📝 **本篇撰写期间修正的代码**：无。
