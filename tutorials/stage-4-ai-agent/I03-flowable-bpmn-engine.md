# I03 — Flowable BPMN 引擎

> **前置要求**：已完成 [I02-六种节点](I02-six-node-types.md)
> **预计阅读**：12 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

I02 讲的 `DefaultWorkflowEngine` 是 Lumina 自研的轻量 DAG 引擎——内存执行、无额外依赖、足够快。但生产环境往往需要**流程持久化、可视化历史、定时器任务、长时间等待人工审批**……这些重活靠自研引擎太费工夫。

Lumina 的答案是：**双引擎并存，按需切换**。这节讲清楚为什么有两套引擎、它们如何自动切换、以及 YAML 是怎么翻译成 BPMN 的。

---

## 类比：手写表达式 vs 引入计算器

想象你要做财务计算：
- **自研引擎（Default）** = 你手写四则运算 —— 简单场景够用，零依赖，但你得自己处理小数位、括号优先级、错误恢复
- **Flowable 引擎** = 引入一台成熟的金融计算器 —— 它已经把"持久化历史"、"断电续算"、"审计日志"这些做透了，你只要把算式翻译成它的输入格式

Lumina 让你写 YAML（手写算式），底层引擎透明替换——开发用自研图省事，生产用 Flowable 图稳妥。

---

## 双引擎设计

```java
// 文件：lumina-agent-core/.../orchestration/flowable/FlowableWorkflowEngine.java:74-78
@Slf4j
@Component
@Primary                                    // 关键 ①：有 Flowable 时优先用它
@ConditionalOnBean(RepositoryService.class)  // 关键 ②：classpath 有 Flowable 才装配
public class FlowableWorkflowEngine implements WorkflowEngine { ... }
```

```java
// 文件：lumina-agent-core/.../orchestration/engine/DefaultWorkflowEngine.java:40-42
// 没有 @Primary，作为 fallback
@Slf4j
@Component
public class DefaultWorkflowEngine implements WorkflowEngine { ... }
```

**两个注解的协同魔法**：
1. `@ConditionalOnBean(RepositoryService.class)` —— Flowable 的 `RepositoryService` Bean 只有在 classpath 有 Flowable 依赖时才存在。没引入 Flowable starter → 这个条件不成立 → `FlowableWorkflowEngine` 根本不会被实例化。
2. `@Primary` —— 当 `FlowableWorkflowEngine` 和 `DefaultWorkflowEngine` 同时存在时，Spring 注入 `WorkflowEngine` 接口时优先选 `@Primary` 的那个。

结果：**同一份 YAML 定义，同一套 `WorkflowService` 调用代码，底层引擎零成本切换**。

---

## 为什么有两个引擎

| 维度 | DefaultWorkflowEngine | FlowableWorkflowEngine |
|------|----------------------|------------------------|
| **依赖** | 零外部依赖 | 需引入 flowable-spring-boot-starter |
| **持久化** | 内存（依赖 `WorkflowServiceImpl` 外层持久化） | BPMN 引擎自带 ACT_RE / ACT_RU / ACT_HI 表 |
| **历史记录** | 需自己实现 | 内置历史服务（`HistoryService`） |
| **定时器** | 不支持 | 原生 BPMN Timer Event |
| **可视化** | 无 | BPMN 2.0 标准模型，可渲染流程图 |
| **执行模型** | DAG 拓扑遍历（同步） | 完整 BPMN semantics（并行网关、多实例） |
| **适用场景** | 开发 / 演示 / 轻量任务 | 生产 / 长流程 / 强审计场景 |

**生产建议**：长流程、需审计、跨重启恢复的场景用 Flowable；演示和原型用 Default。

---

## YAML → BPMN 自动转换

Lumina 用户写 YAML，但 Flowable 吃的是 BPMN 2.0 XML。`FlowableBpmnConverter` 是这个翻译官：

```
YAML 工作流定义
  ↓ FlowableBpmnConverter.convert()
BpmnModel (内存对象)
  ↓ repositoryService.createDeployment().addBpmnModel().deploy()
可执行流程（持久化到 ACT_RE_DEPLOYMENT 表）
```

### 节点映射规则（源码已写明）

