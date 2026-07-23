# 05 — Spring Boot 在 Lumina 的实践

> **前置要求**：已完成 [04-Spring Boot 基础](04-spring-boot-basics.md)
> **预计阅读**：25 分钟
> **难度**：⭐⭐☆☆☆

---

## 这节解决什么问题

上一节你学了 IoC、DI、AOP 的概念。这节看 Lumina 项目**怎么实际运用这些机制**解决真实问题：

- 怎么用 `@ConditionalOnProperty` 实现"配置驱动，换一个配置值就换一个实现"
- 怎么把自己的配置类注册成"自动配置"，让别的模块引入就能用
- 怎么用 Profile 在单体模式和微服务模式之间切换

学完这节，你能理解 Lumina 为什么能"同一套代码支持两种部署模式"。

---

## @ConditionalOnXxx：条件装配

### 类比：自动感应灯

走廊的灯装了人体感应器——**有人走过才亮，没人就灭**。不是所有灯都一直亮着。

`@ConditionalOnXxx` 就是 Spring 的"感应器"——**满足条件才创建 Bean，不满足就跳过**。

### 四个常用条件注解

| 注解 | 含义 | 类比 |
|------|------|------|
| `@ConditionalOnProperty` | 配置项满足条件才生效 | "设定了开灯模式才亮" |
| `@ConditionalOnClass` | classpath 有某个类才生效 | "走廊装了感应器才启用" |
| `@ConditionalOnBean` | 容器里有某个 Bean 才生效 | "电闸推上了灯才能亮" |
| `@ConditionalOnMissingBean` | 容器里**没有**某个 Bean 才生效（默认实现） | "没人手动开灯，才走自动模式" |

### Lumina 实例：5 个 OCR 引擎靠一个配置值切换

这是全项目最精彩的 `@ConditionalOnProperty` 教学案例。

Lumina 的 RAG 知识库需要识别图片中的文字（OCR），支持 5 种引擎：百度、腾讯、阿里、本地、不用。**怎么选？靠一个配置值**：

```yaml
# application.yml
lumina:
  rag:
    reader:
      ocr:
        provider: baidu    # 改成 tencent/alibaba/local/none 就换引擎
```

代码里 5 个实现类各自标注不同的条件：

```java
// 文件：lumina-agent-core/.../rag/NoopOcrProvider.java
@Component
@ConditionalOnProperty(prefix = "lumina.rag.reader.ocr", name = "provider",
        havingValue = "none", matchIfMissing = true)  // ← matchIfMissing=true 意味着不配就默认用它
public class NoopOcrProvider implements OcrProvider {
    @Override
    public String recognize(byte[] imageBytes, String language) {
        return "";  // 默认实现：什么也不做
    }
}

// 文件：lumina-agent-core/.../rag/BaiduOcrProvider.java
@Component
@ConditionalOnProperty(prefix = "lumina.rag.reader.ocr", name = "provider",
        havingValue = "baidu")    // ← 配 provider=baidu 时才创建
public class BaiduOcrProvider implements OcrProvider { ... }

// 腾讯/阿里/本地同理，各自标 havingValue = "tencent"/"alibaba"/"local"
```

**效果**：你改一个配置值，Spring 就自动创建对应的实现类，其他 4 个完全不创建。业务代码里注入 `OcrProvider` 接口，完全不用关心当前用的是哪个引擎。

> 💡 `matchIfMissing = true` 是关键——它表示"如果用户**根本没配**这个项，也用我（NoopOcrProvider）"。所以默认是安全的（不做 OCR），要用了才配成具体的引擎。

### 另一个实例：Flowable 工作流引擎

```java
// 文件：lumina-agent-core/.../flowable/FlowableWorkflowEngine.java
@Primary                              // 有多个候选时优先选我
@ConditionalOnBean(RepositoryService.class)  // ← 只有 Flowable 的 RepositoryService 存在时才创建
public class FlowableWorkflowEngine implements WorkflowEngine { ... }
```

含义：**项目引入了 Flowable 依赖（RepositoryService 才会存在），才用 Flowable 引擎；否则用自研的 DefaultWorkflowEngine。** 这样实现了"有 Flowable 用 Flowable，没有也能跑"的优雅降级。

---

## 自动配置：把自己的 Config 注册成"开箱即用"

### 为什么要注册自动配置？

上一节讲了，Spring Boot 的 starter 能自动配置一切。但 Lumina 自己写的配置类（如 Redis 封装、审计切面），怎么让**别的模块引入依赖就自动生效**，而不用每个模块手动 `@Import`？

答案：注册到 `AutoConfiguration.imports` 文件。

### 在 Lumina 里长啥样

```
# 文件：lumina-framework/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports

io.lumina.framework.config.JacksonConfig
io.lumina.framework.config.MyBatisPlusConfig
io.lumina.framework.config.RedisConfig
io.lumina.framework.config.WebMvcConfig
io.lumina.framework.config.SpringDocConfig
io.lumina.framework.cache.RedisCacheManager
io.lumina.framework.context.ContextPropagationAutoConfiguration
io.lumina.framework.web.LogWebMvcAutoConfiguration
```

**就这 8 行**。意思是：任何依赖 `lumina-framework` 的模块，启动时 Spring Boot 自动加载这 8 个配置类，不用手动声明。

> ⚠️ **Spring Boot 3.x 变化**：Spring Boot 3.0 之前用 `META-INF/spring.factories` 文件注册自动配置。**3.0 之后改用 `AutoConfiguration.imports`**。Lumina 用的是 Spring Boot 3.3.5，所以是新方式。你在网上搜的旧教程可能还在讲 `spring.factories`，那是过时的。

