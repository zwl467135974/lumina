# 11 — 微服务架构：Nacos + Gateway

> **前置要求**：已完成 [10-实战：前端开发](10-build-a-feature-frontend.md)
> **预计阅读**：25 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

前面你一直用 standalone 单体模式（一个进程）。但生产环境用**微服务模式**——Gateway + Base + Agent 三个独立服务。这节讲微服务架构的两个核心组件：

- **Nacos**：服务发现（服务之间怎么找到彼此）+ 配置中心（配置集中管理）
- **Gateway**：API 网关（统一入口、路由、认证）

---

## 为什么要微服务？先建立直觉

### 单体 vs 微服务

| | 单体（standalone） | 微服务 |
|---|---|---|
| 进程数 | 1 个 | 3+ 个 |
| 部署 | 简单（一个 jar） | 复杂（多个服务） |
| 扩展 | 整体扩 | 按需扩（Agent 负载大就只扩 Agent） |
| 故障隔离 | 一挂全挂 | 一个服务挂了其他还能用 |
| 适用 | 开发体验/PoC | 生产环境 |

### 微服务架构图

```
浏览器
  │
  ▼
Gateway（8080）────── 认证、路由、限流
  │
  ├──► Base 服务（8082）── 用户/权限/租户
  ├──► Agent 服务（8081）── Agent/工作流/知识库
  │
  ▼
Nacos（8848）────── 服务发现 + 配置中心
```

---

## Nacos：服务发现 + 配置中心

### 服务发现（服务之间怎么找到彼此）

**问题**：Gateway 要把请求转发给 Agent 服务。但 Agent 服务的 IP 可能变（容器重启、扩容）。Gateway 怎么知道 Agent 在哪？

**Nacos 解决**：每个服务启动时向 Nacos **注册**自己的地址。Gateway 要找 Agent 时，问 Nacos "Agent 服务在哪？"，Nacos 返回可用地址列表。

```
Agent 服务启动 → 向 Nacos 注册："我是 agent-service，IP 是 192.168.1.10:8081"
Gateway 路由 → 问 Nacos："agent-service 在哪？"
Nacos → "192.168.1.10:8081"
Gateway → 转发请求到 192.168.1.10:8081
```

### 配置中心（配置集中管理）

**问题**：3 个服务各自有 `application.yml`，改一个配置要改 3 个文件、重启 3 个服务。

**Nacos 解决**：配置统一存在 Nacos，服务启动时从 Nacos **拉取配置**。改配置在 Nacos 控制台改一次，所有服务自动更新。

### 在 Lumina 里怎么配

```yaml
# 文件：lumina-gateway/src/main/resources/application.yml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848       # Nacos 地址（服务注册）
      config:
        server-addr: localhost:8848       # Nacos 地址（配置拉取）
        import-check:
          enabled: false                   # CI 无 Nacos 时不报错
  config:
    import:
      - optional:nacos:lumina-gateway.yaml # ← 从 Nacos 拉这个配置文件
```

`optional:` 前缀表示"Nacos 不在也能启动"（本地开发或 CI 时）。

---

## Gateway：API 网关

### 类比：公司前台

访客（请求）来公司，先到**前台**——前台检查身份（JWT 校验）、查通讯录找对应部门（路由）、记录来访（日志）。不是让访客直接闯进办公区。

### Gateway 做什么

```
所有请求 → Gateway（8080）
              │
              ├── ① JWT 校验（JwtAuthenticationGatewayFilterFactory）
              ├── ② 路由转发（按 URL 匹配到不同服务）
              ├── ③ 限流（RateLimit）
              └── ④ 日志记录

  /api/v1/base/**  → Base 服务（8082）
  /api/v1/agents/** → Agent 服务（8081）
  /v1/chat/**       → Agent 服务（OpenAI 兼容）
```

### 路由配置（在 Nacos 里）

```yaml
# 文件：nacos-config/lumina-gateway.yaml（简化）
spring:
  cloud:
    gateway:
      routes:
        - id: lumina-agent-route
          uri: lb://lumina-agent-service     # lb:// = 负载均衡，从 Nacos 找服务
          predicates:
            - Path=/api/v1/agents/**          # 匹配这个路径
          filters:
            - StripPrefix=0                   # 不剥离前缀

        - id: lumina-base-route
          uri: lb://lumina-business-base
          predicates:
            - Path=/api/v1/base/**

      default-filters:                        # 所有路由都套的过滤器
        - JwtAuthentication                   # JWT 认证
        - name: Retry                         # 失败重试
```