```java
// 文件：lumina-agent-core/.../orchestration/flowable/FlowableBpmnConverter.java:145-180
private FlowElement convertNode(WorkflowNode node) {
    if (node instanceof AgentNode)      return createDelegateTask(node, "agentDelegate");
    if (node instanceof TransformNode)  return createDelegateTask(node, "transformDelegate");
    if (node instanceof ConditionNode)  { ExclusiveGateway gw = new ExclusiveGateway(); ... }
    if (node instanceof ParallelNode)   { ParallelGateway gw = new ParallelGateway(); ... }
    if (node instanceof HumanNode)      { UserTask task = new UserTask(); ... }
    if (node instanceof LoopNode)       return convertLoopNode(loopNode);
}
```

| YAML 节点 | BPMN 元素 | 说明 |
|-----------|-----------|------|
| start（自动生成） | `StartEvent` | 引擎自动加 |
| end（自动生成） | `EndEvent` | 终端节点汇聚到这里 |
| `agent` | `ServiceTask` (delegateExpression=`${agentDelegate}`) | 由 AgentServiceTaskDelegate 执行 |
| `transform` | `ServiceTask` (delegateExpression=`${transformDelegate}`) | 由 TransformServiceTaskDelegate 执行 |
| `loop` (集合遍历) | `ServiceTask` + `MultiInstanceLoopCharacteristics` | Flowable 原生多实例 |
| `loop` (条件循环) | `ServiceTask` (delegateExpression=`${loopDelegate}`) | delegate 内部 while |
| `condition` (二分支) | `ExclusiveGateway` + 条件流 + 默认流 | true 走条件流，false 走默认流 |
| `condition` (多分支) | `ExclusiveGateway` + 多条件流 | 每条 branch 一个条件 |
| `parallel` | `ParallelGateway` (fork) + `ParallelGateway` (join) | 转换器自动推算 join 位置 |
| `human` | `UserTask` | 等待人工 complete |

### 条件表达式翻译

YAML 用 SpEL（`#var`），Flowable 用 JUEL（`${var}`）。转换器做简单正则替换：

```java
// 文件：FlowableBpmnConverter.java:411-431
// #category == 'refund'  →  ${category == 'refund'}
String spelToFlowableCondition(String spel) {
    return "${" + convertSpelVars(spel.trim()) + "}";
}
private String convertSpelVars(String expr) {
    return expr.replaceAll("#([a-zA-Z_][a-zA-Z0-9_.]*)", "$1");
}
```

### 节点定义"夹带私货"

BPMN 标准字段有限，但 Lumina YAML 节点属性丰富（`agentId`、`inputVar`、`prompt` 等）。转换器把这些**全部 JSON 序列化塞进 BPMN 扩展元素**，delegate 执行时再取出来反序列化：

```java
// 文件：FlowableBpmnConverter.java:392-404 —— 写入扩展元素
private void addNodeDefinition(FlowElement element, WorkflowNode node) {
    String json = JsonUtils.OBJECT_MAPPER.writeValueAsString(node);
    ExtensionElement ext = new ExtensionElement();
    ext.setName("nodeDefinition");                 // lumina:nodeDefinition
    ext.setNamespacePrefix("lumina");
    ext.setNamespace("https://lumina.io/bpmn");
    ext.setElementText(json);
    element.addExtensionElement(ext);
}
```

```java
// 文件：AbstractWorkflowDelegate.java:36-67 —— delegate 执行时取出
protected <T extends WorkflowNode> T getNode(DelegateExecution execution, Class<T> expectedType) {
    FlowElement current = execution.getCurrentFlowElement();
    List<ExtensionElement> elements = current.getExtensionElements().get("nodeDefinition");
    String json = elements.get(0).getElementText();
    return JsonUtils.OBJECT_MAPPER.readValue(json, expectedType);
}
```

这是**让 YAML 节点透传到 delegate**的关键技巧——BPMN 是公共协议，扩展元素是它的"逃生舱"。

---

## 三个 ServiceTask Delegate

Flowable 的 ServiceTask 通过 `delegateExpression="${beanName}"` 引用一个 Spring Bean，该 Bean 实现 `JavaDelegate` 接口。Lumina 有三个：

| Delegate Bean | 处理节点 | 关键逻辑 |
|---------------|---------|---------|
| `agentDelegate` | `agent` | 调 `AgentExecutionHandler.executeAgent()` 走完整 Agent 执行链 |
| `transformDelegate` | `transform` | SpEL 求值 或 `${var}` 模板替换 |
| `loopDelegate` | `loop` | 集合遍历走多实例；条件循环 delegate 内部 while |

