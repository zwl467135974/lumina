# 04 — Spring Boot 基础

> **前置要求**：已完成 [03-Maven 多模块](03-maven-modules.md)
> **预计阅读**：30 分钟
> **难度**：⭐⭐☆☆☆

---

## 这节解决什么问题

你打开 Lumina 的任意一个 Java 类，会看到一堆"注解"——`@Service`、`@Autowired`、`@RestController`、`@Configuration`……这些是什么意思？为什么加上一个注解，对象就自动创建好了、依赖就自动注入了？

这节用最直白的类比讲清楚 Spring Boot 的三大核心概念：**IoC（控制反转）、DI（依赖注入）、AOP（面向切面）**。理解了这三个，Lumina 里 80% 的代码你都能看懂。

---

## 先建立直觉：Spring Boot 到底帮你做了什么

假设没有 Spring，你写一个 Agent 管理功能，代码大概长这样：

```java
// 没有 Spring 的世界：一切自己 new
public class AgentController {
    private AgentService agentService;

    public AgentController() {
        // 自己创建依赖，还要创建依赖的依赖……套娃地狱
        AgentMapper mapper = new AgentMapper();
        this.agentService = new AgentServiceImpl(mapper);
    }
}
```

问题很明显：**Controller 要知道 Service 怎么构造、Service 要知道 Mapper 怎么构造**——层层耦合，改一个就要改一串。

Spring Boot 说：**"你别管怎么创建了，告诉我你需要谁，我给你送过来。"**

```java
// Spring Boot 的世界：声明你需要什么就行
@RestController
public class AgentController {
    private final AgentService agentService;  // 声明需要什么

    // Spring 自动把 AgentService 的实例传进来（依赖注入）
    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }
}
```

这就是 Spring 的核心价值——**你只管写业务逻辑，对象的创建和组装交给 Spring**。

---

## 概念一：IoC 容器（Spring 是"大管家"）

### 类比：公司的行政部

想象一家公司：
- **没有行政部**：每个员工自己去买电脑、找工位、申请账号——焦头烂额
- **有行政部**：你入职时行政部已经把电脑、工位、账号全部配好——你直接来上班就行

**Spring 的 IoC 容器就是这个行政部**。它负责：
1. 创建对象（买电脑）
2. 管理对象的生命周期（管电脑维修/报废）
3. 把对象分配给需要的人（发电脑给你）

### 对象 = Bean

在 Spring 世界里，被容器管理的对象叫 **Bean**。一个 Bean 就是一个由 Spring 创建和管理的 Java 对象。

### 怎么告诉 Spring "请你管这个对象"？

用注解。Lumina 里最常见的：

| 注解 | 含义 | 类比 |
|------|------|------|
| `@Component` | 通用组件，请 Spring 管理 | "我是公司一员，给我配工位" |
| `@Service` | 业务逻辑层 | "我是业务部门的" |
| `@Repository` / `@Mapper` | 数据访问层 | "我是仓库管理员" |
| `@Controller` / `@RestController` | 接口层 | "我是前台接待" |
| `@Configuration` | 配置类（里面用 @Bean 声明对象） | "我是行政部门，负责分配资源" |

> 💡 `@Service`、`@Controller` 本质上都是 `@Component` 的特化版——它们都告诉 Spring"请管理这个类"。不同名字只是语义标记，方便你一眼看出这个类属于哪一层。

### 在 Lumina 里长啥样

```java
// 文件：lumina-modules/lumina-business-agent/.../service/impl/BudgetServiceImpl.java
@Slf4j
@Service                              // ← 告诉 Spring："请管理这个类，它是一个 Service"
@RequiredArgsConstructor             // ← Lombok 注解，自动生成构造器（下一节讲）
public class BudgetServiceImpl implements BudgetService {

    private final BudgetRuleMapper budgetRuleMapper;    // ← Spring 会自动注入这个依赖
    private final AgentTaskMapper agentTaskMapper;
    private final RedisCacheManager redisCacheManager;

    // @RequiredArgsConstructor 自动生成了这个构造器：
    // public BudgetServiceImpl(BudgetRuleMapper budgetRuleMapper, ...) { ... }
    // Spring 看到这个构造器，就自动把对应的 Bean 传进来
}
```

---

## 概念二：依赖注入（DI）

### 类比：外卖配送

你需要吃午饭（依赖食物），有两种方式：
- **自己去做**（`new` 对象）：去菜市场买菜、做饭、洗碗
- **点外卖**（依赖注入）：在 App 下单，骑手送到门口

**依赖注入 = Spring 当骑手，把你要的对象送到你的构造器里。**

### 三种注入方式

#### ✅ 方式一：构造器注入（推荐，Lumina 标准做法）

