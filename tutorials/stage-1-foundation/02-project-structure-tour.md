# 02 — 项目结构导览

> **前置要求**：已完成 [01-环境搭建](01-environment-setup.md)
> **预计阅读**：15 分钟
> **难度**：⭐☆☆☆☆

---

## 这节解决什么问题

你已经把环境搭好了，现在 clone 了 Lumina 代码，打开一看——**这么多文件夹，从哪看起？**

别慌。这节带你从高空俯瞰整个项目，搞清楚"哪个目录放什么"、"后端和前端怎么组织"、"我改一个功能要动哪些地方"。建立全局地图后，后面深入细节就不会迷路。

---

## 鸟瞰图：根目录长这样

```
lumina/
├── lumina-common/              ← 后端：公共工具（最底层）
├── lumina-framework/           ← 后端：框架配置（基础设施）
├── lumina-agent-core/          ← 后端：AI 引擎核心
├── lumina-gateway/             ← 后端：API 网关
├── lumina-standalone/          ← 后端：单体启动器
├── lumina-modules/             ← 后端：业务模块（3 个子模块）
│   ├── lumina-business-base/       用户/角色/权限/租户
│   ├── lumina-business-agent/      Agent/知识库/工作流
│   └── lumina-business-notification/ 通知中心
├── lumina-frontend/            ← 前端：Vue 3 项目
├── docs/                       ← 开发者文档（给懂的人看）
├── tutorials/                  ← 你正在看的教程（给学的人看）
├── examples/                   ← 示例（智能运维 demo）
├── nacos-config/               ← Nacos 配置文件（微服务模式用）
├── scripts/                    ← 运维脚本
├── deploy/                     ← 部署文件（Docker/K8s）
├── docker-compose-standalone.yml  ← 单体模式一键启动
├── pom.xml                     ← Maven 根配置（管所有后端模块）
└── README.md                   ← 项目说明
```

> 💡 **现阶段你只需要关注**：`lumina-modules/`（后端业务）、`lumina-frontend/`（前端）、`docker-compose-standalone.yml`（启动）。其他的慢慢认识。

---

## 后端模块：依赖关系是关键

后端代码分成 6 个模块，**不是随便分的**——它们有明确的"谁依赖谁"关系，从下往上逐层依赖：

```
┌──────────────────────────────────────────────────────────┐
│                    lumina-standalone                         │  ← 启动器（最顶层）
│              lumina-gateway（网关）                           │
└────────────────────────┬─────────────────────────────────────┘
                         │ 依赖
┌────────────────────────▼─────────────────────────────────────┐
│               lumina-modules（业务模块）                       │
│   ┌──────────────┬──────────────┬──────────────────────┐     │
│   │  base        │  agent       │  notification        │     │
│  用户/权限/租户   │ Agent/工作流  │  通知/Webhook        │     │
│   └──────────────┴──────────────┴──────────────────────┘     │
└────────────────────────┬─────────────────────────────────────┘
                         │ 依赖
┌────────────────────────▼─────────────────────────────────────┐
│               lumina-agent-core（AI 引擎核心）                 │
│         ReAct/Plan-Execute/RAG/工具/MCP/工作流引擎             │
└────────────────────────┬─────────────────────────────────────┘
                         │ 依赖
┌────────────────────────▼─────────────────────────────────────┐
│               lumina-framework（框架基础设施）                  │
│      Redis/审计/存储/异常处理/上下文传播/日志                    │
└────────────────────────┬─────────────────────────────────────┘
                         │ 依赖
┌────────────────────────▼─────────────────────────────────────┐
│               lumina-common（最底层公共模块）                   │
│        统一响应 R<T> / 异常体系 / 工具类 / 上下文                │
└──────────────────────────────────────────────────────────────┘
```

### 为什么要分层？

用生活类比：**就像盖楼**。

- **common** 是地基——所有楼都要用的砖头（统一返回格式、异常定义）
- **framework** 是基础设施——水电管线（Redis 连接、全局异常处理）
- **agent-core** 是 AI 引擎——核心设备（Agent 执行、RAG 检索）
- **modules** 是各个房间——实际业务功能（用户管理、Agent 管理）
- **gateway / standalone**是大门——访客进来要先过这里（认证、路由）

**核心原则**：上层可以依赖下层，**下层绝对不能依赖上层**。地基不可能知道楼上住了谁。

> 📖 为什么这么分层、违反了会怎样？详见[第二阶 02-分层架构](../stage-2-application/02-layered-architecture.md)。

