# Lumina 框架技术选型方案

## 一、选型原则

1. **站在巨人肩膀上** - 优先选择成熟稳定的开源组件
2. **技术先进性** - 选择主流且持续维护的版本
3. **兼容性优先** - 确保各组件版本兼容，避免冲突
4. **企业级特性** - 支持多租户、权限、审计等企业需求
5. **Agent 领域特化** - 集成 Agent 开发所需的核心能力

---

## 二、核心技术栈选型

### 2.1 基础运行环境

| 技术 | 版本 | 选型理由 | 参考来源 |
|------|------|---------|---------|
| **Java** | **21 (LTS)** | 最新 LTS 版本，性能提升显著，支持虚拟线程 | biwinai-bak 使用 |
| **Maven** | 3.9+ | Java 项目标准构建工具 | 通用选择 |

**说明**: 
- ✅ 选择 Java 21 而非 Java 17，因为 Agent 平台需要处理大量并发，Java 21 的虚拟线程特性非常适合
- ⚠️ AgentScope 使用 Java 17，但我们可以通过依赖隔离解决兼容性问题

---

### 2.2 Spring 生态

| 技术 | 版本 | 选型理由 | 参考来源 |
|------|------|---------|---------|
| **Spring Boot** | **3.3.5** | 稳定版本，与 Java 21 完美兼容 | biwinai-bak |
| **Spring Cloud** | **2023.0.3** | 与 Spring Boot 3.3.5 匹配 | biwinai-bak |
| **Spring Cloud Alibaba** | **2023.0.1.2** | 提供 Nacos、Sentinel 等微服务组件 | biwinai-bak |

**说明**:
- ✅ 采用微服务架构，支持分布式部署
- ✅ Spring Cloud Alibaba 提供完整的微服务解决方案

---

### 2.3 数据持久层

| 技术 | 版本 | 选型理由 | 参考来源 |
|------|------|---------|---------|
| **MyBatis** | **3.0.3** | 成熟稳定，灵活度高，适合复杂查询 | biwinai-bak |
| **MyBatis-Plus** | **3.5.7+** | 提供 CRUD 增强，减少样板代码 | 建议新增 |
| **PageHelper** | **2.1.0** | 分页插件，简单易用 | biwinai-bak |
| **Druid** | **1.2.23** | 阿里数据库连接池，监控功能强大 | biwinai-bak |
| **Dynamic DataSource** | **4.3.1** | 多数据源支持，适合多租户场景 | biwinai-bak |

**数据库支持**:
- MySQL 8.0+ (主数据库)
- PostgreSQL (可选)
- Redis (缓存/会话存储)

**说明**:
- ✅ MyBatis 灵活性高，适合 Agent 平台的复杂业务场景
- ⚠️ 考虑引入 MyBatis-Plus 简化基础 CRUD 操作
- ✅ 多数据源支持多租户架构

---

### 2.4 缓存与消息队列

| 技术 | 版本 | 选型理由 | 参考来源 |
|------|------|---------|---------|
| **Redis** | **7.0+** | 高性能缓存和会话存储 | 通用选择 |
| **Redisson** | **3.24.3** | Redis Java 客户端，提供分布式锁等高级特性 | biwinai-bak |
| **Kafka** | **3.6+** | 高吞吐量消息队列，适合事件驱动架构 | biwinai-bak |

**说明**:
- ✅ Redis 用于缓存、会话管理、分布式锁
- ✅ Kafka 用于 Agent 任务事件流、异步消息处理

---

### 2.5 Agent 核心框架

| 技术 | 版本 | 选型理由 | 参考来源 |
|------|------|---------|---------|
| **AgentScope Java** | **1.0.7** | 专业的 Agent 开发框架，支持 ReAct、工具调用、多智能体协作 | agentscope-java |
| **Project Reactor** | **2025.0.2** | 响应式编程，AgentScope 核心依赖 | agentscope-java |
| **Spring AI** | **1.0.3** | Spring 官方 AI 框架，可选择性集成 | biwinai-bak |

**AgentScope 核心能力**:
- ✅ ReAct Agent (推理-行动范式)
- ✅ 工具调用 (Tool Calling)
- ✅ 记忆管理 (Memory Management)
- ✅ 多智能体协作 (Multi-Agent)
- ✅ MCP 协议支持
- ✅ A2A 协议支持 (分布式 Agent)
- ✅ RAG 支持
- ✅ PlanNotebook (任务规划)

**说明**:
- ✅ AgentScope 是 Agent 领域的专业框架，直接集成
- ⚠️ 需要解决 Java 版本兼容问题 (AgentScope 使用 Java 17，我们使用 Java 21)
- ✅ Spring AI 可作为补充，但 AgentScope 是主要框架

---

### 2.6 LLM 模型支持

| 技术 | 版本 | 选型理由 | 参考来源 |
|------|------|---------|---------|
| **DashScope SDK** | **2.22.5** | 阿里云通义千问 | agentscope-java |
| **OpenAI Java SDK** | **4.15.0** | OpenAI GPT 系列 | agentscope-java |
| **Anthropic SDK** | **2.11.1** | Claude 系列 | agentscope-java |
| **Google GenAI** | **1.34.0** | Gemini 系列 | agentscope-java |
| **Ollama** | 最新 | 本地模型支持 | agentscope-java |

