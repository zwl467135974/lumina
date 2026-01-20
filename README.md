# Lumina Framework

<div align="center">

**Lumina AI Agent Platform Framework**

基于 AgentScope 和 Spring Cloud 的新一代 AI Agent 开发框架

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen)](https://spring.io/projects/spring-boot)
[![AgentScope](https://img.shields.io/badge/AgentScope-1.0.7-blue)](https://github.com/modelscope/agentscope-java)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

</div>

---

## 项目简介

Lumina 是一个企业级 AI Agent 开发框架，基于 [AgentScope Java](https://github.com/modelscope/agentscope-java) 和 [Spring Cloud](https://spring.io/projects/spring-cloud) 构建，提供开箱即用的 Agent 能力、微服务架构和企业级特性。

### 核心特性

- **AgentScope 集成** - 原生集成 AgentScope 框架，支持 ReAct、工具调用、多智能体协作
- **微服务架构** - 基于 Spring Cloud Alibaba，支持服务注册、配置管理、负载均衡
- **简化分层架构** - 清晰的 API、Service、Domain、Infrastructure 四层架构
- **企业级特性** - 支持多租户、权限管理、审计日志、分布式锁
- **响应式编程** - 基于 Project Reactor，支持高并发场景
- **多 LLM 支持** - 支持 DashScope、OpenAI、Claude、Gemini 等主流模型

---

## 项目结构

### 后端模块

```
lumina/
├── lumina-common/              # 公共模块（统一响应、异常体系、工具类）
├── lumina-framework/           # 框架模块（配置类、全局异常处理、Web 配置）
├── lumina-agent-core/          # Agent 核心模块（执行引擎、配置加载、工具管理）
├── lumina-gateway/             # API 网关模块（统一入口、路由、限流）
└── lumina-modules/             # 业务模块聚合器
    ├── lumina-business-base/   # 基础业务模块（用户、角色、权限、租户管理）
    ├── lumina-business-*/      # 传统业务模块
    └── lumina-agent-*/         # Agent 业务模块
```

### 前端项目

```
lumina-frontend/
├── src/
│   ├── api/                    # API 接口定义
│   ├── components/             # 公共组件
│   ├── composables/            # 组合式函数
│   ├── layouts/                # 布局组件
│   ├── router/                 # 路由配置
│   ├── stores/                 # 状态管理 (Pinia)
│   ├── types/                  # TypeScript 类型定义
│   ├── utils/                  # 工具函数
│   └── views/                  # 页面组件
└── package.json
```

### 模块说明

#### 后端模块

| 模块 | 说明 | 依赖 |
|------|------|------|
| **lumina-common** | 公共组件模块，提供统一响应、异常体系、工具类、常量 | 无 |
| **lumina-framework** | 框架基础设施模块，提供配置类、全局异常处理、Web 配置 | lumina-common |
| **lumina-agent-core** | Agent 执行引擎核心模块，封装 AgentScope 能力 | lumina-common |
| **lumina-gateway** | API 网关模块，作为统一入口 | lumina-common, lumina-framework |
| **lumina-business-base** | 基础业务模块，提供用户、角色、权限、租户管理 | lumina-common, lumina-framework |
| **lumina-modules** | 业务模块聚合器，按需添加业务模块 | 以上模块 |

#### 前端项目

| 项目 | 说明 | 技术栈 |
|------|------|--------|
| **lumina-frontend** | 前端项目，基于 Vue 3 + TypeScript + Element Plus | Vue 3, TypeScript, Element Plus, Pinia, Vite |

---

## 快速开始

### 环境要求

#### 后端环境

- **JDK 21+** - [下载](https://adoptium.net/)
- **Maven 3.9+** - [下载](https://maven.apache.org/download.cgi)
- **MySQL 8.0+** - [下载](https://dev.mysql.com/downloads/mysql/)
- **Redis 7.0+** - [下载](https://redis.io/download)
- **Nacos 3.1.1+** - [下载](https://nacos.io/zh-cn/docs/quick-start.html)

#### 前端环境

- **Node.js 18+** - [下载](https://nodejs.org/)
- **pnpm 8+** (推荐) 或 npm 9+ / yarn 1.22+ - [下载](https://pnpm.io/)

### 安装步骤

#### 1. 克隆项目

```bash
git clone https://github.com/your-username/lumina.git
cd lumina
```

#### 2. 启动基础设施

**启动 MySQL**

```bash
# 创建数据库
mysql -u root -p
CREATE DATABASE lumina_dev CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

**启动 Redis**

```bash
redis-server
```

**启动 Nacos**

```bash
# 下载 Nacos
wget https://github.com/alibaba/nacos/releases/download/3.1.1/nacos-server-3.1.1.zip

# 解压并启动
unzip nacos-server-3.1.1.zip
cd nacos/bin
./startup.sh -m standalone
```

访问 Nacos 控制台：http://localhost:8848/nacos（默认账号密码：nacos/nacos）

#### 3. 配置环境变量

```bash
# Linux/Mac
export DASHSCOPE_API_KEY=your_api_key_here

# Windows (PowerShell)
$env:DASHSCOPE_API_KEY="your_api_key_here"
```

#### 4. 初始化数据库

```bash
# 执行建表脚本
mysql -u root -p lumina_dev < sql/01_create_tables.sql

# 执行初始化数据脚本
mysql -u root -p lumina_dev < sql/02_init_data.sql
```

**默认管理员账号**：
- 用户名：`admin`
- 密码：`admin123`
- 租户：SYSTEM（系统租户，tenant_id=0）
- 角色：SUPER_ADMIN（超级管理员）

#### 5. 启动后端服务

**启动 Gateway**

```bash
cd lumina-gateway
mvn spring-boot:run
```

访问 Gateway：http://localhost:8080

**启动 Base 服务**（可选，用于用户管理）

```bash
cd lumina-modules/lumina-business-base
mvn spring-boot:run
```

Base 服务访问：http://localhost:8082

#### 6. 启动前端项目

```bash
# 进入前端目录
cd lumina-frontend

# 安装依赖
pnpm install

# 启动开发服务器
pnpm dev
```

访问前端：http://localhost:3000

**注意**: 前端开发服务器已配置代理，API 请求会自动转发到后端 Gateway (http://localhost:8080)

---

## 开发指南

### 多租户用户管理

Lumina 提供完整的多租户用户管理功能，基于 `lumina-business-base` 模块实现。

**核心特性**：
- **多租户隔离**：每个租户的用户数据严格隔离（ToB 场景）
- **RBAC 权限模型**：用户 → 角色 → 权限三级权限体系
- **角色管理**：角色作为权限集合，可配置给用户使用
- **分级管理**：
  - 超级管理员（SUPER_ADMIN）：可管理所有租户，拥有所有权限
  - 租户管理员（TENANT_ADMIN）：只能管理本租户用户和角色
  - 普通用户（TENANT_USER）：基本权限

**使用方式**：

1. **用户登录**：
```bash
curl -X POST http://localhost:8080/api/v1/base/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "admin123",
    "tenantId": 0
  }'
```

2. **Gateway 传递用户信息**：
Gateway 自动将用户信息通过 HTTP Header 传递给下游服务：
- `X-User-Id`: 用户 ID
- `X-Username`: 用户名
- `X-Tenant-Id`: 租户 ID
- `X-Roles`: 角色列表（逗号分隔）
- `X-Permissions`: 权限列表（逗号分隔）

3. **在业务代码中获取用户信息**：
```java
// 从 HttpServletRequest 中获取
String userId = request.getHeader("X-User-Id");
String tenantId = request.getHeader("X-Tenant-Id");
String[] roles = request.getHeader("X-Roles").split(",");
```

详细文档：[sql/README.md](sql/README.md)

### 创建业务模块

#### 1. 创建传统业务模块

```bash
# 在 lumina-modules 下创建模块
mkdir -p lumina-modules/lumina-business-order/src/main/java/io/lumina/order
```

创建 `pom.xml`：

```xml
<parent>
    <groupId>io.lumina</groupId>
    <artifactId>lumina</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <relativePath>../../pom.xml</relativePath>
</parent>

<artifactId>lumina-business-order</artifactId>

<dependencies>
    <dependency>
        <groupId>io.lumina</groupId>
        <artifactId>lumina-common</artifactId>
    </dependency>
    <dependency>
        <groupId>io.lumina</groupId>
        <artifactId>lumina-framework</artifactId>
    </dependency>
</dependencies>
```

#### 2. 创建 Agent 业务模块

```bash
# 在 lumina-modules 下创建模块
mkdir -p lumina-modules/lumina-agent-customer/src/main/java/io/lumina/agent/customer
```

创建 `pom.xml`：

```xml
<parent>
    <groupId>io.lumina</groupId>
    <artifactId>lumina</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <relativePath>../../pom.xml</relativePath>
</parent>

<artifactId>lumina-agent-customer</artifactId>

<dependencies>
    <dependency>
        <groupId>io.lumina</groupId>
        <artifactId>lumina-common</artifactId>
    </dependency>
    <dependency>
        <groupId>io.lumina</groupId>
        <artifactId>lumina-agent-core</artifactId>
    </dependency>
    <dependency>
        <groupId>io.lumina</groupId>
        <artifactId>lumina-framework</artifactId>
    </dependency>
</dependencies>
```

### 分层架构规范

Lumina 采用简化分层架构：

```
lumina-modules/lumina-{domain}/
└── src/main/java/io/lumina/{domain}/
    ├── api/                    # 接口层
    │   ├── controller/         # REST 控制器
    │   └── dto/                # 数据传输对象
    │
    ├── service/                # 业务服务层（核心）
    │   ├── {业务}Service.java
    │   └── impl/
    │
    ├── domain/                 # 领域模型层
    │   ├── model/              # 领域实体
    │   └── enums/              # 领域枚举
    │
    └── infrastructure/         # 基础设施层
        ├── mapper/             # MyBatis Mapper
        └── entity/             # 数据库实体 (DO)
```

详细规范参考：[Lumina开发规范与编码标准.md](docs/guides/Lumina开发规范与编码标准.md)

### 使用 Agent 执行引擎

```java
@Autowired
private AgentExecutionEngine agentExecutionEngine;

public String executeAgent(String task) {
    AgentConfig config = new AgentConfig();
    config.setAgentName("customer-service");
    config.setAgentType("ReAct");

    AgentConfig.LLMConfig llmConfig = new AgentConfig.LLMConfig();
    llmConfig.setModelType("dashscope");
    llmConfig.setModelName("qwen-max");
    config.setLlmConfig(llmConfig);

    ExecuteResult result = agentExecutionEngine.executeSync("customer-service", task, config);
    return result.getResult();
}
```

---

## 技术栈

### 后端技术栈

| 分类 | 技术 | 版本 | 说明 |
|------|------|------|------|
| **运行环境** | Java | 21 (LTS) | 最新 LTS 版本，支持虚拟线程 |
| **框架** | Spring Boot | 3.3.5 | 微服务基础框架 |
| | Spring Cloud | 2023.0.3 | 微服务组件 |
| | Spring Cloud Alibaba | 2023.0.1.2 | 阿里微服务组件 |
| **Agent 框架** | AgentScope Java | 1.0.7 | Agent 开发框架 |
| | Project Reactor | 2025.0.2 | 响应式编程 |
| **数据持久** | MyBatis | 3.0.3 | ORM 框架 |
| | MyBatis-Plus | 3.5.7 | MyBatis 增强工具 |
| **缓存** | Redisson | 3.24.3 | Redis 客户端 |
| **服务治理** | Nacos | 3.1.1+ | 服务注册/配置中心 |
| **文档** | SpringDoc | 2.6.0 | API 文档生成 |
| **JSON 处理** | Jackson | 2.20.1 | 统一 JSON 处理库 |

### 前端技术栈

| 分类 | 技术 | 版本 | 说明 |
|------|------|------|------|
| **框架** | Vue | 3.4+ | 渐进式 JavaScript 框架 |
| **语言** | TypeScript | 5.3+ | 类型安全的 JavaScript |
| **构建工具** | Vite | 5.0+ | 快速构建工具 |
| **UI 组件库** | Element Plus | 2.5+ | Vue 3 组件库 |
| **状态管理** | Pinia | 2.1+ | Vue 官方状态管理库 |
| **路由** | Vue Router | 4.2+ | Vue 官方路由库 |
| **HTTP 客户端** | Axios | 1.6+ | HTTP 请求库 |
| **工具库** | dayjs | 1.11+ | 日期处理库 |
| **样式** | SCSS | 1.69+ | CSS 预处理器 |

---

## 文档

### 快速开始

- [项目 README](README.md) - 项目介绍和快速开始
- [配置说明](docs/CONFIGURATION.md) - JWT、白名单、租户隔离等完整配置
- [测试指南](TESTING.md) - 测试验证步骤和场景
- [SQL 使用说明](sql/README.md) - 数据库脚本使用

### 开发指南

- [Lumina开发规范与编码标准](docs/guides/Lumina开发规范与编码标准.md) - 开发规范
- [业务模块开发指南](docs/guides/业务模块开发指南.md) - 业务模块开发
- [前端开发指南](docs/guides/前端开发指南.md) - 前端开发指南
- [工具开发指南](docs/guides/工具开发指南.md) - Agent 工具开发
- [配置管理规范](docs/guides/配置管理规范.md) - 配置管理规范
- [数据库配置指南](docs/guides/数据库配置指南.md) - 数据库配置

### 架构设计

- [Agent执行引擎设计](docs/architecture/Agent执行引擎设计.md) - Agent 核心设计
- [项目结构设计](docs/architecture/项目结构设计.md) - 项目结构说明
- [Lumina模块设计](docs/architecture/Lumina模块设计.md) - 模块设计文档
- [Lumina技术选型方案](docs/architecture/Lumina技术选型方案.md) - 技术选型说明
- [前端架构设计](docs/architecture/前端架构设计.md) - 前端架构设计
- [架构模式分析与建议](docs/architecture/架构模式分析与建议.md) - 架构模式分析

---

## 常见问题

### 1. Java 版本兼容性

AgentScope Java 使用 Java 17 编译，但 Lumina 使用 Java 21。由于 Java 21 向下兼容，可以直接使用。

### 2. Maven 依赖下载慢

在 `~/.m2/settings.xml` 中配置阿里云镜像：

```xml
<mirrors>
    <mirror>
        <id>aliyun-maven</id>
        <mirrorOf>*</mirrorOf>
        <name>Aliyun Maven</name>
        <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
</mirrors>
```

### 3. Nacos 连接失败

检查 Nacos 是否启动，访问 http://localhost:8848/nacos 确认。

### 4. 前端依赖安装失败

如果使用 pnpm 安装依赖失败，可以尝试：

```bash
# 清除缓存
pnpm store prune

# 重新安装
pnpm install
```

或者使用 npm：

```bash
npm install
```

### 5. 前端代理配置

前端开发服务器已配置代理，API 请求会自动转发到后端。如需修改代理地址，编辑 `lumina-frontend/vite.config.ts` 中的 `proxy` 配置。

### 6. 前后端跨域问题

开发环境下，前端已配置代理，不会出现跨域问题。生产环境需要在 Gateway 中配置 CORS。

---

## 贡献指南

欢迎贡献代码！请遵循以下步骤：

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 提交 Pull Request

---

## 许可证

本项目采用 Apache License 2.0 许可证。详见 [LICENSE](LICENSE) 文件。

---

## 联系方式

- 项目主页：[https://github.com/your-username/lumina](https://github.com/your-username/lumina)
- 问题反馈：[Issues](https://github.com/your-username/lumina/issues)

---

## 项目状态

### 已完成

- ✅ 项目基础架构搭建
- ✅ 核心模块开发（common、framework、agent-core、gateway）
- ✅ 统一响应和异常体系
- ✅ 框架配置（MyBatis-Plus、Redis、Jackson）
- ✅ 前端项目结构和核心功能
- ✅ 前端布局组件和页面
- ✅ Agent 执行引擎接口定义
- ✅ **lumina-business-base 基础业务模块（用户、角色、权限、租户管理）**
- ✅ **多租户 RBAC 权限体系实现**
- ✅ **JWT 认证和 Gateway 集成**
- ✅ **数据库脚本和初始化数据**

### 进行中

- 🔄 测试验证（启动服务、接口测试、租户隔离测试）
- 🔄 Agent 执行引擎具体实现
- 🔄 Gateway 路由配置完善

### 计划中

- 📋 用户管理、角色管理、权限管理、租户管理 API 完善
- 📋 BaseContext 工具类（ThreadLocal 存储用户上下文）
- 📋 权限注解（@RequirePermission）
- 📋 Redis 缓存用户权限信息
- 📋 单元测试和集成测试
- 📋 性能优化
- 📋 文档完善

---

**Lumina Framework** - 让 AI Agent 开发更简单 🚀
