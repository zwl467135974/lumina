# 15 — 技术选型决策

> **前置要求**：已完成 [14-Git 规范](14-git-commit-convention.md)
> **预计阅读**：20 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

你学了 Spring Boot、MyBatis-Plus、Redis、Vue……但为什么选这些而不是别的？面试官最爱问"为什么选 A 不选 B"。这节讲 Lumina 每个选型的**取舍分析**。

---

## 后端选型

### MyBatis-Plus vs JPA/Hibernate

| | MyBatis-Plus | JPA/Hibernate |
|---|---|---|
| SQL 控制 | ✅ 灵活（想写就写） | ❌ 自动生成难优化 |
| 学习曲线 | 低 | 高 |
| 复杂查询 | Lambda Wrapper 链式 | Criteria API 难写 |
| **Lumina 选择** | **✅** | |

**理由**：企业级场景经常有复杂 SQL，MyBatis-Plus 保留 SQL 控制力，又有零 SQL 的便捷。JPA 太"魔法"，出问题难调。

### Redisson vs Jedis

| | Redisson | Jedis |
|---|---|---|
| 分布式锁 | ✅ RLock 内置 | ❌ 要自己实现 SETNX |
| 原子操作 | ✅ RAtomicLong | ❌ 要自己拼 Lua |
| API 风格 | Java 对象风格 | Redis 命令风格 |
| **Lumina 选择** | **✅** | |

**理由**：分布式锁、限流、原子计数是刚需。Redisson 封装好了，Jedis 要自己造轮子。

### Resilience4j vs Hystrix

| | Resilience4j | Hystrix |
|---|---|---|
| 维护状态 | ✅ 活跃 | ❌ 已停止维护 |
| 编程模型 | 函数式 | 注解式 |
| **Lumina 选择** | **✅** | |

**理由**：Hystrix 已经不维护了。Resilience4j 是官方推荐的替代。

### Flowable vs 自研 DAG 引擎

**Lumina 的选择**：**两个都要**。

```java
@Primary
@ConditionalOnBean(RepositoryService.class)
public class FlowableWorkflowEngine implements WorkflowEngine { ... }
// 有 Flowable 用 Flowable（BPMN 标准引擎）

public class DefaultWorkflowEngine implements WorkflowEngine { ... }
// 没有 Flowable 用自研 DAG（轻量，不引依赖）
```

**理由**：Flowable 功能强但重（带一整套 BPMN 表）。standalone 模式不需要时用自研的轻量引擎，生产环境需要 BPMN 标准时引入 Flowable。**优雅降级**。

---

## 前端选型

### Vue 3 vs React

| | Vue 3 | React |
|---|---|---|
| 学习曲线 | 低（模板直观） | 中（JSX + hooks） |
| Element Plus | ✅ 企业级组件库 | Ant Design 同级 |
| 中国生态 | ✅ 强 | 强 |
| **Lumina 选择** | **✅** | |

**理由**：面向中国 ToB 市场，Vue + Element Plus 是最主流的企业级技术栈。团队上手快。

### pnpm vs npm

| | pnpm | npm |
|---|---|---|
| 安装速度 | ✅ 快 3 倍 | 慢 |
| 磁盘占用 | ✅ 省去重 | 重复存 |
| **Lumina 选择** | **✅** | |

---

## 架构选型

### 单体 vs 微服务

**Lumina 的选择**：**都支持**。standalone 单体 + 微服务双模式，共享同一套业务代码。

**理由**：企业客户有的要快速 PoC（单体），有的要生产扩容（微服务）。用 Profile + @ConditionalOnXxx 实现同一套代码两种部署。

### 多租户：共享数据库 vs 独立数据库

| | 共享数据库 + tenant_id | 独立数据库 |
|---|---|---|
| 成本 | ✅ 低 | 高（每租户一套库） |
| 隔离 | 行级隔离 | 物理隔离 |
| 运维 | ✅ 简单 | 复杂 |
| **Lumina 选择** | **✅** | |

**理由**：中国 ToB SaaS 最常见的模式——成本低、运维简单。tenant_id 行级隔离 + MyBatis 拦截器自动注入。

---

## 选型的核心原则

1. **选活跃维护的**（不选已停更的，如 Hystrix）
2. **选生态成熟的**（Element Plus > 小众 UI 库）
3. **选团队能上手的**（Vue > React，对于 Java 团队）
4. **选可降级的**（Flowable > 只用 Flowable）
5. **不为技术而技术**（够用就好，不追新）

---

## 🎉 第二阶全部完成！

你已经完成了第二阶全部 15 篇。你现在能：
- 理解 Lumina 每个设计决策的"为什么"
- 从零开发完整的前后端功能
- 做技术选型的取舍分析
- 达到中级开发水平

---

## 下一步

两个推荐路径：

1. **[AI 专项](../stage-4-ai-agent/README.md)** — 深入 Lumina 的核心：AI Agent。从 [A01-LLM 基础](../stage-4-ai-agent/A01-llm-fundamentals.md) 开始（推荐）
2. **[第三阶：原理深潜](../stage-3-mastery/README.md)** — 如果你想先搞懂底层原理（Spring IoC/AOP/事务/分布式锁）

> 🚀 **推荐**：进入 [AI 专项](../stage-4-ai-agent/README.md)——Lumina 是 AI 平台，AI 是它的灵魂。

---

📝 **本篇撰写期间修正的代码**：无。