### 自动配置类长什么样

```java
// 文件：lumina-framework/.../config/SpringDocConfig.java（简化）
@AutoConfiguration                                    // ← 标记这是自动配置类
@ConditionalOnProperty(prefix = "springdoc.api-docs", // ← 可通过配置关闭
        name = "enabled", havingValue = "true", matchIfMissing = true)
public class SpringDocConfig {

    @Bean                                             // ← 这个方法返回的对象注册为 Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info().title("Lumina API").version("v1.0.0"));
    }
}
```

`@AutoConfiguration` = `@Configuration` + 自动被 `AutoConfiguration.imports` 发现。

---

## Profile：环境切换

### 类比：手机的"工作模式"和"生活模式"

手机有情景模式：工作模式下静音通知、生活模式下声音全开。**同一套硬件，模式不同行为不同**。

Spring Profile 就是"情景模式"——**同一套代码，不同 Profile 下加载不同配置**。

### Lumina 的两种 Profile

Lumina 有两种部署模式，靠 Profile 切换：

```yaml
# 文件：lumina-standalone/src/main/resources/application.yml
spring:
  profiles:
    active: standalone              # ← 激活 standalone Profile

  cloud:
    nacos:
      discovery:
        enabled: false              # standalone 模式不用 Nacos

  autoconfigure:
    exclude:                        # standalone 模式排除微服务组件
      - org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration
```

**对比**：如果切到微服务模式（`profiles.active: microservice`），Nacos discovery 开启、不排除那些自动配置——同一份代码，行为完全不同。

### 怎么切换 Profile

```bash
# 方式一：启动参数
java -jar lumina.jar --spring.profiles.active=standalone

# 方式二：环境变量
export SPRING_PROFILES_ACTIVE=standalone

# 方式三：application.yml 里写死（默认值）
spring.profiles.active: standalone
```

---

## @Bean：用代码创建对象

有时候你不能用 `@Component` 让 Spring 管理（比如第三方库的对象），这时用 `@Bean` 方法：

```java
// 文件：lumina-framework/.../config/RedisConfig.java（简化）
@Configuration
public class RedisConfig {

    @Bean(destroyMethod = "shutdown")              // ← 方法的返回值注册为 Bean
    @ConditionalOnMissingBean                      // ← 如果业务代码没自定义，才用这个
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
              .setAddress("redis://localhost:6379")
              .setPassword("123456");
        return Redisson.create(config);            // ← 手动创建对象，交给 Spring 管理
    }
}
```

**`@Component` vs `@Bean` 的区别**：
- `@Component`：加在你**自己写的类**上，Spring 自动 new
- `@Bean`：加在 `@Configuration` 类的**方法**上，你手动 new 再交给 Spring（用于第三方库的对象）

---

## 动手试试

1. **找到 OCR Provider 系列文件**：
   `lumina-agent-core/src/main/java/io/lumina/agent/rag/` 目录下找 `NoopOcrProvider`、`BaiduOcrProvider` 等
2. **对比它们的 `@ConditionalOnProperty`**：看看 `havingValue` 和 `matchIfMissing` 各不同
3. **打开 `AutoConfiguration.imports` 文件**：数数 lumina-framework 注册了几个自动配置类
4. **打开 `lumina-standalone/.../application.yml`**：找到 `profiles.active`，理解 standalone 模式关了什么

---

## 小结

| 机制 | 一句话记忆 | Lumina 实例 |
|------|-----------|-------------|
| `@ConditionalOnProperty` | 配置值满足才生效 | OCR 5 引擎靠一个配置值切换 |
| `@ConditionalOnBean` | 有某个 Bean 才生效 | Flowable 引擎有依赖才启用 |
| `AutoConfiguration.imports` | 注册自动配置，引入即生效 | framework 的 8 个配置类 |
| Profile | 情景模式，同一套代码不同行为 | standalone / microservice 切换 |
| `@Bean` | 手动创建对象交给 Spring | RedissonClient 配置 |

---

## 下一步

后端的 Spring Boot 基础到这里就讲完了。接下来进入数据访问层：[MyBatis-Plus 基础](06-mybatis-plus-basics.md)——怎么不用写 SQL 就能操作数据库。

> 🚀 **现在继续**：[06 — MyBatis-Plus 基础 →](06-mybatis-plus-basics.md)

---

## 自测题

1. **`@ConditionalOnProperty(matchIfMissing = true)` 的 `matchIfMissing` 是什么意思？**
   <details><summary>答案</summary>当用户完全没有配置这个属性时，也视为条件满足。相当于"默认实现"——没配就用我。</details>

2. **Spring Boot 3.x 用什么文件注册自动配置？和 2.x 有什么区别？**
   <details><summary>答案</summary>3.x 用 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`，2.x 用 `META-INF/spring.factories`。3.x 的方式更清晰，一行一个类名。</details>

3. **`@Component` 和 `@Bean` 什么时候各用哪个？**
   <details><summary>答案</summary>自己写的类用 @Component（加在类上）；第三方库的对象用 @Bean（加在 @Configuration 类的方法里，手动创建返回）。</details>

4. **Lumina 怎么做到"同一套代码支持 standalone 和微服务两种模式"？**
   <details><summary>答案</summary>用 Profile 切换 + @ConditionalOnXxx 条件装配。standalone 模式下通过 autoconfigure.exclude 排除微服务组件，关闭 Nacos；微服务模式下启用 Nacos 服务发现和配置中心。</details>

---

📝 **本篇撰写期间修正的代码**：无。
