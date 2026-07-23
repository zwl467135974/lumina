# 第三阶：原理深潜 — "八股文大师"

> **目标**：学完这 15 篇，你深入理解底层原理，面试能讲清"为什么"，理论+实践完全掌握。
>
> **前置要求**：已完成[第一阶](../stage-1-foundation/README.md)和[第二阶](../stage-2-application/README.md)，能熟练读懂 Lumina 代码。
>
> **预计总时长**：15-20 小时（含源码追踪）

---

## 这阶讲什么

前两阶你学会了"怎么用"和"为什么这么设计"。这阶回答最后的问题：**"它底层到底是怎么工作的？"**

面试官最爱问的就是原理：
- "Spring Bean 为什么默认单例？"
- "@Transactional 的传播机制有哪些？"
- "MyBatis 多租户拦截器怎么改写 SQL 的？"
- "Redis 分布式锁的 RedLock 有什么争议？"
- "响应式编程的背压是什么？"

这阶每篇都以**面试题开篇**，跟到 Lumina 代码 + 框架源码关键路径，让你不仅会用，还能讲清原理。

---

## 学习路线

### 🧬 框架原理（01-04）

| # | 标题 | 核心面试考点 | 难度 |
|---|------|-------------|------|
| 01 | [自动配置机制](01-spring-autoconfig-internals.md) | AutoConfiguration.imports 怎么被加载？条件装配执行顺序 | ⭐⭐⭐⭐ |
| 02 | [IoC + Bean 生命周期](02-spring-ioc-bean-lifecycle.md) | 三级缓存解循环依赖、Bean 初始化全流程 | ⭐⭐⭐⭐⭐ |
| 03 | [AOP 动态代理](03-spring-aop-proxy.md) | JDK 动态代理 vs CGLIB、@Audit 怎么织入的 | ⭐⭐⭐⭐ |
| 04 | [事务传播机制](04-transaction-propagation.md) | 7 种传播级别、rollbackFor、事务失效的 5 个坑 | ⭐⭐⭐⭐ |

### ⚙️ 核心机制（05-07）

| # | 标题 | 核心面试考点 | 难度 |
|---|------|-------------|------|
| 05 | [MyBatis 拦截器](05-mybatis-interceptor-internals.md) | 拦截器责任链、SQL 改写（多租户 tenant_id 怎么注入） | ⭐⭐⭐⭐⭐ |
| 06 | [ThreadLocal 上下文](06-threadlocal-context.md) | BaseContext 怎么跨层传租户、内存泄漏风险、Reactor 上下文传播 | ⭐⭐⭐⭐ |
| 07 | [线程池](07-thread-pool-async.md) | ThreadPoolExecutor 参数、4 种拒绝策略、异步任务线程池配置 | ⭐⭐⭐⭐ |

### 🌊 IO 与并发（08-10）

| # | 标题 | 核心面试考点 | 难度 |
|---|------|-------------|------|
| 08 | [响应式编程](08-reactor-reactive.md) | Flux/Mono、背压、Context Propagation（SSE 流式底层） | ⭐⭐⭐⭐⭐ |
| 09 | [HTTP/SSE 协议层](09-http-sse-protocol.md) | chunked transfer、keep-alive、SSE 半关闭、代理缓冲 | ⭐⭐⭐⭐ |
| 10 | [缓存模式](10-cache-pattern.md) | 缓存穿透/雪崩/击穿、RedisCacheManager 三层防线 | ⭐⭐⭐⭐ |

### 🔒 分布式（11-14）

| # | 标题 | 核心面试考点 | 难度 |
|---|------|-------------|------|
| 11 | [分布式锁](11-distributed-lock-theory.md) | SETNX→RedLock→Redisson 看门狗、CAP 取舍 | ⭐⭐⭐⭐⭐ |
| 12 | [JWT 安全纵深](12-jwt-security-deep.md) | 签名算法、过期刷新、黑名单、Header 防伪造 | ⭐⭐⭐⭐ |
| 13 | [容错模式](13-resilience-pattern.md) | 熔断状态机（Closed→Open→Half-Open）、限流算法、降级 | ⭐⭐⭐⭐ |
| 14 | [工作流引擎](14-workflow-engine-internals.md) | DAG 拓扑排序、BPMN 标准、Flowable 引擎、循环/并行执行 | ⭐⭐⭐⭐⭐ |

