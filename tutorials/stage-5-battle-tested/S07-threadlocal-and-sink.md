# S07 — 异步线程里的幽灵：ThreadLocal 怎么丢了

> **前置要求**：[06 ThreadLocal 上下文](../stage-3-mastery/06-threadlocal-context.md)、[07 线程池与异步](../stage-3-mastery/07-thread-pool-async.md)
> **预计阅读**：20 分钟
> **难度**：⭐⭐⭐⭐☆
> **对应真实代码**：`BaseContext` / `ToolUsageSink` / `DbToolUsageSink`

---

## 这节解决什么问题

一个经典悬案：多租户系统里，某张表的查询条件 `tenant_id = 3` 突然变成 `tenant_id = NULL`——A 租户的数据对 B 租户可见了。代码 review 完全正常：每个查询都走了租户插件。幽灵在哪？

答案是 ThreadLocal 在异步边界的丢失。这节课讲透它的机制，然后讲一个架构模式（Sink 接缝）——它既解决了"跨层依赖"问题，也顺手解决了幽灵问题的另一半。

---

## 第一层：工牌模型——ThreadLocal 怎么工作的

Lumina 的多租户实现：请求进来时，网关把 `tenantId`（当前用户属于哪个租户）存进 `BaseContext`——本质是个 ThreadLocal：

```java
// 文件：lumina-common/.../core/BaseContext.java（示意）
private static final ThreadLocal<LoginContext> CONTEXT = new ThreadLocal<>();
```

而 MyBatis-Plus 的租户插件在**每次 SQL 执行前**读取这个值，自动拼上 `WHERE tenant_id = ?`。

把 ThreadLocal 想象成**工牌挂在每个员工（线程）脖子上**：进入公司（处理请求）时领牌，SQL 安检门（租户插件）看到工牌才知道"你是 3 号租户的，只能看 3 号的柜子"。

关键性质：**工牌是每个线程私有的**。这条性质平时是优点（线程隔离，不会串），直到——

## 第二层：案发——线程池边界

Agent 的执行是异步的：提交任务后，工作交给线程池里的另一个线程去跑（Agent 循环要几十秒，不能占着 Web 线程）。问题来了：

```
Web 线程（脖子上挂着工牌 tenant=3）
    │  task 提交
    ▼
线程池线程（脖子上……什么都没有）    ← 幽灵在此
    │  执行 Agent 循环，触发 SQL
    ▼
租户插件读 BaseContext → null
    ▼
WHERE tenant_id = ?   →  ？= null   →  越权 / 报错 / 数据错乱
```

**工牌不会自己走到另一个员工脖子上**。ThreadLocal 的"线程隔离"优点，在线程切换的那一瞬间变成了数据断崖。这解释了悬案的一切：代码"完全正常"（同一线程内确实处处有租户上下文），bug 只在"跨线程"那条看不见的线上。

## 第三层：两个修法——传值与豁免

**修法一：显式捕获传递。** 提交任务时（还在 Web 线程上）把 tenantId 读出来，作为**数据**随任务对象一起传进异步线程。Lumina 的工具使用分析就是这么做的：

```java
// TraceCollector 在 startTrace 时（仍在原线程）捕获 tenantId，
// 之后异步落库线程用的是"随对象传递的值"，不再依赖 ThreadLocal
```

**修法二：豁免表（ALWAYS_IGNORE）。** 有些表是系统级全局表（如 `lumina_tool_usage` 工具调用明细），本来就不该拼租户条件。把它们登记进租户插件的忽略名单，隔离责任显式转移到"写入方/查询方自己带条件"。

两个修法常一起用，但**语义不同**：传值是"把上下文带过去"，豁免是"声明这张表不需要上下文"。搞混了会把该隔离的数据豁免掉——那不是修 bug，是埋雷（`@InterceptorIgnore` 注解绕过租户插件的每一次使用都应该有书面理由）。

## 第四层：Sink 接缝——顺路解决的架构问题

工具使用分析还有个更上层的难题：**谁写库？**

- 工具调用的发生地在 agent-core（引擎里）；
- 写库的能力（Mapper、DataSource）在 business-agent；
- 而 agent-core **不能依赖** business 模块（依赖方向只能向下）。

