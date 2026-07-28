# Lumina 框架定位说明

> 本文档回答三个问题：**Lumina 是什么 / 为什么用它 / 什么时候不用它**。
> 品牌命名、Logo、视觉设计相关内容已归档至 [`docs/brand.md`](../brand.md)（0 用户前不投入）。

## 一、定位：企业私有化 Agent 中台

**Lumina 是一套开箱即用的企业 Agent 中台**，私有化部署、租户隔离、算得清账。

不是 LangGraph/Dify 那样的通用 Agent 框架或 LLMOps 平台，而是面向**需要私有化部署 + 多租户隔离**的中国 ToB 场景：企业 IT 部门、ToB 软件商、政企客户。

一句话主张：

> **"Dify 开源版没有的多租户/RBAC/预算/审计，Lumina 全有且带测试。"**

## 二、为什么选 Lumina

### 唯一领先维度：企业级特性

| 能力 | 实现位置 | 说明 |
|---|---|---|
| **行级多租户隔离** | `TenantLineHandlerImpl` fail-closed | 启动期自动检测 `tenant_id` 列，未识别的表默认拦截（不是放行） |
| **五表 RBAC** | user/role/permission/menu/tenant | 用户→角色→权限三级，注解拦截器 + 权限实时缓存 |
| **审计日志** | `@Audit` AOP + 异步落库 | 31 处关键操作自动记录，含 tenantId/userId/traceId |
| **预算管控** | model_pricing + budget 表 | Token 用量计费 + 消费汇总仪表盘 + 超额告警 |
| **Prompt 注入检测 + PII 脱敏** | business-agent/security/ | 11 种注入模式 + 手机/身份证/银行卡/邮箱脱敏 |
| **JWT fail-fast + 身份头防伪造** | gateway filter | 网关入口剥离客户端身份头，重注入 JWT 解析后的身份 |

以上每一项都有集成测试（`TenantIsolationIntegrationTest` 等），不是宣传。

### 竞品对比（诚实版）

| 维度 | LangGraph | Dify | Spring AI Alibaba | **Lumina** |
|---|---|---|---|---|
| Agent 编排 | **5** | 4 | 4 | 3 |
| 多 Agent 协作 | **5** | 3 | 4 | **4.0** ✅ Supervisor 模式 + DAG 工作流 |
| RAG 完成度 | 3 | **5** | 3.5 | 3.5 |
| 工具生态 | **5** | **5** | 3.5 | 3.5（MCP 三传输已生产化） |
| **企业级（多租户/RBAC）** | 2 | 3.5 | 2 | **4.5** ✅ |
| 工作流引擎 | 4 | **5** | 3.5 | 3 |
| 文档/社区 | **5** | **5** | 4 | 1 ⚠️ 单人项目 |
| 开发者体验(DX) | 4 | **5** | 4 | 3.5（standalone 已交付） |

完整维度对比见 [`市场定位分析.md`](strategy/市场定位分析.md)。

## 三、什么时候选 Lumina

✅ **推荐**：
- 需要私有化部署（数据不能出内网）
- 需要多租户（一套实例服务多个业务线/客户）
- Java 技术栈（Spring Cloud Alibaba / MyBatis-Plus / Nacos）
- 需要算清账（Token 成本按租户/Agent 维度归集）
- 需要审计合规（操作可追溯）

❌ **不推荐**：
- 要做通用 AI 应用平台（选 Dify/Coze）
- 要做研究型 Agent（选 LangGraph）
- 已有 Spring AI 应用想加 Agent 能力（选 Spring AI Alibaba）
- 需要 Python 技术栈
- 需要海量插件市场（Dify 1000+ 插件）
- 需要复杂多 Agent 协作（LangGraph swarm/handoff）

## 四、技术栈速览

- **后端**：Java 21 + Spring Boot 3.3.5 + Spring Cloud Alibaba + MyBatis-Plus + Flowable 7.0.1 + Resilience4j
- **Agent 底座**：AgentScope Java SDK 2.0.0（ReAct/Plan-Execute/Toolkit/StreamOptions）
- **前端**：Vue 3 + TypeScript + Element Plus + Pinia
- **架构**：Gateway(8080) + Agent(8081) + Base(8082) 微服务；standalone 模式可单体部署
- **API 文档**：SpringDoc OpenAPI 2.6.0（Swagger UI + JWT 安全方案）
- **定时调度**：Cron 触发器（Redisson 分布式锁，misfire 策略）
- **成本管控**：模型价格管理（Flyway V44，18 条预置价格）

## 五、决策树：Lumina vs 竞品

```
你的场景是？
│
├─ 企业内私有化 + 多租户 ──────► Lumina ✅
│   （Dify 开源版无多租户；LangGraph 不管这层）
│
├─ 通用 AI 应用 / 快速原型 ────► Dify
│   （拖拽 + 插件市场 + LLMOps）
│
├─ 复杂状态机 / 可审计编排 ────► LangGraph
│   （金融/合规场景）
│
├─ 已有 Spring 应用加 Agent ───► Spring AI Alibaba
│   （一个 starter 依赖引入）
│
└─ Python 团队 / 研究 ─────────► LangChain / CrewAI
```

## 六、后续路线

- v3.4 已补齐：standalone 单体模式 / OpenAI 兼容出口 / MCP 生产化 / Webhook+企微 / 向量层租户隔离修复
- v3.5 已补齐：Cron 触发器（定时执行）/ Grafana 3 个预置仪表盘 / 监控叠加文件
- v3.6 已补齐：模型价格管理 / Token 追踪修复 / Controller 权限审计 / 工作流 PAUSED 上下文修复 / API 文档完善（Swagger）/ 预算在途追踪 / 限流并发控制
- 规划中：真 multi-agent handoff（接入 AgentScope MsgHub）/ 可观测性 dashboard / 更多渠道（钉钉/飞书/Slack）

详见 [市场定位分析](strategy/市场定位分析.md) 与各 [路线图](roadmap/)。
