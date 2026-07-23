# 04 — @Transactional 事务传播机制

> **前置要求**：已完成 [03-AOP 代理](03-spring-aop-proxy.md)
> **预计阅读**：20 分钟
> **难度**：⭐⭐⭐⭐☆

---

## 面试题引入

> **"面试官：@Transactional 的传播机制有哪些？什么时候事务会失效？"**

---

## 表层回答（60 分）

7 种传播级别（REQUIRED/REQUIRES_NEW/NESTED...）。事务失效场景：自调用、非 public、异常被吞、异常类型不匹配。

---

## 深层原理（90 分）

### 7 种传播级别

| 传播级别 | 含义 | 类比 |
|----------|------|------|
| **REQUIRED**（默认） | 有事务就加入，没有就新建 | 跟着大部队走 |
| REQUIRES_NEW | 不管有没有，新建一个独立事务 | 另起炉灶 |
| NESTED | 嵌套事务（基于保存点） | 大事里套小事 |
| SUPPORTS | 有就加入，没有就非事务跑 | 随遇而安 |
| NOT_SUPPORTED | 非事务跑（挂起当前事务） | 不掺和 |
| MANDATORY | 必须在事务里（没就报错） | 强制要求 |
| NEVER | 不能在事务里（有就报错） | 独来独往 |

### 最常用的两个

#### REQUIRED（默认）

```java
@Service
public class AgentServiceImpl {
    @Transactional    // REQUIRED（默认）
    public Agent createAgent(Agent agent) {
        agentMapper.insert(agent);
        knowledgeBaseService.mount(agent);    // ← mount 如果也 @Transactional(REQUIRED)
        // mount 加入 createAgent 的事务，同一个事务
    }
}
// 如果 mount 抛异常 → createAgent 也回滚（同一事务）
```

#### REQUIRES_NEW

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void writeAuditLog() {
    // 不管外层有没有事务，我开一个新事务
    // 即使外层回滚，我的日志也保留
}
```

---

## 事务失效的 5 个坑

### 坑 1：自调用（最常见）

```java
@Service
public class Service {
    public void methodA() {
        this.methodB();    // ❌ methodB 的 @Transactional 失效！
    }
    @Transactional
    public void methodB() { ... }
}
```

**原因**：`this.methodB()` 调原始对象不是代理对象。和 AOP 同理（[03 篇](03-spring-aop-proxy.md)）。

### 坑 2：非 public 方法

```java
@Transactional
void method() { ... }    // ❌ 包级私有，CGLIB 无法重写，事务失效
```

### 坑 3：异常被 catch 吞掉

```java
@Transactional
public void method() {
    try {
        agentMapper.insert(bad);
    } catch (Exception e) {
        log.error("出错", e);
        // ❌ 异常被吞了，Spring 不知道出错了 → 不回滚！
    }
}
```

### 坑 4：rollbackFor 不匹配

```java
@Transactional    // 默认只回滚 RuntimeException
public void method() throws Exception {
    throw new IOException();    // ❌ 受检异常，默认不回滚！
}

// 正确写法
@Transactional(rollbackFor = Exception.class)    // ← 所有异常都回滚
public void method() throws Exception { ... }
```

### 坑 5：数据库引擎不支持

MySQL 的 MyISAM 引擎不支持事务。必须用 InnoDB。

---

## Lumina 的规范

```java
// 所有写操作都标注（AGENTS.md 检查清单要求）
@Transactional(rollbackFor = Exception.class)
public Agent createAgent(Agent agent) { ... }
```

**为什么 `rollbackFor = Exception.class`**：默认只回滚 RuntimeException，但受检异常也要回滚（防止坑 4）。

---

## 常见追问

### Q：REQUIRES_NEW 怎么实现"独立事务"？

**A**：Spring 把当前事务**挂起**（suspend），创建新数据库连接开新事务，新事务完成后**恢复**原事务。两个事务用不同的 Connection。

### Q：NESTED 和 REQUIRES_NEW 有什么区别？

**A**：NESTED 基于**保存点**（Savepoint）——子事务回滚到保存点，但外层事务可以继续。REQUIRES_NEW 是完全独立的两个事务，互不影响。

---

## 小结

| 知识点 | 一句话 |
|--------|--------|
| 7 种传播 | REQUIRED（默认）/ REQUIRES_NEW / NESTED 最常用 |
| 5 个失效坑 | 自调用/非public/吞异常/rollbackFor/引擎 |
| Lumina 规范 | `@Transactional(rollbackFor = Exception.class)` |
| REQUIRES_NEW | 挂起当前事务，新连接开独立事务 |

---

📝 **本篇撰写期间修正的代码**：无。
