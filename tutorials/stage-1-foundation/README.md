# 第一阶：技术栈基础 — "能上手"

> **目标**：学完这 17 篇，你能把项目完整跑起来、看懂大部分代码结构、能做简单的修改。
>
> **预计总时长**：8-12 小时（含动手练习）

---

## 这阶讲什么

假设你会 Java / JS 的语法，但不知道 Spring、Vue、Redis 这些东西是干嘛的。这阶就是把这些"拦路虎"一个个讲清楚——**每个技术栈先用生活类比让你理解"它解决什么问题"，再用最小示例让你看懂"基本怎么用"，最后带你看 Lumina 项目里真实怎么写的**。

---

## 学习路线

### 🔧 后端基础（01-10）

| # | 标题 | 你将学会 | 难度 |
|---|------|----------|------|
| 01 | [环境搭建](01-environment-setup.md) | 装 JDK 21 / Maven / Node / Docker / IDEA，配好 IDE | ⭐ |
| 02 | [项目结构导览](02-project-structure-tour.md) | Lumina 有哪些目录、各放什么、后端+前端怎么组织 | ⭐ |
| 03 | [Maven 多模块](03-maven-modules.md) | pom.xml 怎么读、多模块依赖关系、怎么编译打包 | ⭐⭐ |
| 04 | [Spring Boot 基础](04-spring-boot-basics.md) | IoC 容器、依赖注入、AOP、自动配置——用"公司配工位"类比 | ⭐⭐ |
| 05 | [Spring Boot 在 Lumina](05-spring-boot-in-lumina.md) | @ConditionalOnXxx 条件装配、Profile 切换、AutoConfiguration | ⭐⭐ |
| 06 | [MyBatis-Plus 基础](06-mybatis-plus-basics.md) | 告别手写 SQL：BaseMapper、LambdaQueryWrapper、分页 | ⭐⭐ |
| 07 | [MyBatis-Plus 在 Lumina](07-mybatis-plus-in-lumina.md) | 实体注解、逻辑删除、自动填充、多租户拦截器初识 | ⭐⭐ |
| 08 | [Redis 基础](08-redis-basics.md) | Redis 是什么、Redisson 客户端、缓存/锁/计数器 | ⭐⭐ |
| 09 | [Redis 在 Lumina](09-redis-in-lumina.md) | RedisCacheManager 封装、分布式锁、限流实战 | ⭐⭐⭐ |
| 10 | [Flyway 迁移](10-flyway-basics.md) | 数据库版本管理、V1-V44 命名约定、种子数据 | ⭐⭐ |

### 🎨 前端基础（11-17）

| # | 标题 | 你将学会 | 难度 |
|---|------|----------|------|
| 11 | [Vue 3 基础](11-vue3-basics.md) | Composition API、ref/reactive/computed、组件通信 | ⭐⭐ |
| 12 | [Vue 3 在 Lumina](12-vue3-in-lumina.md) | Lumina 组件怎么组织、LumStatCard/LumTablePanel 封装 | ⭐⭐ |
| 13 | [Element Plus](13-element-plus-basics.md) | el-table/el-form/el-dialog 常用组件速成 | ⭐⭐ |
| 14 | [Pinia + Router](14-pinia-router-basics.md) | 状态管理（store）、路由配置、路由守卫（鉴权） | ⭐⭐ |
| 15 | [TypeScript + Vite](15-typescript-vite-basics.md) | 类型定义（interface/泛型）、Vite 构建与代理配置 | ⭐⭐ |
| 16 | [Axios + SSE](16-axios-sse-basics.md) | HTTP 请求封装、拦截器、SSE 流式通信（AI 打字机） | ⭐⭐⭐ |
| 17 | [跑起项目](17-run-the-project.md) | standalone 模式一条命令启动、登录验证、前后端联调 | ⭐ |

---

## 核心概念速览（这阶最重要的 5 句话）

### 1. Spring Boot = "自动装配的乐高底座"
你不用手动 new 对象、不用手动配数据库连接——Spring Boot 帮你"约定俗成"地搞定，你只要写业务逻辑。
> 详见 [04-spring-boot-basics](04-spring-boot-basics.md)

### 2. MyBatis-Plus = "不用写 SQL 的数据库操作"
继承一个 `BaseMapper<T>` 接口，增删改查全免费，复杂查询用 `LambdaQueryWrapper` 链式拼条件。
> 详见 [06-mybatis-plus-basics](06-mybatis-plus-basics.md)

### 3. Redis = "超快的内存数据库"
不只是缓存——Redis 还能做分布式锁（防多实例重复执行）、限流（防刷接口）、消息广播。
> 详见 [08-redis-basics](08-redis-basics.md)

### 4. Vue 3 = "数据驱动界面的前端框架"
你不用手动操作 DOM——改数据，界面自动更新。Composition API 把逻辑组织成可复用的函数。
> 详见 [11-vue3-basics](11-vue3-basics.md)

### 5. SSE = "服务器主动推送的 HTTP 长连接"
AI 打字机效果的秘密：服务器不一次性返回，而是一个字一个字"推"给浏览器。
> 详见 [16-axios-sse-basics](16-axios-sse-basics.md)

---

## 自测：学完这阶你应该能做到

- [ ] 能说出 Lumina 后端有哪些模块、各模块职责
- [ ] 能读懂一个 Controller → Service → Mapper 的完整链路
- [ ] 能看懂一段 Vue 组件的 `<script setup>` 逻辑
- [ ] 能用 standalone 模式把项目跑起来并登录
- [ ] 能看懂 Flyway 迁移脚本在做什么
- [ ] 能理解 Redis 在项目里至少 3 种用途

> 如果有 2 项以上做不到，回去重读对应章节。能做到全部？恭喜，进入[第二阶](../stage-2-application/README.md)。

---

## ⚠️ 重要提示

这阶**不要求你记住所有 API**。目标是"建立心智模型"——知道每个技术是干嘛的、在项目哪里能看到。具体 API 用法随时回来查。

> 🚀 **现在开始**：打开 [01-environment-setup.md](01-environment-setup.md)，先把开发环境搭好。