```java
@Service
@RequiredArgsConstructor    // Lombok 自动生成带 final 字段的构造器
public class BudgetServiceImpl implements BudgetService {
    private final BudgetRuleMapper budgetRuleMapper;  // final = 不可变，注入后不能改
    // Spring 通过构造器把 budgetRuleMapper 传进来
}
```

**为什么推荐？**
- `final` 保证依赖不可变（线程安全）
- 不依赖 Spring 容器也能测试（可以直接 new 传 mock）
- 能在编译期发现缺失的依赖

> 💡 `@RequiredArgsConstructor` 是 Lombok 注解，它自动为所有 `final` 字段生成构造器。不用手写一长串构造器代码。Lumina 大部分 ServiceImpl 和 Controller 都用这个。

#### ⚠️ 方式二：字段注入（能工作但不推荐）

```java
// ❌ 反面写法（不推荐）
@Service
public class SomeServiceImpl implements SomeService {
    @Autowired                        // ← 字段注入
    private AgentExecutionEngine agentExecutionEngine;

    @Autowired
    private AgentMapper agentMapper;
    // ... 一堆 @Autowired 字段
}
```

字段注入的问题：
- 没有 `final`，可能被意外修改
- 无法脱离 Spring 容器做单元测试
- 循环依赖在启动时不会报错（可能隐藏设计问题）

> 💡 **真实案例**：Lumina 的 `AgentServiceImpl` 早期就用了十几个 `@Autowired` 字段注入。在本教程撰写期间已修正为构造器注入（`@RequiredArgsConstructor` + `final` 字段）。如果你看到本文档外的旧代码快照还有 `@Autowired`，那是修正前的版本。
>
> **例外**：该类的 3 个可选依赖（`@Autowired(required = false)` 标注的，如 `ContentModerationService`）保留了字段注入——因为构造器注入可选 Bean 需要额外包装（`ObjectProvider`），对这些"不一定存在"的边缘依赖，字段注入更清晰。

#### 方式三：Setter 注入（很少用，略过）

---

## 概念三：AOP（面向切面编程）

### 类比：地铁安检

你去坐地铁，不管在哪个站进站，都要过安检。安检不是每条线路各自实现的——它是**统一插入的通用逻辑**。

AOP 就是"安检"：**把通用逻辑（日志、审计、权限、事务）统一插入到多个方法上，不改业务代码。**

### Lumina 里的 AOP 实例

#### @Audit（审计日志）

你在 Controller 方法上加一个 `@Audit`，Spring 就会在方法执行前后自动记录"谁、在什么时候、做了什么操作"：

```java
// 文件：lumina-modules/lumina-business-agent/.../AgentController.java
@Audit(module = "agent", action = "CREATE", description = "创建Agent")  // ← 这一行
@PostMapping
public R<AgentVO> createAgent(@Valid @RequestBody CreateAgentDTO dto) {
    // 你的业务代码：创建 Agent
    // 你完全不用写日志代码——@Audit 帮你自动记录了
}
```

Spring 实际做了什么（你看不到，但确实发生了）：
```
1. 有人调用 createAgent()
2. @Audit 切面拦截到调用
3. 记录：开始时间、操作人（从上下文取）、操作模块
4. 执行你的 createAgent() 业务逻辑
5. 记录：结束时间、是否成功、耗时
6. 把审计日志存入数据库
```

**你只写了一行业务代码，但自动获得了完整的审计追踪。** 这就是 AOP 的威力。

#### @Transactional（事务）

另一个 AOP 应用的例子：

```java
@Service
public class AgentServiceImpl {
    @Transactional(rollbackFor = Exception.class)  // ← AOP 自动管理事务
    public Agent createAgent(Agent agent) {
        agentMapper.insert(agent);              // 写数据库
        knowledgeBaseService.mount(agent);       // 写关联表
        // 如果上面任何一步抛异常，Spring 自动回滚所有数据库操作！
    }
}
```

> 📖 AOP 底层原理（动态代理）详见[第三阶 03-AOP 代理](../stage-3-mastery/03-spring-aop-proxy.md)。

---

## 概念四：自动配置（Spring Boot 的魔法）

### 类比：智能家居

传统 Spring（没有 Boot）就像普通家居：你想用空调，得自己装空调、接电线、配遥控器。

Spring Boot 就像智能家居：**你只要在房子里放一台空调（加依赖），系统自动检测到并配好一切（自动配置）。**

### 在 Lumina 里怎么体现

Lumina 引入了 Redis 依赖：

```xml
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-boot-starter</artifactId>
</dependency>
```

加了这一行后，Spring Boot **自动**：
1. 检测到 classpath 有 Redisson
2. 读取 `application.yml` 里的 `spring.data.redis.*` 配置
3. 创建 `RedissonClient` Bean
4. 等着别人来注入使用

你**一行 Redis 配置代码都不用写**，直接注入用就行：