### 📝 面试速查

| # | 标题 | 内容 |
|---|------|------|
| 15 | [面试八股速查](15-interview-cheatsheet.md) | 40 个考点 × 项目实例定位，面试前一晚看这个 |

---

## 每篇的结构（面试导向）

```
## 面试题引入
"面试官问：Spring Bean 为什么默认单例？"

## 表层回答（60 分）
能用的回答，面试 60 分及格

## 深层原理（90 分）
跟到源码关键路径，讲清来龙去脉

## Lumina 里的实际体现
本项目哪个文件用了这个原理（给完整路径）

## 常见追问
面试官的 3 个连环追问 + 满分回答

## 自测题
```

---

## 核心原理速览（这阶最重要的 5 个原理）

### 1. 循环依赖 → 三级缓存
A 依赖 B，B 依赖 A，Spring 怎么创建？答案：三级缓存——先暴露"半成品 A"，B 拿到半成品完成创建，A 再完成。
> 详见 [02-spring-ioc-bean-lifecycle](02-spring-ioc-bean-lifecycle.md)

### 2. AOP → 动态代理
`@Audit` 注解怎么自动记录日志？Spring 用动态代理包了一层"壳"——你调 `createAgent()`，实际执行的是代理对象的 `createAgent()`，先记日志再调原方法。
> 详见 [03-spring-aop-proxy](03-spring-aop-proxy.md)

### 3. SQL 改写 → MyBatis 拦截器
`SELECT * FROM agent` 怎么自动变成 `SELECT * FROM agent WHERE tenant_id = 1`？拦截器在 SQL 执行前拦截，用 JSqlParser 解析改写。
> 详见 [05-mybatis-interceptor-internals](05-mybatis-interceptor-internals.md)

### 4. ThreadLocal → 线程隔离的"信箱"
每个线程有自己的 `BaseContext`，存着当前用户 ID 和租户 ID。请求处理全程都从这里取，不需要层层传参。
> 详见 [06-threadlocal-context](06-threadlocal-context.md)

### 5. 背压 → 生产者和消费者的速度协调
SSE 流式输出时，LLM 生成速度快于前端渲染速度怎么办？Reactor 的背压机制让消费者"按需拉取"，不会内存爆炸。
> 详见 [08-reactor-reactive](08-reactor-reactive.md)

---

## 面试速查表亮点（第 15 篇）

这篇是**面试前的救命稻草**。40 个高频考点，每个都标注：

| 考点 | 一句话答案 | Lumina 实例位置 |
|------|-----------|-----------------|
| Spring Bean 作用域 | 默认单例，@Scope 可改 | 所有 @Service |
| @Transactional 失效场景 | 自调用/非 public/异常被吞 | AgentServiceImpl |
| MyBatis 一级/二级缓存 | 一级=session、二级=mapper | （项目未启用二级） |
| ... | ... | ... |

> 详见 [15-interview-cheatsheet](15-interview-cheatsheet.md)

---

## 自测：学完这阶你应该能做到

- [ ] 能画出 Spring Bean 从实例化到销毁的完整生命周期图
- [ ] 能解释 JDK 动态代理和 CGLIB 的区别及选择策略
- [ ] 能讲清 @Transactional 的 7 种传播级别
- [ ] 能手写一个 MyBatis 拦截器改写 SQL
- [ ] 能解释 Redis 分布式锁的 RedLock 算法及争议
- [ ] 能讲清 Reactor 背压机制
- [ ] 面试时遇到 Lumina 用过的技术，都能讲出底层原理

> 全部做到？恭喜，你是八股文大师了 🎓。
> 别忘了还有 [AI 专项](../stage-4-ai-agent/README.md)——Lumina 的核心卖点。

> 🚀 **现在开始**：打开 [01-spring-autoconfig-internals.md](01-spring-autoconfig-internals.md)。