---

## 后端单个模块内部结构

打开任意一个业务模块（比如 `lumina-business-agent`），你会看到统一的目录结构：

```
lumina-modules/lumina-business-agent/src/main/java/io/lumina/agent/
├── api/                        ← 接口层（接收 HTTP 请求）
│   ├── controller/             ←   Controller：定义 API 路径
│   ├── dto/                    ←   DTO：接收前端传来的数据
│   └── vo/                     ←   VO：返回给前端的数据
│
├── service/                    ← 服务层（业务逻辑）
│   ├── AgentService.java       ←   接口
│   └── impl/                   ←   实现类
│       └── AgentServiceImpl.java
│
├── domain/                     ← 领域层（业务模型）
│   ├── model/                  ←   Agent 领域实体（含业务方法）
│   └── enums/                  ←   枚举（AgentType 等）
│
└── infrastructure/             ← 基础设施层（数据库访问）
    ├── entity/                 ←   DO：和数据库表对应的实体
    │   └── AgentDO.java
    └── mapper/                 ←   Mapper：数据库操作接口
        └── AgentMapper.java
```

### 一个请求经过这些层的顺序

```
前端发请求 → Controller（api 层）
                ↓ 调用
            Service（service 层）—— 写业务逻辑的地方
                ↓ 调用
            Domain（domain 层）—— 业务规则（可选）
                ↓ 调用
            Mapper（infrastructure 层）—— 查数据库
                ↓
            MySQL 数据库
```

> 📖 这四层各自干嘛、为什么要分这么多？详见[第二阶 02-分层架构](../stage-2-application/02-layered-architecture.md)和[03-DTO/VO/Domain 模式](../stage-2-application/03-dto-vo-domain-pattern.md)。

---

## 数据库迁移：Flyway 目录

后端的表结构不是手动建的——是用 SQL 脚本"版本化管理"的，放在这里：

```
lumina-modules/lumina-business-base/src/main/resources/db/migration/
├── V1__init_schema.sql              ← 第 1 版：初始建表
├── V2__init_data.sql                ← 第 2 版：初始数据（admin 用户等）
├── ...
├── V4__add_audit_log_table.sql      ← 第 4 版：加审计日志表
├── ...
├── V44__add_glm_model_pricing.sql   ← 第 44 版：模型价格种子数据
└── （共 44 个迁移脚本）
```

**命名规则**：`V版本号__描述.sql`（注意是**两个下划线**）

启动项目时 Flyway 会自动按版本号顺序执行这些脚本，建表、加列、灌种子数据。

> 📖 Flyway 详细用法见[第一阶 10-Flyway 基础](10-flyway-basics.md)。

---

## 前端项目结构

```
lumina-frontend/
├── src/
│   ├── api/                    ← API 接口定义（调后端的）
│   │   ├── request.ts          ←   Axios 封装（所有请求的基础）
│   │   └── modules/            ←   按模块组织（agent.ts/user.ts...）
│   ├── assets/                 ← 静态资源（图片/样式）
│   │   └── styles/             ←   全局 SCSS 样式
│   ├── components/             ← 公共组件（可复用的）
│   │   ├── common/             ←   通用组件（LumTablePanel 等）
│   │   └── agent/              ←   Agent 相关组件
│   ├── composables/            ← 组合式函数（可复用的逻辑）
│   │   └── useTable.ts         ←   列表页通用逻辑（分页/搜索）
│   ├── directives/             ← 自定义指令
│   │   └── permission.ts       ←   v-permission 按钮权限指令
│   ├── layouts/                ← 布局组件（侧边栏/顶栏/内容区）
│   ├── locales/                ← 国际化（中文/英文）
│   ├── router/                 ← 路由配置（页面地址映射）
│   │   ├── index.ts            ←   路由实例
│   │   ├── modules/            ←   按模块组织路由
│   │   └── guards.ts           ←   路由守卫（登录拦截）
│   ├── stores/                 ← 状态管理（Pinia）
│   │   └── modules/            ←   user/app/permission...
│   ├── types/                  ← TypeScript 类型定义
│   │   └── api.ts              ←   和后端对齐的接口类型
│   ├── utils/                  ← 工具函数
│   ├── views/                  ← 页面组件（每个路由对应一个）
│   │   ├── login/              ←   登录页
│   │   ├── dashboard/          ←   首页
│   │   ├── agent/              ←   Agent 管理页
│   │   ├── system/             ←   系统管理（用户/角色/权限）
│   │   └── ...
│   ├── App.vue                 ← 根组件
│   └── main.ts                 ← 入口文件（挂载 Vue 应用）
├── vite.config.ts              ← Vite 构建配置（含代理）
├── package.json                ← 前端依赖
└── tsconfig.json               ← TypeScript 配置
```

