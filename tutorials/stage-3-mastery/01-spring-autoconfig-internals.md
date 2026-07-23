# 01 — Spring Boot 自动配置机制（源码级）

> **前置要求**：已完成[第一阶](../stage-1-foundation/README.md) + [第二阶](../stage-2-application/README.md)
> **预计阅读**：25 分钟
> **难度**：⭐⭐⭐⭐☆

---

## 面试题引入

> **"面试官：Spring Boot 的自动配置是怎么实现的？@SpringBootApplication 注解到底做了什么？"**

---

## 表层回答（60 分）

`@SpringBootApplication` = `@SpringBootConfiguration` + `@EnableAutoConfiguration` + `@ComponentScan`。自动配置通过读取 `META-INF/spring/...AutoConfiguration.imports` 文件加载配置类。

---

## 深层原理（90 分）

### 启动入口到底做了什么

```
@SpringBootApplication
    ├── @SpringBootConfiguration → 标记这是配置类（里面能 @Bean）
    ├── @ComponentScan → 扫描当前包及子包的 @Component
    └── @EnableAutoConfiguration → 核心：触发自动配置
```

### @EnableAutoConfiguration 的秘密

```java
// Spring Boot 源码（简化）
@Import(AutoConfigurationImportSelector.class)
public @interface EnableAutoConfiguration { ... }
```

`AutoConfigurationImportSelector` 做了什么：

```
1. 读取 META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
2. 拿到所有自动配置类的全限定名
3. 逐个检查 @ConditionalOnXxx 条件
4. 满足条件的 → 加载（注册为 Bean）
5. 不满足的 → 跳过
```

### 在 Lumina 里看这个文件

```
# 文件：lumina-framework/src/main/resources/META-INF/spring/
#       org.springframework.boot.autoconfigure.AutoConfiguration.imports

io.lumina.framework.config.JacksonConfig
io.lumina.framework.config.MyBatisPlusConfig
io.lumina.framework.config.RedisConfig
io.lumina.framework.config.WebMvcConfig
io.lumina.framework.config.SpringDocConfig
io.lumina.framework.cache.RedisCacheManager
io.lumina.framework.context.ContextPropagationAutoConfiguration
io.lumina.framework.web.LogWebMvcAutoConfiguration
```

**8 行，一行一个类名**。任何依赖 `lumina-framework` 的模块启动时，这 8 个配置类被自动加载。

### Spring Boot 3.x vs 2.x 的变化

| | Spring Boot 2.x | Spring Boot 3.x |
|---|---|---|
| 文件 | `META-INF/spring.factories` | `META-INF/spring/...AutoConfiguration.imports` |
| 格式 | key=value | 一行一个类名 |
| 性能 | 解析全量 factories | 只读自动配置，更快 |

> ⚠️ 网上旧教程还讲 `spring.factories`，那是 2.x 的。Lumina 用 3.3.5，是新方式。

---

## 条件装配的执行顺序

```
SpringBoot 启动
  ↓
加载所有 AutoConfiguration 类名
  ↓
对每个类依次检查：
  ├── @ConditionalOnClass → classpath 有没有这个类？
  ├── @ConditionalOnBean → 容器里有没有这个 Bean？
  ├── @ConditionalOnProperty → 配置项满不满足？
  └── @ConditionalOnMissingBean → 容器里"没有"这个 Bean？
  ↓
全满足 → 加载
有一个不满足 → 跳过
```

### Lumina 实例

```java
// 文件：FlowableWorkflowEngine.java:77
@ConditionalOnBean(RepositoryService.class)
// → 只有项目引入了 Flowable（RepositoryService 才存在）才加载
// → standalone 模式没引 Flowable → 跳过 → 用 DefaultWorkflowEngine
```

---

## 常见追问

### Q：@ConditionalOnMissingBean 为什么重要？

**A**：它实现"用户优先"。框架提供默认实现，但标记 `@ConditionalOnMissingBean`——如果用户自己定义了同类型 Bean，框架的默认就不加载。

```java
// 文件：RedisConfig.java
@Bean
@ConditionalOnMissingBean    // 如果业务代码没自定义 RedissonClient，才用这个默认的
public RedissonClient redissonClient() { ... }
```

### Q：自动配置类加载顺序怎么保证？

**A**：`@AutoConfigureBefore` / `@AutoConfigureAfter` / `@AutoConfigureOrder` 控制顺序。但不建议依赖顺序——用 `@ConditionalOnBean` 让"依赖关系"自然保证顺序。

---

## 小结

| 知识点 | 一句话 |
|--------|--------|
| 入口 | @EnableAutoConfiguration → AutoConfigurationImportSelector |
| 文件 | AutoConfiguration.imports（3.x）/ spring.factories（2.x） |
| 过滤 | @ConditionalOnXxx 逐个检查 |
| 用户优先 | @ConditionalOnMissingBean 让用户定义覆盖默认 |

---

## 自测题

1. **Spring Boot 3.x 用什么文件注册自动配置？2.x 呢？**
   <details><summary>答案</summary>3.x 用 META-INF/spring/...AutoConfiguration.imports（一行一个类名）；2.x 用 META-INF/spring.factories（key=value）。</details>

2. **@ConditionalOnMissingBean 解决什么问题？**
   <details><summary>答案</summary>"用户优先"——框架提供默认 Bean，但如果用户自定义了同类型 Bean，框架默认不加载。让默认实现可被覆盖。</details>

---

📝 **本篇撰写期间修正的代码**：无。
