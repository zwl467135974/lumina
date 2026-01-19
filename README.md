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

## 模块结构

```
lumina/
├── lumina-common/              # 公共模块（统一响应、异常体系、工具类）
├── lumina-framework/           # 框架模块（配置类、全局异常处理、Web 配置）
├── lumina-agent-core/          # Agent 核心模块（执行引擎、配置加载、工具管理）
├── lumina-gateway/             # API 网关模块（统一入口、路由、限流）
└── lumina-modules/             # 业务模块聚合器
    ├── lumina-business-*/      # 传统业务模块
    └── lumina-agent-*/         # Agent 业务模块
```

### 模块说明

| 模块 | 说明 | 依赖 |
|------|------|------|
| **lumina-common** | 公共组件模块，提供统一响应、异常体系、工具类、常量 | 无 |
| **lumina-framework** | 框架基础设施模块，提供配置类、全局异常处理、Web 配置 | lumina-common |
| **lumina-agent-core** | Agent 执行引擎核心模块，封装 AgentScope 能力 | lumina-common |
| **lumina-gateway** | API 网关模块，作为统一入口 | lumina-common, lumina-framework |
| **lumina-modules** | 业务模块聚合器，按需添加业务模块 | 以上模块 |

---

## 快速开始

### 环境要求

- **JDK 21+** - [下载](https://adoptium.net/)
- **Maven 3.9+** - [下载](https://maven.apache.org/download.cgi)
- **MySQL 8.0+** - [下载](https://dev.mysql.com/downloads/mysql/)
- **Redis 7.0+** - [下载](https://redis.io/download)
- **Nacos 3.1.1+** - [下载](https://nacos.io/zh-cn/docs/quick-start.html)

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

#### 4. 编译项目

```bash
mvn clean compile
```

#### 5. 启动 Gateway

```bash
cd lumina-gateway
mvn spring-boot:run
```

访问 Gateway：http://localhost:8080

---

## 开发指南

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

---

## 文档

- [项目搭建执行计划](docs/guides/项目搭建执行计划.md) - 项目搭建指南
- [Lumina开发规范与编码标准](docs/guides/Lumina开发规范与编码标准.md) - 开发规范
- [业务模块开发指南](docs/guides/业务模块开发指南.md) - 业务模块开发
- [工具开发指南](docs/guides/工具开发指南.md) - Agent 工具开发
- [Agent执行引擎设计](docs/architecture/Agent执行引擎设计.md) - Agent 核心设计
- [项目结构设计](docs/architecture/项目结构设计.md) - 项目结构说明
- [Lumina模块设计](docs/architecture/Lumina模块设计.md) - 模块设计文档
- [Lumina技术选型方案](docs/architecture/Lumina技术选型方案.md) - 技术选型说明

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

**Lumina Framework** - 让 AI Agent 开发更简单 🚀
