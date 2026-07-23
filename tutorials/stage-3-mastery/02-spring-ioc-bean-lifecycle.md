# 02 — IoC 容器与 Bean 生命周期

> **前置要求**：已完成 [01-自动配置](01-spring-autoconfig-internals.md)
> **预计阅读**：25 分钟
> **难度**：⭐⭐⭐⭐⭐

---

## 面试题引入

> **"面试官：讲一下 Spring Bean 的完整生命周期。循环依赖是怎么解决的？"**

---

## 表层回答（60 分）

Bean 生命周期：实例化 → 属性注入 → 初始化 → 使用 → 销毁。循环依赖用三级缓存解决。

---

## 深层原理（90 分）

### Bean 生命周期 9 步

```
1. 实例化（new）           → 调构造器，对象创建出来
2. 属性注入（populate）     → @Autowired / @Value 注入依赖
3. BeanNameAware          → 注入 bean 名字
4. BeanFactoryAware       → 注入工厂引用
5. ApplicationContextAware → 注入上下文
6. BeanPostProcessor前置  → postProcessBeforeInitialization
7. 初始化                  → @PostConstruct → InitializingBean.afterPropertiesSet → @Bean(initMethod)
8. BeanPostProcessor后置  → postProcessAfterInitialization（AOP 代理在这里生成！）
9. 使用 → 销毁             → @PreDestroy → DisposableBean.destroy → @Bean(destroyMethod)
```

### 关键：AOP 代理在"第 8 步"生成

```
第 1-7 步：你拿到的是"原始对象"
第 8 步：BeanPostProcessor 后置处理 → 如果需要 AOP → 替换成"代理对象"
第 9 步：容器里存的是"代理对象"
```

> 这就是为什么 `@Audit` 能工作——容器里存的是代理对象，你调用方法实际调用的是代理。

---

## 三级缓存解循环依赖

### 问题

```java
@Service A { @Autowired B b; }    // A 依赖 B
@Service B { @Autowired A a; }    // B 依赖 A
```

创建 A 需要 B，创建 B 需要 A——死循环？

### 三级缓存

| 缓存 | 存什么 | 名字 |
|------|--------|------|
| 一级 | 完整的 Bean（初始化完毕） | singletonObjects |
| 二级 | 提前暴露的 Bean（半成品） | earlySingletonObjects |
| 三级 | Bean 工厂（能生产 Bean） | singletonFactories |

### 解决过程

```
1. 创建 A → 实例化 A（半成品）→ 把 A 的工厂放入三级缓存
2. A 需要注入 B → 去创建 B
3. 创建 B → 实例化 B → B 需要注入 A
4. 查一级缓存 → 没有 A（A 还没创建完）
5. 查二级缓存 → 没有
6. 查三级缓存 → 有 A 的工厂！→ 调工厂生产 A（半成品）→ 放入二级缓存
7. B 拿到 A（半成品）→ B 完成创建 → 放入一级缓存
8. A 拿到 B（完整）→ A 完成创建 → 放入一级缓存
```

**核心**：A 先暴露"半成品"到三级缓存，B 拿到半成品能完成创建，A 再拿完整的 B 完成创建。

### 为什么要三级不是两级

三级缓存存的是**工厂**（ObjectFactory），不是 Bean 本身。如果 A 需要 AOP 代理，工厂可以在暴露半成品时就生成代理——保证 B 拿到的和最终容器里的 A 是**同一个代理对象**。

---

## Lumina 里的体现

### @PostConstruct

```java
// 文件：TenantLineHandlerImpl.java:54
@PostConstruct
public void detectTenantTables() {
    // Bean 初始化后（第 7 步）自动执行
    // 查 information_schema 检测哪些表有 tenant_id
}
```

### 构造器注入 vs 字段注入（回顾）

> 📖 详见[第一阶 04-Spring Boot 基础](../stage-1-foundation/04-spring-boot-basics.md)。

构造器注入（`@RequiredArgsConstructor`）在第 1 步就完成——**没有循环依赖问题也能被检测到**。字段注入（`@Autowired`）在第 2 步——循环依赖被三级缓存"隐藏"了，可能掩盖设计问题。

---

## 常见追问

### Q：构造器注入的循环依赖能解决吗？

**A**：**不能！** 三级缓存只解决 setter/字段注入的循环依赖。构造器注入的循环依赖会直接报错——这其实是好事，强迫你重新设计。

### Q：Bean 默认单例还是多例？

**A**：默认单例（`@Scope("singleton")`）。Lumina 所有 `@Service`/`@Component` 都是单例——所以**不要在 Bean 里存实例状态**（用 ThreadLocal 或方法参数传递）。

---

## 小结

| 知识点 | 一句话 |
|--------|--------|
| 9 步生命周期 | 实例化→注入→Aware→前置→初始化→后置→使用→销毁 |
| AOP 代理 | 第 8 步后置处理生成 |
| 三级缓存 | singletonObjects → earlySingletonObjects → singletonFactories |
| 循环依赖 | A 暴露半成品到三级缓存，B 拿到后完成创建 |
| 构造器循环 | 无法解决（直接报错），强制重新设计 |

---

📝 **本篇撰写期间修正的代码**：无。