硬写的两个烂办法：agent-core 直接依赖 business（架构污染），或者引擎里定义一堆回调接口由调用方注入（散乱）。Lumina 的做法是一个很轻的模式——**Sink 接缝**：

```java
// 文件：lumina-agent-core/.../tracing/ToolUsageSink.java
public interface ToolUsageSink {
    void record(ToolUsageRecord record);   // 引擎只管"发生了什么"
}
```

```java
// 文件：lumina-business-agent/.../tracing/DbToolUsageSink.java
@Component
public class DbToolUsageSink implements ToolUsageSink {
    // business 层实现：落库到 lumina_tool_usage
}
```

这个模式的三个性质，每个都值得抄走：

1. **依赖倒置**：接口定义在使用方（core），实现在提供方（business）——Spring 装配自动连线，依赖方向不破；
2. **可选装配**：没注册实现？调用点判空跳过——agent-core 单独跑（比如被别的项目引用）不会崩。和 TraceSink 同构，第三个观测需求照葫芦画瓢五分钟接入；
3. **容错纪律**：Sink 里任何异常都被吃掉（记日志），**观测永远不能伤害主路径**——统计写库失败就把 Agent 执行搞挂，是本末倒置。

> 💡 判断一个扩展点设计得好不好，就看**加第三个消费者要不要改引擎**。Sink 模式的答案是：不要。（对照 [S01](S01-monotonic-guard.md) 的事件总线：加指标只加监听器——同一哲学。）

## 面试官会怎么追问

**Q：为什么不用 InheritableThreadLocal 或 TransmittableThreadLocal 一劳永逸？**
A：它们解决"创建子线程时复制上下文"，但线程池的线程是**复用**的，不是每次新建——第一次的上下文会"钉"在线程上，下个任务读到上个任务的租户（更隐蔽的串号）。TTL 需要包装 Runnable 且仍要显式传递。显式传值虽然啰嗦，但**数据流在代码里看得见**——分布式系统的铁律是显式优于隐式。

**Q：async 方法上标 @Async 不就行了？**
A：@Async 的线程同样没有 ThreadLocal（Spring 只帮你切线程，不搬上下文；需要额外配 TaskDecorator）。所有"异步"的本质都一样：**换线程 = 丢工牌**，无论语法糖多甜。

**Q：Sink 模式和 Spring 的 ApplicationEvent 有什么区别？什么时候选哪个？**
A：事件是广播（不关心谁听、可以多听），Sink 是定向消费语义（通常单实现、有明确契约）。 Lumina 里 AgentTurnEvent 用事件（观测者天然多个），ToolUsageSink 用接缝（一对一、强契约）。选型看消费关系的形状，不是看哪个更新。

## 自测题

1. **用"工牌"比喻 30 秒讲清 ThreadLocal 在异步边界丢失的机制。**
   <details><summary>答案</summary>ThreadLocal 是每线程私有的储物柜（工牌挂脖子上）。任务从 Web 线程交给线程池线程时，新线程的柜子是空的——租户 ID 留在了旧线程身上。SQL 执行线程读到 null，隔离条件失效。</details>

2. **ALWAYS_IGNORE 豁免和显式传值，语义差异是什么？误用豁免的后果？**
   <details><summary>答案</summary>传值=把租户上下文带过线程边界（隔离继续生效）；豁免=声明该表不参与租户隔离（隔离责任转移到业务代码）。误用豁免：本该隔离的表裸奔，跨租户读写；且 @InterceptorIgnore 绕过是无声的，review 时极难发现。</details>

3. **Sink 模式怎么同时解决"依赖方向"和"可选性"两个问题？**
   <details><summary>答案</summary>接口定义在依赖方（agent-core）、实现在被依赖层（business），Spring 装配注入——依赖方向不破（倒置）；调用点对 Sink 判空，无实现时跳过——core 可独立运行（可选）。</details>

4. **"观测写库失败不能影响主路径"在 Sink 实现里怎么落实？**
   <details><summary>答案</summary>record() 内部全量 try-catch，异常记 warn 日志后吞掉（可加失败计数器）。理由：观测是旁路职责，它失败的成本应该只是"少了一条统计"，绝不能是"Agent 执行失败"。</details>

---

**下一篇**：[S08 — 会给自己的系统找茬](S08-self-review.md)——前七篇教你把系统做对，最后一篇教你回答那个最难的问题："你做过的东西里，哪里是烂的？"
