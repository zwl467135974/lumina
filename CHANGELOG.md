# Changelog

本文件记录 Lumina 项目的版本变更历史。

格式基于 [Keep a Changelog](https://keepachangelog.com/)，版本号遵循 [Semantic Versioning](https://semver.org/)。

## [3.1.0] - 2026-07-10

### 后端代码审计与修复

#### 架构级改造
- **TenantLineHandler 自动检测**: 从硬编码 IGNORE_TABLES 白名单改为 `information_schema` 自动检测 + ALWAYS_IGNORE 双重保护，新建表不再需要手动维护忽略列表
- **Resilience4j 熔断器**: 手写 ToolCircuitBreaker 替换为 Resilience4j CircuitBreakerRegistry，消除所有竞态条件
- **Flowable 7.0 工作流引擎**: 引入 Flowable 作为 @Primary 工作流引擎，旧 DefaultWorkflowEngine 保留为 fallback。BPMN 转换器支持 YAML→BPMN 自动映射

#### 安全修复
- Gateway Header 注入越权（清除客户端伪造身份 Header）
- 白名单 startsWith 前缀绕过
- Token 黑名单接入
- SpEL 表达式注入 RCE（StandardEvaluationContext + 自定义 TypeLocator）
- 并行工作流 HashMap 竞态
- 9 处租户隔离 IDOR（会话/工作流/Prompt/知识库/预算/LlmProvider）
- DictController/OnlineUserController 全接口权限缺失
- 登录防暴力破解（Redis 失败计数 + 锁定）

#### 功能修复
- 循环工作流完全失效（LoopSignal 处理接入）
- 并行工作流后必停（executeParallelBranches 路由）
- 条件循环每轮重新求值
- 嵌套循环变量隔离（LoopSignal 携带 items/itemVar）
- Flowable 并行 Join 网关 + Loop 子图执行
- 在线用户功能链路修复（recordLogin 接入）

#### 工程化
- 217+ 测试基线（含 30 个集成测试）
- 9 个 VO 类新建 + 15 个 Controller DO→VO 转换
- 全项目构造器注入统一
- @Transactional 补全 + rollbackFor
- @Audit 注解全覆盖
- 权限实时缓存（Gateway Redis 读取，5min→2h TTL）

## [3.0.0] - 2026-07

### 稳定化与生产就绪

#### 数据层修复
- Flyway V17: 8 模块权限 + 菜单路由 + 3 角色分配 + 示例 Agent/Prompt 种子数据
- Flyway V18: 字段对齐（user real_name→nickname, tenant contact_name→contact 等）+ Agent 表增加 llm_config/tools/tenant_id
- Flyway V19: Agent 任务增加 model_name/provider（成本计算精确化）
- Flyway V20: 评估运行记录增加 model_name/provider
- Flyway V21: 菜单图标与路径规范化
- Flyway V22: 示例工作流种子数据

#### 接线断裂修复（P0）
- Agent LLM 配置/工具全链路打通（DTO→Domain→VO→执行引擎）
- 知识库文档上传 kbId 关联（KnowledgeService→KnowledgeDocumentDO）
- MenuController 从数据库权限表动态构建菜单树（不再硬编码）
- Prompt agent_type 全链路（DO→DTO→Service→前端表单/列表）
- 成本计算使用真实模型（AgentTaskDO.model_name → CostServiceImpl）
- 评估使用 Agent 专属配置（buildEvalConfig 解析 llmConfig）
- 异步评估不再产生重复行

#### 前端 UI/UX
- Luminous 暗色主题设计系统（130 CSS 变量 + Element Plus 覆盖）
- Dashboard 首页（统计卡片 + 近期任务 + 快捷操作）
- PageHeader 修复（description + #actions slot）
- i18n 全量国际化（350+ key，20+ 页面 $t() 替换）
- 暗色主题 CSS 变量化（消除硬编码 hex）
- 403/401 错误页
- Agent 状态 el-switch 切换
- 评估/知识库 Agent 选择改为下拉
- 审计日志查看页
- 前端设计技能包（skills/lumina_frontend_design）+ 自进化设计文档

#### 配置与部署
- Nacos 配置统一（本地仅 Nacos 连接，业务配置全放 nacos-config）
- Redis 无密码兼容（setPassword 空值跳过）
- spring.config.import 使用 optional（CI 无 Nacos 可运行）
- BCrypt 密码哈希修复
- Flyway 编码 UTF-8 + out-of-order

#### 联调修复
- JwtAuthenticationFilter → JwtAuthenticationGatewayFilterFactory 重命名
- MybatisPlusTenantConfig bean 覆盖 + 拦截器顺序
- 前端 API 路径 /base 前缀对齐
- 登录链路扁平结构适配
- Vite proxy 统一走 Gateway + keep-alive

### 测试基线
- 后端 300 单元测试（全 6 模块通过）
- 前端 80 测试（11 文件通过）

## [2.0.0] - 2026-07

### P0：测试补全 + 技术债清理
- 后端测试从 135 扩展到 355 用例（全 8 模块）
- 前端 vitest 80 用例（66 工具/Store + 14 组件）
- 删除遗留 `sql/` 目录，Flyway V1-V14 统一管理
- 提取 `StreamEventType` 常量类
- CI 后端/前端门禁全量通过

### P1：Agent 编排引擎
- DAG 工作流引擎（`DefaultWorkflowEngine`）
- 6 种节点：Agent / Condition / Loop / Parallel / Transform / Human
- SpEL 表达式求值
- YAML 工作流加载器 + 5 个预设模板
- 工作流定义/实例/执行日志持久化（Flyway V8）
- Workflow API + 前端管理页
- 可视化工作流设计器（Vue Flow 画布拖拽 + YAML 双向同步）
- 多 Agent 对话可视化 + Human-in-the-Loop 恢复机制
- 工作流 Micrometer 指标（execution/node duration Timer）

### P2：流式增强 + Agent 调试
- 多模态流式 SSE 接口
- 前端流式多模态执行
- Agent 调试面板（工具调用/Token/推理过程）
- RAG 来源可视化（`RAG_SOURCES` 流式事件 + 前端引用来源折叠面板）

### P3：生产可用性
- **P3-1 后台异步任务**：`lumina_agent_task` 表（V10）、专用线程池、状态流转、提交/查询 API、前端任务列表页
- **P3-2 成本管理**：模型价格表（V11）、CostService 费用计算、消费汇总 API、前端成本仪表盘 + 趋势图表
- **P3-3 安全防护**：PromptInjectionFilter 注入检测（11 种模式）、OutputSanitizer PII 脱敏、AgentRateLimiter 频率限制（Redis 滑动窗口）、ContentModerationService 内容审核

### Prompt 管理
- Flyway V9 `lumina_prompt` 表 + 种子数据
- Prompt CRUD + 版本管理 + 发布/激活
- Agent 执行链路接入 DB 激活 Prompt（租户优先 + 全局回退）
- 前端 Prompt 管理页 + Agent 运行时 Prompt 可见性

### E1：Agent 评估框架
- YAML 数据集管理 + 文件上传导入
- 4 种评分器：精确匹配 / 关键词包含 / 语义相似度（Embedding 余弦）/ LLM Judge（1-5 分制）
- 评估报告：分类统计 + ECharts 柱状图 + 历史趋势折线图
- 异步评估（大数据集）+ A/B 两次评估对比 + CSV 导出
- Flyway V13（dataset + run 两表）+ V14（status 字段）

### P4：K8s 部署完善
- Helm Chart 全量模板（gateway/business-base/agent-service/frontend）
- ConfigMap / Secret / Ingress / HPA
- NOTES.txt 安装后提示
- Nacos 动态路由实际验证通过

### P5：国际化 + 开源就绪
- LICENSE（Apache 2.0）
- CHANGELOG.md 变更日志
- 英文 README + 核心文档（ARCHITECTURE / QUICK_START / AGENT_DEVELOPMENT / WORKFLOW_DESIGN）

### 联调修复
- RedisConfig 读取 `spring.data.redis.password` 并 setPassword
- JwtAuthenticationFilter 重命名为 `JwtAuthenticationGatewayFilterFactory`（符合 Spring Cloud Gateway 命名约定）
- MybatisPlusTenantConfig bean 覆盖 + 拦截器顺序修正（租户先于分页）
- RoleController 新增 `/all` 端点（避免路径匹配冲突）
- business-agent 补充 Nacos Discovery 依赖
- 前端 API 路径补充 `/base` 前缀 + 登录链路扁平结构修复 + SUPER_ADMIN 放行
- Vite proxy 统一走 Gateway + keep-alive agent

### 文件存储
- StorageClient 抽象层（LocalDisk / MinIO）
- 多模态文件上传 + 历史回放

## [1.3.0]

- 多模态对话（文本 + 图片）
- OpenAI / DeepSeek / Claude / Ollama 多 Provider 适配
- Resilience4j LLM 容错（重试/熔断/降级）
- JaCoCo 覆盖率集成
- 前端 vitest 单元测试

## [1.2.0]

- RAG 知识库（Qdrant + Embedding）
- 全链路可观测（MDC + 审计 + Micrometer + OpenTelemetry）
- 前端增强（动态菜单/调试面板/暗色主题/i18n）

## [1.1.0]

- 多轮对话（Redis 热记忆 + DB 冷存储）
- 会话管理 API

## [1.0.0]

- 初始版本
- AgentScope ReAct Agent 集成
- 工具动态注册 + 记忆管理
- 多租户 RBAC + Spring Cloud 微服务架构