**说明**:
- ✅ AgentScope 已集成主流 LLM SDK，直接使用
- ✅ 支持多模型切换，便于成本优化

---

### 2.7 向量数据库 (RAG)

| 技术 | 版本 | 选型理由 | 参考来源 |
|------|------|---------|---------|
| **Qdrant** | **1.16.2** | 高性能向量数据库 | agentscope-java |
| **Milvus** | **2.6.12** | 企业级向量数据库 | agentscope-java |
| **阿里云百炼** | **2.6.2** | 托管 RAG 服务 | agentscope-java |

**说明**:
- ✅ 支持自建向量数据库和托管服务
- ✅ 根据业务规模选择

---

### 2.8 服务注册与配置中心

| 技术 | 版本 | 选型理由 | 参考来源 |
|------|------|---------|---------|
| **Nacos** | **3.1.1+** | 服务注册、配置管理、服务发现 | agentscope-java / biwinai-bak |

**说明**:
- ✅ Nacos 是 Spring Cloud Alibaba 的标准组件
- ✅ AgentScope 的 A2A 协议支持 Nacos 服务发现

---

### 2.9 API 文档与接口

| 技术 | 版本 | 选型理由 | 参考来源 |
|------|------|---------|---------|
| **SpringDoc (Swagger)** | **2.6.0** | OpenAPI 3.0 文档生成 | biwinai-bak |

**说明**:
- ✅ 自动生成 API 文档，提升开发效率

---

### 2.10 工具库

| 技术 | 版本 | 选型理由 | 参考来源 |
|------|------|---------|---------|
| **Jackson** | **2.20.1** | JSON 处理 (AgentScope 使用) | agentscope-java |
| **Fastjson2** | **2.0.53** | 高性能 JSON 处理 (可选) | biwinai-bak |
| **Apache Commons Lang3** | **3.20.0** | 工具类库 | agentscope-java |
| **Guava** | **33.5.0** | Google 工具库 | agentscope-java |

**说明**:
- ✅ Jackson 是 AgentScope 的标准，必须使用
- ⚠️ Fastjson2 可选，但建议统一使用 Jackson 避免冲突

---

### 2.11 安全认证

| 技术 | 版本 | 选型理由 | 参考来源 |
|------|------|---------|---------|
| **JWT (jjwt)** | **0.12.5** | JWT Token 认证 | 建议升级 |
| **Spring Security** | **6.3+** | 安全框架 | Spring Boot 内置 |

**说明**:
- ⚠️ biwinai-bak 使用 jjwt 0.9.1 (较旧)，建议升级到 0.12.5
- ✅ Spring Security 提供完整的认证授权能力

---

### 2.12 可观测性

| 技术 | 版本 | 选型理由 | 参考来源 |
|------|------|---------|---------|
| **OpenTelemetry** | **1.58.0** | 分布式追踪、指标收集 | agentscope-java |
| **Logback + Logstash** | 最新 | 日志收集 | biwinai-bak |

**说明**:
- ✅ OpenTelemetry 是云原生可观测性标准
- ✅ AgentScope 原生支持 OpenTelemetry

---

### 2.13 文件处理

| 技术 | 版本 | 选型理由 | 参考来源 |
|------|------|---------|---------|
| **Apache POI** | **5.5.1** | Office 文档处理 | agentscope-java |
| **Apache PDFBox** | **3.0.6** | PDF 处理 | agentscope-java |
| **Apache Tika** | **3.2.3** | 文档内容提取 | agentscope-java |
| **MinIO** | **8.2.2+** | 对象存储 (可选) | biwinai-bak |

**说明**:
- ✅ 支持多种文档格式，适合 RAG 场景

---

### 2.14 前端技术栈 (参考)

| 技术 | 版本 | 选型理由 | 参考来源 |
|------|------|---------|---------|
| **Vue 3** | 3.x | 现代化前端框架 | biwinai-bak (Vue 2) |
| **Element Plus** | 最新 | UI 组件库 | 建议升级 |
| **TypeScript** | 5.x | 类型安全 | 建议新增 |

**说明**:
- ⚠️ biwinai-bak 使用 Vue 2，建议升级到 Vue 3
- ✅ 前后端分离架构

---

## 三、技术选型对比分析

### 3.1 与 biwinai-bak 的差异

| 项目 | biwinai-bak | Lumina 建议 | 原因 |
|------|------------|------------|------|
| Java 版本 | 21 | **21** ✅ | 保持一致 |
| Spring Boot | 3.3.5 | **3.3.5** ✅ | 稳定版本 |
| Agent 框架 | Spring AI | **AgentScope** ✅ | 更专业的 Agent 框架 |
| JWT | 0.9.1 | **0.12.5** ⬆️ | 安全更新 |
| Fastjson2 | 使用 | **Jackson** ✅ | 统一使用，避免冲突 |
| Vue | 2.x | **Vue 3** ⬆️ | 现代化升级 |