### 四要素

| 要素 | 含义 | 示例 |
|------|------|------|
| `id` | 路由标识 | `lumina-agent-route` |
| `uri` | 目标地址 | `lb://lumina-agent-service`（lb=负载均衡） |
| `predicates` | 匹配条件 | `Path=/api/v1/agents/**` |
| `filters` | 过滤处理 | `StripPrefix=0`、`JwtAuthentication` |

---

## 自定义过滤器：JwtAuthenticationGatewayFilterFactory

Lumina 的 JWT 认证是自定义的 Gateway 过滤器：

```java
// 文件：lumina-gateway/.../filter/JwtAuthenticationGatewayFilterFactory.java
// 类名必须以 GatewayFilterFactory 结尾！
// 这样 Gateway 才能通过 application.yml 里的 name: JwtAuthentication 找到它
```

> ⚠️ **命名约定**：Spring Cloud Gateway 要求自定义过滤器类名必须以 `GatewayFilterFactory` 结尾。如果你命名成 `JwtAuthFilter`，Gateway 找不到它——这是最常踩的坑。

---

## standalone 模式为什么不用 Nacos/Gateway？

standalone 把三个服务合并成一个进程，不需要服务间调用——所以不需要 Nacos 服务发现、不需要 Gateway 路由。

```yaml
# standalone 的 application.yml
spring:
  cloud:
    nacos:
      discovery:
        enabled: false              # 不用服务发现
  autoconfigure:
    exclude:                        # 排除微服务组件
      - ...NacosDiscovery...
```

**认证怎么办？** standalone 用 `StandaloneJwtFilter`（WebMVC Filter），移植自 Gateway 的 JWT 过滤器逻辑。

---

## 动手试试

1. **安装并启动 Nacos**：
   ```bash
   wget https://github.com/alibaba/nacos/releases/download/3.1.1/nacos-server-3.1.1.zip
   unzip nacos-server-3.1.1.zip && cd nacos/bin && ./startup.sh -m standalone
   ```
2. **访问 Nacos 控制台**：http://localhost:8848/nacos（nacos/nacos）
3. **导入配置**：把 `nacos-config/*.yaml` 导入对应 Data ID
4. **启动三个服务**：Base(8082) → Agent(8081) → Gateway(8080)

---

## 小结

| 组件 | 职责 | 类比 |
|------|------|------|
| Nacos 服务发现 | 服务注册 + 查找 | 通讯录 |
| Nacos 配置中心 | 配置集中管理 | 公司制度文件 |
| Gateway 路由 | 按路径转发到不同服务 | 公司前台指路 |
| Gateway 过滤器 | JWT 校验、限流、日志 | 前台安检 |
| lb:// | 负载均衡（从 Nacos 找服务） | 多个工位自动分配 |

---

## 下一步

下一篇 [配置管理](12-config-management.md)——yml/Profile/Nacos/环境变量，配置优先级怎么排。

> 🚀 [12 — 配置管理 →](12-config-management.md)

---

## 自测题

1. **Gateway 怎么知道 Agent 服务在哪个 IP？**
   <details><summary>答案</summary>Agent 服务启动时向 Nacos 注册地址。Gateway 用 lb://lumina-agent-service 从 Nacos 查询可用地址。</details>

2. **自定义 Gateway 过滤器类名为什么必须以 GatewayFilterFactory 结尾？**
   <details><summary>答案</summary>Spring Cloud Gateway 的命名约定——Gateway 通过 application.yml 的 name: Xxx 找类名为 XxxGatewayFilterFactory 的类。不符合命名就找不到。</details>

3. **standalone 模式为什么不用 Gateway？**
   <details><summary>答案</summary>standalone 把三个服务合并成一个进程，不需要服务间网络调用，所以不需要 Gateway 路由。认证用 StandaloneJwtFilter 替代。</details>

---

📝 **本篇撰写期间修正的代码**：无。
