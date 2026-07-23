# 第二阶：项目实战应用 — "能开发"

> **目标**：学完这 15 篇，你理解 Lumina 的设计理念，能独立开发新功能，达到中级开发水平。
>
> **前置要求**：已完成[第一阶](../stage-1-foundation/README.md)，或已掌握 Spring Boot + Vue 基础。
>
> **预计总时长**：10-15 小时（含实战练习）

---

## 这阶讲什么

第一阶你学会了"每个技术是什么"。这阶回答更深的问题：**"为什么 Lumina 要这么设计？"**

- 为什么要有 Controller/Service/Domain/Infrastructure 四层？
- 为什么要分 DTO/VO/Domain 这么多对象？
- 为什么异常要用 ErrorCode 枚举而不是直接 throw？
- 为什么权限要做成 RBAC 五表？
- 为什么多租户要自动改写 SQL？

每篇都用 Lumina 真实代码回答"为什么"，看完你就能自己做技术决策。

---

## 学习路线

### 🔗 核心理念（01-08）

| # | 标题 | 回答的"为什么" | 难度 |
|---|------|----------------|------|
| 01 | [一个请求的旅程](01-request-lifecycle.md) | 从前端发起到 DB 返回，全链路串联第一阶所有技术 | ⭐⭐⭐ |
| 02 | [分层架构](02-layered-architecture.md) | 为什么分四层？不是 MVC 三层够吗？ | ⭐⭐ |
| 03 | [DTO/VO/Domain 模式](03-dto-vo-domain-pattern.md) | 为什么要这么多对象类型？一个 Map 不行吗？ | ⭐⭐ |
| 04 | [异常与错误码](04-exception-error-code.md) | 为什么不用 RuntimeException？统一异常怎么工作？ | ⭐⭐ |
| 05 | [校验与审计](05-validation-and-audit.md) | @Valid 校验 + @Audit AOP 自动记录操作日志 | ⭐⭐⭐ |
| 06 | [权限 RBAC](06-permission-rbac.md) | @RequirePermission 怎么拦截？RBAC 五表怎么设计？ | ⭐⭐⭐ |
| 07 | [多租户隔离](07-multi-tenancy.md) | SQL 自动加 tenant_id 是怎么做到的？ | ⭐⭐⭐ |
| 08 | [JWT 认证](08-jwt-auth.md) | 网关→服务→上下文，Token 怎么传递和校验？ | ⭐⭐⭐ |

### 🛠️ 实战开发（09-10）— 本阶最有价值

| # | 标题 | 你将做到 | 难度 |
|---|------|----------|------|
| 09 | [实战：后端模块开发](09-build-a-feature-backend.md) | 从零实现"公告管理"：Flyway→DO→Mapper→Service→Controller→权限→审计→测试 | ⭐⭐⭐⭐ |
| 10 | [实战：前端页面开发](10-build-a-feature-frontend.md) | 配套前端：类型→API→路由→页面→composable→权限指令 | ⭐⭐⭐⭐ |

### 🏗️ 工程化（11-15）

| # | 标题 | 你将学会 | 难度 |
|---|------|----------|------|
| 11 | [Nacos + Gateway](11-nacos-gateway.md) | 微服务架构、服务发现、网关路由 | ⭐⭐⭐ |
| 12 | [配置管理](12-config-management.md) | yml/Profile/Nacos/环境变量，配置优先级 | ⭐⭐ |
| 13 | [测试实践](13-testing-practice.md) | 单元测试 + 集成测试 + JaCoCo 覆盖率门控 | ⭐⭐⭐ |
| 14 | [Git 提交规范](14-git-commit-convention.md) | Conventional Commits、分支策略、PR 流程 | ⭐ |
| 15 | [技术选型决策](15-tech-selection-why.md) | 为什么选这些技术？每个选型的取舍分析 | ⭐⭐⭐ |

---

## 核心理念速览（这阶最重要的 5 个"为什么"）

### 1. 为什么分层？→ 关注点分离
```
Controller（接请求）→ Service（业务逻辑）→ Domain（领域模型）→ Infrastructure（DB）
```
每层只管自己的事。改 DB 不会影响接口格式，改接口格式不会碰业务逻辑。
> 详见 [02-layered-architecture](02-layered-architecture.md)

### 2. 为什么分 DTO/VO？→ 安全 + 解耦
- **DTO**（Data Transfer Object）：前端传进来的，只含允许修改的字段
- **VO**（View Object）：返回给前端的，可以脱敏（如 apiKey 不回显）
- 一个 `Agent` 实体同时对接收和返回？那是**安全漏洞**
> 详见 [03-dto-vo-domain-pattern](03-dto-vo-domain-pattern.md)

### 3. 为什么统一异常？→ 前端不用猜
所有异常统一变成 `{"code": 20001, "message": "Agent 不存在"}` 格式。前端只看 code 决定怎么提示，不用 try-catch 各种异常。
> 详见 [04-exception-error-code](04-exception-error-code.md)

### 4. 为什么多租户？→ 一套系统服务多家客户
A 公司的数据 B 公司绝对看不到。靠 MyBatis 拦截器自动给每条 SQL 加 `WHERE tenant_id = 当前租户`，业务代码完全无感。
> 详见 [07-multi-tenancy](07-multi-tenancy.md)

### 5. 为什么 JWT？→ 无状态 + 可分布
Token 里编码了用户信息，任何服务器都能验签，不需要查 Session。天然支持多实例部署。
> 详见 [08-jwt-auth](08-jwt-auth.md)

---

## 实战篇说明（09-10）

这两篇是**整个教学体系最有价值的实战**。我们会从零开始，在 Lumina 项目里实现一个完整的"公告管理"功能：

**后端（09）**：
1. Flyway 建表（V45__add_announcement.sql）
2. 实体类 AnnouncementDO + Mapper
3. Service 接口 + ServiceImpl（含 @Transactional）
4. Controller（含 @RequirePermission + @Audit + @Valid）
5. DTO/VO + 权限种子数据
6. 单元测试

**前端（10）**：
1. types/api.ts 定义类型
2. api/modules/announcement.ts 封装 API
3. router 注册路由
4. views/announcement/index.vue 列表页（用 useTable composable）
5. v-permission 控制按钮权限

每一步都对照 Lumina 现有模块（以 Agent 模块为模板），学完你能复制这个流程做任何新功能。

---

## 自测：学完这阶你应该能做到

- [ ] 能画出"一个 HTTP 请求从前端到 DB 再回来"的完整流程图
- [ ] 能解释为什么要分 Controller/Service/Domain/Infrastructure
- [ ] 能独立创建一个后端业务模块（从建表到接口）
- [ ] 能独立创建一个前端页面（从类型到路由）
- [ ] 能解释多租户隔离的工作原理
- [ ] 能写出一个符合项目规范的 Controller（权限+审计+校验+异常）

> 全部做到？你已经是中级开发了。想成为"八股文大师"？进入[第三阶](../stage-3-mastery/README.md)。
> 想深入 AI 部分？进入[AI 专项](../stage-4-ai-agent/README.md)。

> 🚀 **现在开始**：打开 [01-request-lifecycle.md](01-request-lifecycle.md)，先看一个请求的完整旅程。