### 3.2 与 agentscope-java 的差异

| 项目 | agentscope-java | Lumina 建议 | 原因 |
|------|----------------|------------|------|
| Java 版本 | 17 | **21** ⬆️ | 使用最新 LTS |
| Spring Boot | 4.0.1 (BOM) | **3.3.5** ✅ | 实际使用版本 |
| 微服务 | 无 | **Spring Cloud** ✅ | 企业级需求 |
| 数据持久 | 无 | **MyBatis** ✅ | 业务数据持久化 |
| 权限系统 | 无 | **Spring Security** ✅ | 企业级权限 |

---

## 四、版本兼容性分析

### 4.1 潜在冲突点

1. **Java 版本兼容**
   - AgentScope 使用 Java 17，Lumina 使用 Java 21
   - ✅ **解决方案**: Java 21 向后兼容 Java 17，无需修改

2. **JSON 处理库**
   - AgentScope 使用 Jackson，biwinai-bak 使用 Fastjson2
   - ✅ **解决方案**: 统一使用 Jackson，移除 Fastjson2

3. **响应式编程**
   - AgentScope 基于 Project Reactor
   - ✅ **解决方案**: Spring WebFlux 也基于 Reactor，天然兼容

### 4.2 依赖管理策略

- 使用 Maven BOM (Bill of Materials) 统一管理版本
- 创建 `lumina-dependencies-bom` 模块
- 参考 AgentScope 的依赖管理方式

---

## 五、技术选型总结

### 5.1 核心选型清单

```
✅ Java 21 (LTS)
✅ Spring Boot 3.3.5
✅ Spring Cloud 2023.0.3
✅ Spring Cloud Alibaba 2023.0.1.2
✅ AgentScope Java 1.0.7 (Agent 核心)
✅ MyBatis 3.0.3 + MyBatis-Plus 3.5.7
✅ Redis + Redisson 3.24.3
✅ Kafka 3.6+
✅ Nacos 3.1.1+
✅ OpenTelemetry 1.58.0
✅ Jackson 2.20.1 (统一 JSON 处理)
✅ Spring Security 6.3+
```

### 5.2 架构层次

```
┌─────────────────────────────────────────┐
│  前端层: Vue 3 + Element Plus           │
└─────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────┐
│  API 网关层: Spring Cloud Gateway       │
└─────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────┐
│  业务服务层: Spring Boot 微服务         │
│  - lumina-system (系统管理)             │
│  - lumina-agent (Agent 核心)            │
│  - lumina-ai (AI 能力)                  │
└─────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────┐
│  Agent 引擎层: AgentScope Java           │
│  - ReAct Agent                          │
│  - 工具调用                              │
│  - 记忆管理                              │
│  - 多智能体协作                          │
└─────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────┐
│  基础设施层                              │
│  - MySQL (业务数据)                      │
│  - Redis (缓存/会话)                     │
│  - Kafka (消息队列)                      │
│  - 向量数据库 (RAG)                      │
└─────────────────────────────────────────┘
```

---

## 六、下一步行动

1. ✅ **确认技术选型** - 本文档
2. ⏳ **设计模块划分** - 确定项目结构
3. ⏳ **设计架构层次** - 详细架构设计
4. ⏳ **创建项目骨架** - Maven 多模块项目

---

## 七、待决策事项

1. **是否完全移除 Fastjson2？**
   - ✅ **决策: 统一使用 Jackson**
   - **理由**: 
     - AgentScope 使用 Jackson，避免依赖冲突
     - Spring Boot 默认支持，无需额外配置
     - 生态兼容性更好，国际化标准
     - 功能更强大，安全性更好
   - **详细分析**: 参见 `Fastjson2与Jackson对比分析.md`

2. **是否引入 MyBatis-Plus？**
   - ✅ **决策: 引入，后续开发优先使用**
   - **理由**: 简化 CRUD 操作，提升开发效率

3. **前端是否升级到 Vue 3？**
   - ✅ **决策: 升级到 Vue 3**
   - **说明**: 后续重新设计前端，无影响

4. **是否支持 GraalVM 原生镜像？**
   - ⏸️ **决策: 暂不考虑**
   - **理由**: AgentScope 支持但复杂度高，后续根据需求再考虑

---

---

## 八、已确认决策

### 8.1 技术选型确认

| 决策项 | 决策结果 | 说明 |
|--------|---------|------|
| **MyBatis-Plus** | ✅ 引入 | 后续开发优先使用 |
| **Vue 3** | ✅ 升级 | 后续重新设计前端 |
| **GraalVM** | ⏸️ 暂不考虑 | 按建议暂不考虑 |
| **Fastjson2 vs Jackson** | ✅ 统一使用 Jackson | 详见对比分析文档 |

### 8.2 项目集成方式

- ✅ **AgentScope-java**: 作为参考，不直接使用，自行构建项目模块进行集成
- ✅ **biwinai-bak**: 作为参考，避免落后技术，参考其项目结构减少 bug

---

**文档版本**: v1.1  
**创建时间**: 2025-01-19  
**最后更新**: 2025-01-19

