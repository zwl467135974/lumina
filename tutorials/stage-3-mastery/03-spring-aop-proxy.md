# 03 — AOP 动态代理原理

> **前置要求**：已完成 [02-IoC 与 Bean 生命周期](02-spring-ioc-bean-lifecycle.md)
> **预计阅读**：20 分钟
> **难度**：⭐⭐⭐⭐☆

---

## 面试题引入

> **"面试官：Spring AOP 用的是 JDK 动态代理还是 CGLIB？@Audit 注解是怎么织入的？"**

---

## 表层回答（60 分）

Spring AOP 默认对接口用 JDK 动态代理，对类用 CGLIB。@Audit 通过 `@Around` 环绕通知，在方法执行前后插入日志逻辑。

---

## 深层原理（90 分）

### 两种代理方式

#### JDK 动态代理（基于接口）

```
目标类实现了接口 → Spring 创建一个实现相同接口的代理类
代理类内部持有目标类引用，方法调用时先执行切面逻辑再委托目标类
```

```java
// 原理（简化）
Object proxy = Proxy.newProxyInstance(
    classLoader,
    target.getClass().getInterfaces(),    // ← 必须有接口
    (p, method, args) -> {
        // 前置逻辑（如 @Audit 记录开始时间）
        Object result = method.invoke(target, args);    // 调原方法
        // 后置逻辑（如 @Audit 记录结果）
        return result;
    }
);
```

#### CGLIB（基于继承）

```
目标类没实现接口 → CGLIB 生成目标类的子类
子类重写方法，在重写的方法里插入切面逻辑
```

```
class TargetClass$$EnhancerByCGLIB extends TargetClass {
    @Override
    public void createAgent() {
        // 前置逻辑
        super.createAgent();    // 调父类（原方法）
        // 后置逻辑
    }
}
```

### Spring 怎么选

| 条件 | 用什么 |
|------|--------|
| 目标类有接口 | JDK 动态代理（默认） |
| 目标类无接口 | CGLIB |
| 强制用 CGLIB | `spring.aop.proxy-target-class=true` |

> 💡 Lumina 的 `@Service` 类通常实现了接口（如 `AgentServiceImpl implements AgentService`），所以默认走 JDK 代理。

---

## @Audit 怎么织入

### 切面代码

```java
// 文件：lumina-framework/.../audit/aspect/AuditAspect.java
@Aspect
@Component
public class AuditAspect {

    @Around("@annotation(audit)")    // ← 切点：所有标了 @Audit 的方法
    public Object auditAround(ProceedingJoinPoint point, Audit audit) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            Object result = point.proceed();    // ← 调用原方法
            return result;
        } finally {
            long duration = System.currentTimeMillis() - start;
            publishAuditEvent(point, audit, duration);    // 异步发布审计事件
        }
    }
}
```

### 你调用时实际发生了什么

```java
// 你写的代码
agentController.createAgent(dto);

// 实际执行的（你看到的代理对象）
auditAspect.auditAround(() -> {
    // 前置：记录开始时间、操作人
    Object result = agentController原始对象.createAgent(dto);
    // 后置：记录结束时间、结果、耗时
    return result;
});
```

**你调的是代理对象，代理对象帮你"包了一层"**。

---

## AOP 生效的条件（重要陷阱）

### 同类内部调用 → AOP 失效！

```java
@Service
public class SomeService {

    public void methodA() {
        this.methodB();    // ❌ AOP 失效！methodB 的 @Audit 不会触发
    }

    @Audit(module = "test", action = "CREATE")
    public void methodB() { ... }
}
```

**为什么**：`this.methodB()` 调的是原始对象（不是代理对象），代理逻辑被跳过了。

**解决**：注入自己再调，或用 `AopContext.currentProxy()`。

### 方法必须是 public

CGLIB 通过继承重写——`private`/`protected` 方法无法被重写，AOP 不生效。

---

## 常见追问

### Q：JDK 代理和 CGLIB 性能差多少？

**A**：CGLIB 创建代理慢（生成字节码），但方法调用快（直接调父类）。JDK 创建快但调用慢（反射）。现代场景差异可忽略。

### Q：@Transactional 也是 AOP 吗？

**A**：是的。`@Transactional` 也是通过 AOP 代理实现的——方法执行前开事务，执行后提交/回滚。和 `@Audit` 同理。

---

## 小结

| 知识点 | 一句话 |
|--------|--------|
| JDK 代理 | 基于接口，Proxy.newProxyInstance |
| CGLIB | 基于继承，生成子类 |
| 代理对象 | 容器存的是代理，你调的是代理 |
| AOP 生效条件 | 外部调用 + public 方法 |
| 同类调用失效 | this.method() 绕过代理 |

---

📝 **本篇撰写期间修正的代码**：无。