```java
// 文件：lumina-agent-core/.../orchestration/flowable/AgentServiceTaskDelegate.java:37-60
@Override
public void execute(DelegateExecution execution) {
    AgentNode node = getNode(execution, AgentNode.class);     // 从扩展元素取回 YAML 节点
    Map<String, Object> variables = execution.getVariables();
    String task = resolveInput(node.getInput(), variables);

    String result = agentHandler.executeAgent(                // 委托真实 Agent 执行
        node.getAgentId(), task, node.getConversationUuid()
    );

    if (node.getOutputVar() != null) {
        execution.setVariable(node.getOutputVar(), result);   // 写回流程变量
    }
    execution.setVariable("__nodeResult_" + node.getId(), result);  // 内部约定
}
```

**注意**：所有 `__` 前缀的变量都是 Lumina 内部用的，最后映射回 `WorkflowContext` 时会被自动过滤（见 `FlowableWorkflowEngine.mergeVariables()`，`FlowableWorkflowEngine.java:242-253`）。

---

## 什么时候选哪个引擎

| 你的场景 | 推荐 |
|---------|------|
| 本地开发、单元测试 | Default（启动快、无数据库表） |
| 演示 Demo、POC | Default |
| 生产长流程（小时级 / 天级） | Flowable（自带断点续传） |
| 需要流程图可视化、审计 | Flowable |
| 需要定时器事件、消息事件 | Flowable（BPMN 原生支持） |
| 简单的同步 pipeline | 两者皆可，默认 Default |

**切换方式**：在 `pom.xml` 加 / 去掉 flowable starter 依赖即可，**代码零改动**。

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| 双引擎 | Default（轻量 DAG）+ Flowable（BPMN 标准） |
| `@Primary` | 有 Flowable 时优先注入它 |
| `@ConditionalOnBean(RepositoryService.class)` | classpath 没有 Flowable 就不装配 |
| `FlowableBpmnConverter` | YAML → BPMN 模型翻译官 |
| 扩展元素 `lumina:nodeDefinition` | YAML 节点 JSON 夹带进 BPMN，delegate 取回 |
| 三个 delegate | `agentDelegate` / `transformDelegate` / `loopDelegate` |
| `__` 前缀变量 | Lumina 内部变量，映射回 context 时过滤 |

### 自测题

1. 为什么 `FlowableWorkflowEngine` 用 `@ConditionalOnBean(RepositoryService.class)` 而不是直接 `@Component`？
   <details><summary>答案</summary>如果不加条件，classpath 没有 Flowable 依赖时，Spring 启动会因为找不到 `RepositoryService`、`RuntimeService` 等构造参数而失败。`@ConditionalOnBean` 让 Bean 仅在 Flowable 自动配置已经创建了 `RepositoryService` 时才装配——实现"有依赖用 Flowable，无依赖退回 Default"的优雅降级。</details>

2. YAML 的 `#category == 'refund'` 条件，转换到 BPMN 后是什么？
   <details><summary>答案</summary>`${category == 'refund'}`。转换器去掉 `#` 前缀（SpEL 变量引用符），用 Flowable 的 JUEL 语法包裹 `${}`。逻辑等价，只是表达式语言不同。</details>

3. YAML 节点的 `agentId`、`prompt` 等属性是怎么传到 delegate 的？为什么不直接放进 BPMN 标准字段？
   <details><summary>答案</summary>BPMN 标准字段（id、name、documentation）容纳不下 Lumina 节点的丰富属性。转换器把整个节点对象 JSON 序列化后塞进 `lumina:nodeDefinition` 扩展元素（BPMN 协议允许的自定义命名空间）。Delegate 执行时调用 `getNode(execution, AgentNode.class)` 从扩展元素反序列化取回完整对象。</details>

4. 如果一个工作流既要在生产用 Flowable，又要在单测中跑 Default，需要改 YAML 吗？
   <details><summary>答案</summary>不需要。YAML 是引擎无关的 DAG 定义，`DefaultWorkflowEngine` 和 `FlowableWorkflowEngine` 都吃同一个 `WorkflowDefinition`。测试时 classpath 不引入 flowable starter，Spring 自动注入 Default；生产环境引入依赖后，`@Primary` 让 Flowable 接管。同一份 YAML、同一份代码，零切换成本。</details>

> 🚀 [I04 — 人工审批 →](I04-human-in-the-loop.md)

---

📝 **本篇撰写期间修正的代码**：无。