### 前端"一个页面"的组成

以 Agent 管理页为例，涉及这些文件：

```
router/modules/index.ts    ← 注册路由：/agent → views/agent/index.vue
        ↓
views/agent/index.vue      ← 页面组件（模板 + 逻辑）
        ↓ 调用
api/modules/agent.ts       ← API 封装（listAgents/createAgent...）
        ↓ 基于
types/api.ts               ← 类型定义（AgentVO/CreateAgentDTO）
        ↓ 复用
composables/useTable.ts    ← 列表页通用逻辑（分页/搜索）
```

> 📖 前端各技术的详细用法见[第一阶 11-17 篇](README.md)。

---

## 配置文件在哪里

| 配置文件 | 作用 | 什么时候看 |
|----------|------|-----------|
| `pom.xml`（根） | Maven 全局配置、版本管理 | 改依赖版本时 |
| `lumina-standalone/.../application.yml` | standalone 模式的配置 | 改数据库/Redis/LLM 配置时 |
| `lumina-gateway/.../application.yml` | 网关配置 | 改路由/认证时 |
| `lumina-frontend/vite.config.ts` | 前端构建/代理配置 | 改 API 代理地址时 |
| `nacos-config/*.yaml` | 微服务模式的 Nacos 配置 | 用微服务模式时 |
| `.env.standalone.example` | 环境变量模板 | 配 LLM API Key 时 |

> 📖 配置管理详见[第二阶 12-配置管理](../stage-2-application/12-config-management.md)。

---

## 动手试试

1. **用 IDEA 打开后端项目**：File → Open → 选 `lumina/` 根目录的 `pom.xml`，选择"Open as Project"
2. **等 IDEA 加载完 Maven 依赖**（右下角进度条，第一次可能要几分钟）
3. **用 VS Code 打开 `lumina-frontend/`**
4. **在项目里找到这些文件**（熟悉路径）：
   - `lumina-modules/lumina-business-agent/.../AgentController.java` — Agent 的 API 入口
   - `lumina-modules/lumina-business-base/.../db/migration/V1__init_schema.sql` — 第一个建表脚本
   - `lumina-frontend/src/views/agent/index.vue` — Agent 列表页
   - `lumina-frontend/src/api/request.ts` — HTTP 请求封装

> 找到了？你对项目结构已经有基本认识了。

---

## 小结

| 你现在应该知道 | 一句话记忆 |
|---------------|-----------|
| 后端分几层模块 | common → framework → agent-core → modules → gateway/standalone |
| 单个业务模块内部结构 | api（Controller）→ service → domain → infrastructure（Mapper） |
| 数据库表怎么来的 | Flyway 按 V1-V44 脚本自动建 |
| 前端代码怎么组织 | views（页面）+ api（请求）+ stores（状态）+ router（路由） |

---

## 下一步

下一节讲 [Maven 多模块](03-maven-modules.md)——那个 `pom.xml` 到底怎么看、模块之间怎么管理依赖。

> 🚀 **现在继续**：[03 — Maven 多模块 →](03-maven-modules.md)

---

## 自测题

1. **`lumina-common` 能依赖 `lumina-modules` 吗？为什么？**
   <details><summary>答案</summary>不能。common 是最底层的公共模块，上层依赖下层，下层不能反向依赖上层，否则形成循环依赖。</details>

2. **如果你要加一个新的后端业务模块"订单管理"，应该在哪个目录下创建？**
   <details><summary>答案</summary>在 `lumina-modules/` 下创建 `lumina-business-order/`，和 base/agent/notification 平级。</details>

3. **Flyway 迁移脚本的命名规则是什么？为什么要版本号？**
   <details><summary>答案</summary>`V版本号__描述.sql`（双下划线）。版本号保证按顺序执行，避免乱序导致表结构不一致。</details>

4. **前端要加一个新页面"公告管理"，至少要改哪几个文件？**
   <details><summary>答案</summary>① `router/modules/` 加路由 ② `views/` 加页面组件 ③ `api/modules/` 加 API 封装 ④ `types/api.ts` 加类型定义</details>

---

📝 **本篇撰写期间修正的代码**：无。
