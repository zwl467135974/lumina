# 06 — ThreadLocal 上下文传播

> **前置要求**：已完成 [05-MyBatis 拦截器](05-mybatis-interceptor-internals.md)
> **预计阅读**：20 分钟
> **难度**：⭐⭐⭐⭐☆

---

## 面试题引入

> **"面试官：ThreadLocal 是什么？Lumina 怎么用它传递租户上下文？有什么内存泄漏风险？"**

---

## 表层回答（60 分）

ThreadLocal 给每个线程一份独立的变量副本。Lumina 用它存当前请求的 userId/tenantId。内存泄漏风险：线程池复用线程，不清理会残留。

---

## 深层原理（90 分）

### ThreadLocal 是什么

```java
// 每个 Thread 对象内部有一个 ThreadLocalMap
class Thread {
    ThreadLocalMap threadLocals;    // ← 每个线程自己的"信箱"
}
```

```
Thread-1: { BaseContext → {userId:1, tenantId:0} }
Thread-2: { BaseContext → {userId:2, tenantId:1} }
Thread-3: { BaseContext → null（没设置） }
```

**每个线程有自己独立的副本，互不干扰**——这就是"线程隔离"。

---

## Lumina 的 BaseContext

```java
// 文件：lumina-common/.../core/BaseContext.java（简化）
public class BaseContext {
    private static final ThreadLocal<LoginContext> CONTEXT = new ThreadLocal<>();

    public static void initFromHeaders(String userId, String username,
                                       String tenantId, String roles, String permissions) {
        LoginContext ctx = new LoginContext();
        ctx.setUserId(Long.valueOf(userId));
        ctx.setTenantId(Long.valueOf(tenantId));
        ctx.setRoles(roles);
        ctx.setPermissions(permissions);
        CONTEXT.set(ctx);                    // ← 存入当前线程的 ThreadLocal
    }

    public static Long getTenantId() {
        LoginContext ctx = CONTEXT.get();     // ← 从当前线程取
        return ctx != null ? ctx.getTenantId() : null;
    }

    public static void clear() {
        CONTEXT.remove();                     // ← 清理！防止泄漏
    }
}
```

---

## 请求生命周期

```
HTTP 请求进来（分配 Thread-1）
  ↓
TenantIsolationInterceptor.preHandle()
  → BaseContext.initFromHeaders(...)     ← 写入 Thread-1 的 ThreadLocal
  ↓
Controller → Service → Mapper
  → BaseContext.getTenantId()            ← 从 Thread-1 的 ThreadLocal 读
  → MyBatis 拦截器用它改写 SQL
  ↓
TenantIsolationInterceptor.afterCompletion()
  → BaseContext.clear()                  ← 清理！
  ↓
Thread-1 归还线程池（给下一个请求复用）
```

---

## 内存泄漏风险

### 问题：线程池 + 不清理

Tomcat 用线程池——线程用完不销毁，复用给下一个请求。如果**不 clear**：

```
请求 A（admin, tenant=0）→ Thread-1 的 ThreadLocal = {tenantId:0}
请求 A 结束，Thread-1 归还
请求 B（普通用户, tenant=1）→ 复用 Thread-1
  → BaseContext.getTenantId() → 读到 0（上一个请求残留的！）
  → 用 admin 的 tenantId 查数据 → 安全漏洞！
```

### 解决：afterCompletion 必须 clear

```java
// 文件：TenantIsolationInterceptor.java
@Override
public void afterCompletion(...) {
    BaseContext.clear();    // ← 必须清理！
}
```

---

## ThreadLocal 与 Reactor 的冲突

### 问题

Reactor（Flux/Mono）在异步线程上执行——ThreadLocal 的值传不过去！

```
Thread-1（HTTP 线程）: BaseContext = {tenantId:0}
  ↓ flatMap 切到 Thread-2（Reactor 线程）
Thread-2: BaseContext = null ← 拿不到！
```

### Lumina 的解决：Context Propagation

```java
// 文件：ContextPropagationAutoConfiguration.java
@PostConstruct
public void enable() {
    Hooks.enableAutomaticContextPropagation();    // ← 自动传播
}
```

`enableAutomaticContextPropagation` 让 Reactor 在线程切换时**自动复制 ThreadLocal** 到新线程。

---

## 常见追问

### Q：ThreadLocalMap 的 key 是弱引用还是强引用？

**A**：key 是**弱引用**（WeakReference），value 是**强引用**。key 被回收后 value 还在 → 内存泄漏。所以必须手动 `remove()`。

### Q：InheritableThreadLocal 和 ThreadLocal 有什么区别？

**A**：`InheritableThreadLocal` 子线程能继承父线程的值。但线程池场景下（线程预创建）不生效——所以 Lumina 用 Reactor 的 Context Propagation 而不是 InheritableThreadLocal。

---

## 小结

| 知识点 | 一句话 |
|--------|--------|
| ThreadLocal | 每线程独立副本，互不干扰 |
| BaseContext | 存 userId/tenantId，全链路传递 |
| 必须 clear | 线程池复用，不清理会残留→安全漏洞 |
| Reactor 冲突 | 异步线程拿不到 ThreadLocal → Context Propagation 解决 |
| key 弱引用 | ThreadLocalMap 的 key 回收后 value 泄漏 |

---

📝 **本篇撰写期间修正的代码**：无。