```java
@Service
public class SomeService {
    private final RedissonClient redissonClient;  // ← 自动就有，拿来就用
}
```

### 它怎么知道要自动配什么？

靠 **starter 依赖**。Spring Boot 生态有大量 `xxx-starter`，每个 starter 都带了"自动配置类"：
- `spring-boot-starter-web` → 自动配 Tomcat、Spring MVC
- `spring-boot-starter-data-redis` → 自动配 Redis 连接
- `mybatis-plus-boot-starter` → 自动配数据源、Mapper 扫描

> 📖 自动配置底层原理详见[第三阶 01-自动配置机制](../stage-3-mastery/01-spring-autoconfig-internals.md)。

---

## application.yml：Spring Boot 的"控制面板"

Spring Boot 几乎所有配置都在 `application.yml` 文件里。用 YAML 格式（缩进表示层级）：

```yaml
# 文件：lumina-standalone/src/main/resources/application.yml（简化版）
server:
  port: 8080                    # 服务器端口

spring:
  datasource:                   # 数据库配置
    url: jdbc:mysql://localhost:3306/lumina_dev
    username: root
    password: 123456
  data:
    redis:                      # Redis 配置
      host: localhost
      port: 6379
      password: 123456

lumina:                         # Lumina 自定义配置
  jwt:
    secret-key: your-secret-key
  agent:
    rate-limit:
      enabled: true
```

你在代码里用 `@Value` 读这些配置：

```java
@Value("${lumina.jwt.secret-key}")   // 读配置
private String jwtSecret;
```

> 📖 配置管理的完整用法详见[第二阶 12-配置管理](../stage-2-application/12-config-management.md)。

---

## 动手试试

1. **在 IDEA 里找到 `BudgetServiceImpl.java`**：
   `lumina-modules/lumina-business-agent/.../service/impl/BudgetServiceImpl.java`
2. **看它的类定义**：找到 `@Service` 和 `@RequiredArgsConstructor` 注解
3. **数数它有几个 `private final` 字段**：这些就是 Spring 自动注入的依赖
4. **打开 `AgentController.java`**：找到 `@Audit` 注解的方法，想想执行时 Spring 会帮你做什么

---

## 小结

| 概念 | 一句话记忆 | Lumina 示例 |
|------|-----------|-------------|
| **IoC 容器** | Spring 是大管家，帮你创建和管理对象 | `@Service` 告诉 Spring 管理这个类 |
| **依赖注入（DI）** | 声明你需要什么，Spring 自动送来 | `@RequiredArgsConstructor` + `final` 字段 |
| **AOP** | 统一插入通用逻辑，不改业务代码 | `@Audit` 自动记录审计、`@Transactional` 自动管理事务 |
| **自动配置** | 加了依赖就自动配好一切 | 加 Redisson starter → 自动创建 RedissonClient |
| **application.yml** | Spring Boot 的控制面板 | `server.port: 8080` |

---

## 下一步

下一节 [Spring Boot 在 Lumina 的实践](05-spring-boot-in-lumina.md)——讲 Lumina 怎么用 `@ConditionalOnXxx` 条件装配、Profile 切换、自动配置注册。

> 🚀 **现在继续**：[05 — Spring Boot 在 Lumina →](05-spring-boot-in-lumina.md)

---

## 自测题

1. **IoC 容器和 DI 是什么关系？**
   <details><summary>答案</summary>IoC 是思想（对象创建权交给容器），DI 是实现手段（容器把依赖注入到需要的地方）。IoC 容器是"大管家"，DI 是它"送外卖"的方式。</details>

2. **为什么推荐构造器注入而不是 @Autowired 字段注入？**
   <details><summary>答案</summary>① final 字段不可变更安全 ② 可以脱离容器做单元测试 ③ 编译期就能发现缺失依赖 ④ 避免隐藏循环依赖。</details>

3. **@Audit 注解是怎么做到"不改业务代码就能记录日志"的？**
   <details><summary>答案</summary>AOP 动态代理。Spring 给加了 @Audit 的类生成一个代理对象，在方法执行前后插入日志逻辑，你调用方法实际调用的是代理对象。</details>

4. **加了一个 `spring-boot-starter-web` 依赖，Spring Boot 自动做了什么？**
   <details><summary>答案</summary>自动检测到 starter，执行其自动配置类：内嵌 Tomcat、注册 Spring MVC、配置默认异常处理等。你不用写任何配置代码。</details>

---

📝 **本篇撰写期间修正的代码**：
- `lumina-modules/lumina-business-agent/.../AgentServiceImpl.java`：13 个必填依赖从 `@Autowired` 字段注入改为 `private final` + `@RequiredArgsConstructor` 构造器注入。3 个可选依赖（`@Autowired(required = false)`）保留字段注入（边缘场景的合理选择）。编译验证通过。
