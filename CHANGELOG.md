# Changelog

本文件记录 Lumina 项目的版本变更历史。

格式基于 [Keep a Changelog](https://keepachangelog.com/)，版本号遵循 [Semantic Versioning](https://semver.org/)。

## [3.11.0] - 2026-08-19

### 上下文工程 + 工具安全管线（融合 DeepSeek Harness 设计）

本次聚焦 Agent 核心能力代际升级：上下文管理从"固定条数窗口"进化到"Token 预算 + 两级压缩 +
溢出自愈"，工具执行引入"拦截器 → 审批 → 单调守卫"安全管线（对标开源竞品均为空白），
流式与异步任务的失败恢复全面加固。设计借鉴 [DeepSeek Harness](https://github.com/deepseek-ai/deepseek-harness)
的上下文工程与工具执行管线，机制移植、架构不搬。

#### 上下文工程（长对话不再打爆窗口）
- **Token 估算 + 输入侧预算**：新增 `TokenEstimator`（CJK ≈ 1 token/字），历史消息按
  `lumina.agent.memory.contextWindowTokens`（默认 16000）从最新向最旧装填、装不下即停——
  替换硬编码 20 条窗口；预算覆盖 [系统提示词+长期记忆+历史+当前输入（含多模态）]。
- **两级压缩管线**：第一级免模型确定性修剪（`ContextPruner`，超 4000 字符消息 head/tail
  + 省略标记，默认启用）；第二级 LLM 检查点摘要（8 段固定格式，关键路径/命令/错误串逐字
  保留，KV 前缀对齐命中 provider 缓存，收缩硬保证摘要必须小于原文）。修复
  `summary-max-tokens` 死配置。
- **Plan-Execute 路径同步升级**："最后 6 条 × 500 字符"改为预算装填（占预算 1/4）。
- **溢出恢复**：LLM 报上下文超限后确定性紧急压缩（保留 SYSTEM 头 + 最近 2 条 + 中段
  修剪段，毫秒级）重试，同步与流式路径均支持（`max-overflow-retries` 默认 1）。

#### 工具安全管线（开源竞品空白的企业差异点）
- **拦截器 → 审批 → 单调守卫**三层：`ToolExecutionInterceptor`（有序可扩展策略，
  DENY/ASK）→ `ToolApprovalPort`（allow-once 人工审批，fail-closed）→ `ToolGuard`
  （单调守卫：无 allow 臂，拒绝在所有策略之后终审、数学上不可被翻转）。
- **审批闭环**：通知渠道审批（站内/企微）+ 待决注册表 + 审批端点
  `POST /agents/tools/approvals/{id}`（新权限 `agent:tool-approval`，V50 种子，
  SUPER_ADMIN 默认授予），超时/异常/无接收人一律拒绝。
- **配置名单**：`lumina.agent.tool.security.deny-tools`（平台禁用）与
  `approval-tools`（高危审批，如 `code.execute`），总开关默认关闭。
- 安全拒绝对模型可见（可自纠）且不计入熔断（策略否决 ≠ 工具故障）。

#### 失败恢复加固
- **SSE 中断合成闭合**：complete/cancel/error 三种终态都落库并写入 Redis 记忆，
  中断时追加 `[本次响应因中断未完成]` 标记——消除"用户已问、模型无答"的孤儿消息，
  下一轮模型与用户看到一致的中断事实。
- **流式反思记忆**：修复 SSE 主路径不触发 Reflective Memory 的问题（与同步路径对齐）。
- **任务真取消**：`cancelTask` 通过 `RunningTaskRegistry` 中断执行线程，真正停止
  LLM 调用与 Token 消耗（此前只改状态位，调用继续烧钱）。

#### 工具结果外存化（spill）
- 超过阈值（默认 8000 字符）的工具结果全文存档（V51 `lumina_tool_artifact`，租户隔离），
  模型侧只留 head/tail 预览 + `artifactId`，新增 `util.getArtifact` 工具按需取回全文；
  取回结果不再 spill（防回环），存档失败降级硬截断。默认关闭
  （`lumina.agent.tool.spill.enabled`）。

#### 数据库迁移
- V50：`agent:tool-approval` 权限种子（挂 Agent 管理，SUPER_ADMIN 授予）
- V51：`lumina_tool_artifact` 工具结果存档表

#### 测试
- 新增 33 个单测：TokenEstimator（11）/ ContextPruner（4）/ 引擎预算装填与压缩（8）/
  ToolSecurityPipeline 单调性与 fail-closed（11，含"审批放行 + 守卫仍否决"核心用例）/
  ToolResultSpiller（5）；AgentTaskServiceImplTest 同步构造器签名。
- agent-core 359 全绿；business-agent 263 与基线逐类一致。

## [3.10.0] - 2026-07-28

### 全面代码审查修复（Release 质量加固）

本次为代码质量加固版本，基于四维度系统审查（CI 技术债 / 分层架构 / 异常处理 / 新功能质量），
修复 6 个 release 阻塞项 + 规范统一，无破坏性 API 变更。

#### 安全修复
- **LongTermMemoryController 鉴权漏洞修复**：`delete`/`deleteAll` 缺 `userId==null` 校验，
  匿名请求触发 `eq(userId, null)` 被 MyBatis-Plus 渲染为 `IS NULL`，存在全表删除风险。
  修复：抽取 `LongTermMemoryService`，所有方法强制 `requireAuthenticated(userId)`。

#### 架构合规
- **Controller 直接注入 Mapper 违规修复**：`LongTermMemoryController` / `ModelPricingController`
  CRUD 全直连 Mapper + 业务逻辑写在 Controller 层。修复：抽取 Service 层 + 新增 VO，
  DO 不再出 API 边界，Controller 只调 Service。

#### 功能 Bug 修复
- **DB 冷启记忆取"最早 100 条"而非"最近 100 条"**：`listMessages` 按 `createTime ASC`，
  `pageNum=1` 取 `[0, limit)` 即最早的。用户隔多日回来恢复的是最早对话，最近上下文全丢。
  修复：`DbColdStartMemoryLoader` 改用 `MessageMapper` DESC 取最近 N 条再反转。
- **模型路由判复杂度用"默认强力模型"**：`buildDefaultLlmConfig()` 返回空配置走默认（glm-4 之类），
  为了省 simpleModel 差价反而每次先烧一次复杂模型成本，路由净亏。
  修复：判复杂度改用 `simpleModel`（最便宜），未配置才 fallback 默认。
- **MultiAgentSupervisor 路由双向 contains 误匹配**：反向 `spec.name().contains(decision)`
  几乎必中（专家名单字就命中），多专家时路由不可预测。修复：改为严格匹配（相等或 decision
  以专家名开头）。附带修复 FINISH 判定（行首锚定，避免解释文本偶现 finish 误判）+ expert
  调用异常捕获（单专家失败不中断整个流程）。

#### 性能修复
- **MemoryManager 冷启动 warm-up 300 次 Redis 往返**：100 条消息逐条 `addMemoryToRedis`，
  每条 RPUSH+LTRIM+EXPIRE=3 次调用，共 300 次。修复：改用 `pushAllToList` 批量 + 单次
  trim/expire（3 次往返）。

#### 可观测性增强
- **RedisAgentStateStore.save 静默失败**：状态保存失败只 warn 不记指标，跨实例状态漂移
  无感知。修复：加 `agent.state.save.errors` counter。
- **ConfigLoader 热更新静默失败**：配置漂移直到下次缓存 TTL，期间实例行为不一致。
  修复：加 `agent.config.reload.errors` counter。
- **MultiAgentSupervisor traceCollector 死代码**：`setTraceCollector` 存了字段但 execute()
  从未调用。修复：`generateFinalSummary` 末尾记录 SUMMARIZE 步骤，MultiAgent 汇总过程
  在 Trace 中可见。

#### 规范统一
- **输出护栏重复检测阈值可配**：连续行数（默认 20）/ 唯一率（默认 0.1）从硬编码提为配置项
  `repetitionConsecutiveLines` / `repetitionUniqueRatio`，适配不同业务场景。
- **错误码语义修正**：`OpenAiCompatServiceImpl` 错用 `AGENT_NOT_FOUND` 表达 unknown model
  （前端会按 Agent 缺失处理），新增 `MODEL_NOT_FOUND(22005)`；补齐缺失业务域段：Tool/Prompt/
  Trace/OCR/Workflow 模板/MCP 配置（共 11 个新错误码）。
- **Jackson 实例统一**：8 处 `new ObjectMapper()` 绕过全局 `JacksonConfig`（PlanExecuteAgent +
  4 Reranker + 4 SearchProvider），统一改用 `JsonUtils.OBJECT_MAPPER`（已配 JavaTimeModule）；
  gateway `GlobalErrorHandler` 改构造注入容器内 ObjectMapper。
- **依赖注入规范化**：8 个文件的必需依赖从字段 `@Autowired` 改为构造器注入
  （`@RequiredArgsConstructor` 或并入现有构造器），字段 `@Autowired` 仅保留用于
  `required=false` 可选场景。

#### 输出护栏检查顺序修正（CI 修复延续）
- **重复检测必须在长度截断之前**：Agent 循环输出（重复 25 次）也超长，先被长度截断静默吞掉，
  丢失"Agent 故障"信号。修复：调整优先级 关键词 → 重复检测 → 长度截断。

## [3.9.0] - 2026-07-27

### AI Agent 深度能力补全

#### DB 冷启记忆恢复
- **修复隐性数据丢失 bug**：Redis 过期/重启后 Agent 失忆（数据在 MySQL 但不读）
- **ColdStartMemoryLoader 端口接口**：core 定义接口，business 实现（避免循环依赖）
- **@Lazy 注入 + Warm-up 回填**：冷启后从 DB 加载历史 + 回填 Redis

#### 会话级 Token 预算
- **CONVERSATION scope**：新增第四种预算范围（TENANT/AGENT/USER/CONVERSATION）
- **BudgetRuleDO.scopeIdStr**：V48 迁移，存储 conversationUuid
- **checkBudget(agentId, conversationUuid)**：接口签名扩展

#### 工具调用错误恢复
- **增强错误消息**：buildErrorResult() 附加参数 schema + 实际输入 + 重试引导
- **正确设置 ERROR state**：ToolResultBlock.builder().state(ERROR) 替代 error()（后者不设 state）
- **AgentScope ReAct 已内置自动重试**：工具错误自动反馈给 LLM 修正参数

#### 自动会话管理 API
- **POST /api/v1/agents/{id}/chat**：前端无需手动管理 conversationId
- 无 conversationId → 自动创建，返回 {conversationId, reply}

#### 知识库级分块策略
- **V49 迁移**：lumina_knowledge_base 新增 chunk_size/overlap/split_strategy 列
- **三级配置优先级**：KB 配置 → 全局 RagProperties → 硬编码默认
- **DocumentIngestMessage 新增 splitStrategy**：上传时从 KB 读取配置透传到 Consumer

#### 教学文档（58 篇）
- 新增 E07/C06/F07/D09 四篇教学
- 全部骨架教学扩写完成（G/I/J/H 模块共 10 篇）
- 自测题答案全部补齐

## [3.8.0] - 2026-07-24

### AI 核心能力三大补全

#### Agent 循环迭代限制（maxIters）
- **AgentConfig.maxIterations**：per-Agent 配置 ReAct 循环上限
- **LuminaAgentProperties.maxIterations**：全局默认值 10
- 防止 Agent 陷入死循环无限烧 Token

#### 结构化输出（JSON Mode）
- **AgentConfig.structuredOutputMode**：JSON_OBJECT / TEXT
- **GenerateOptions.responseFormat**：通过 AgentScope 2.0 API 约束 LLM 输出格式

#### 上下文压缩（Context Compression）
- **ContextSummarizer 接口 + ContextSummarizerImpl**：LLM 对旧消息生成摘要
- **CompressionConfig**：threshold=15 / recentKeepCount=5 / summaryMaxTokens=500
- 旧消息压缩为摘要注入，不直接丢弃

#### 多 Agent 协作（Supervisor 模式）
- **MultiAgentSupervisor**：LLM 路由器自动选择专家 Agent 执行
- **AgentConfig.SubAgentConfig**：专家配置（name/description/sysPrompt/llmConfig/toolConfig）
- **V47 迁移**：lumina_agent 新增 sub_agents 列
- 子 Agent 配置可继承父 Agent（null = 继承）

#### 动态模型路由
- **ModelRouter 接口 + ComplexityModelRouter**：LLM 判断 SIMPLE/COMPLEX → 选便宜/强力模型
- 成本优化：简单问题用 Flash（免费），复杂问题用 GLM-4

#### 输出护栏
- **OutputGuardrail 接口 + DefaultOutputGuardrail**：关键词拦截 + 长度截断 + 重复检测
- **GuardrailResult**：pass / block / rewrite 三种结果

## [3.7.0] - 2026-07-24

### AgentScope 2.0 升级 + Trace 可观测性

#### AgentScope 1.0.7 → 2.0.0 升级
- ChatModelFactory 10 个 import 路径迁移到 extensions 包
- `.memory()` → `.stateStore()`（RedisAgentStateStore 跨实例记忆共享）
- PlanExecuteAgent 移除 9 处 `.memory()` 调用

#### RedisAgentStateStore（跨实例记忆共享）
- 实现 AgentScope 2.0 AgentStateStore 接口
- Key: `lumina:agent:state:{userId}:{sessionId}:{stateKey}`，TTL 7 天
- AgentState.toJson()/fromJsonString() 序列化

#### 推理链 Trace 系统
- **LuminaTraceTracer**：实现 AgentScope Tracer SPI，全局拦截 Agent/Model/Tool 调用
- **Reactor Context 传播**：TraceContext 通过 contextWrite/deferContextual 跨线程传递
- **V45 迁移**：lumina_agent_trace 表（steps JSON 列）
- **前端可视化**：trace.vue 列表页 + 详情页 timeline（el-timeline）
- **V46 迁移**：推理追踪菜单 + 权限种子
- **数据清理**：AgentTraceCleanupJob 定时清理（默认保留 30 天）
- **全路径覆盖**：同步/流式/PlanAndExecute/FailoverChain 全覆盖
- **数据质量**：agentId 填充、REASONING durationMs、SUMMARIZE 步骤、taskUuid 关联

## [3.6.0] - 2026-07-23

### 企业级加固

#### 模型价格管理（P1）
- **价格管理 CRUD**: 新增 `ModelPricingController`（含 `@RequirePermission`），提供输入/输出价格的查询/创建/更新/删除接口
- **价格种子数据**: Flyway V44 灌入 18 条模型价格（GLM/Kimi/DashScope/Anthropic/Ollama），成本计算不再回退硬编码默认值
- **前端管理页**: 模型价格管理前端页面 + 菜单/权限种子

#### Token 追踪修复（P0）
- **同步执行**: `recordSyncTask` 写入 modelName/provider，`executeAgentForResult` 返回完整 ExecuteResult（含 TokenUsage）
- **多模态异步**: 多模态分支改调 `executeAgentMultimodalForResult`，token 不再丢失
- **流式执行**: `StreamChunk` 新增 `tokenUsage` 字段，从 FINAL/AGENT_RESULT 事件提取 token 信息

#### 工作流修复（P0）
- **PAUSED 上下文持久化**: 人工审批节点暂停时写入 `ctx.getVariables()` 到 `instance.output`，resume 正确恢复全部上下文变量

#### 预算增强（P1）
- **在途消耗追踪**: 预算检查计入 RUNNING 状态任务（防并发超额）
- **告警去重**: Redis `budget:alert:{ruleId}:{date}` 去重，防止告警轰炸

#### 安全加固（P0/P1）
- **Controller 权限审计**: Agent 模块 18 个 Controller 全部补 `io.lumina.common.annotation.RequirePermission`，由 `PermissionCheckInterceptor` 拦截校验
- **`RequirePermission` 迁移**: 从 base 模块迁移至 common 模块（`io.lumina.common.annotation`），解决 agent 模块编译期不可见问题
- **文件服务租户校验**: `FileService.getByUuid()` 增加显式 tenantId 防御性过滤
- **ControllerPermissionTest**: 回归测试验证所有写操作 Controller 有权限注解

#### MCP 运行时注册（P1）
- **工具自动注册**: `McpToolRegistrar.registerToolsFromServer` 改为 public，`registerServer()` 成功后自动拉取并注册工具到 `EnhancedToolManager`

#### Redis 修复
- **RAtomicLong/RBucket 类型不匹配**: `RedisCacheManager.incrementAndGetWithExpire` 统一使用 RAtomicLong，修复 expire 操作的类型不匹配

#### API 文档（P1）
- **Swagger 注解补齐**: Agent 模块 16 个 Controller 补 `@Tag`/`@Operation` 注解（共 118 个 @Operation），Swagger UI 分组清晰

#### 限流与并发控制（P1）
- **Per-Agent rate limit**: Redis 滑动窗口限流（Flyway V42）
- **Per-Agent maxConcurrent**: 信号量并发控制（Flyway V43）

#### 预防措施
- **Flyway 技能包强化**: `lumina_flyway` SKILL.md 新增 Step 0（DESCRIBE 查实际表结构再写 SQL）
- **CI 迁移检查**: `scripts/check-migration.sh` 集成到 CI workflow，迁移 SQL 列名检查前置
- **JaCoCo 门控提升**: 最低覆盖率从 10% 提升至 25%
- **AGENTS.md 检查清单**: 新增 3 项（Controller 权限、调用链完整性、数据持久化）

### 测试
- **关键路径回归测试**: 覆盖本次审计修复的全部 bug（AgentExecutionChainIntegrationTest 6 个、KnowledgeBaseIsolationTest 2 个、WorkflowPausedContextTest 2 个、PermissionInterceptorIntegrationTest 1 个、BudgetServiceUnitTest 5 个、AbTestServiceUnitTest 5 个）
- **总计 770 个测试全过**

### 智能运维助手最佳实践 Demo
- **examples/ops-platform/**: 覆盖全部 16 核心能力的真实业务场景（知识库 + DAG 工作流 + Cron 触发器 + 评估 + A/B + 预算 + 成本）
- **Python 自动化脚本**: 纯 stdlib 实现，零外部依赖
- **前端操作指南**: 含 16 张端到端截图

## [3.5.0] - 2026-07-20

### Cron 触发器
- **定时执行**: Agent 按 Cron 表达式定时执行，复用 `executeTask` 管线（Flyway V41 `lumina_agent_trigger` 表）
- **Redisson 分布式锁**: 防多实例重复触发，misfire 策略
- **管理 API**: 创建/暂停/恢复/手动触发/删除 + 前端管理页

### 可观测性
- **Grafana 3 个预置仪表盘**: Agent 执行 / 工具+RAG / 工作流+Trigger，provisioning 开箱即用
- **监控叠加文件**: `docker-compose-monitoring.yml` 任意模式一键加监控
- **健康检查**: actuator/health 暴露 DB/Redis 连通性

### 测试
- 230 测试通过（v3.4 的 208 + 22 个 trigger 测试）

## [3.4.0] - 2026-07-17

### standalone 单体模式
- **单 jar 部署**: base+agent+notification 合并为单进程，仅需 MySQL+Redis
- **docker-compose-standalone.yml**: 一条命令到登录页
- **StandaloneJwtFilter**: WebMVC OncePerRequestFilter 移植自 Gateway 的 JWT 过滤器

### OpenAI 兼容出口
- **/v1/chat/completions + /v1/models**: 标准 OpenAI SDK 直接调用
- **API Token 管理**: sk-xxx 格式 token，Gateway `ApiTokenAuthGlobalFilter` 认证

### 安全修复
- **向量层租户隔离**: Qdrant payload filter 下推 + tenant_id 索引（修复安全漏洞）
- **MCP 生产化**: streamable-http 传输 + headers 鉴权 + 重连健康检查 + 运行时动态注册

### 集成出口
- **Webhook 系统**: per-user/per-category 订阅 + HMAC-SHA256 签名 + 连续失败自动禁用
- **企业微信机器人**: markdown 着色 + 4096 字节分片 + 限频

### 定位重写
- 竞品对比表 + 收窄到"企业私有化 Agent 中台"

## [3.3.0] - 2026-07-16

### 多模态 PDF/Word 文档理解
- **文档直接喂 LLM**: PDF/Word 不再只走 RAG 入库，可直接作为对话附件投递给多模态 LLM（纯文本化方案）
- **MultimodalContent 统一接口**: sealed interface 统一图片（`MultimodalImage`）和文档（`MultimodalDocument`），引擎按 instanceof 分发到 ImageBlock/TextBlock
- **文档文本提取**: 复用 AgentScope PDFReader/WordReader 解析全文，超 50000 字符自动截断
- **前端预览分流**: 图片显示缩略图，PDF/Word 显示文件名 chip

### 多 Agent 高层封装
- **一键创建工作流**: `POST /api/v1/workflows/from-template`，传入模板名 + Agent 映射即可创建发布
- **占位符替换机制**: 模板中 `${agent1}` 自动替换为实际 Agent ID，替换后校验 + 自动发布
- **新增工作流模板**: `plan-execute`（Planner→Executor→Summarizer）、`group-chat`（多 Agent 轮流讨论→共识判断）
- **requiredAgents 元数据**: `getTemplates` 返回每个模板所需的 Agent 角色信息

### RAG 混合检索 + Reranker
- **HybridKnowledge**: 并行向量检索 + 关键词检索，RRF（Reciprocal Rank Fusion）算法融合两路结果
- **MySQL FULLTEXT 关键词路**: `lumina_knowledge_chunk` 表双写 chunk 原文，ngram 分词支持中文全文检索
- **三模式 Reranker**: SiliconFlow（免费 API）、Local（本地模型服务）、None（仅 RRF 融合不调模型）
- **配置驱动**: `lumina.rag.hybrid.enabled` + `lumina.rag.rerank.provider` 一键切换
- **Flyway V28**: `lumina_knowledge_chunk` 表（FULLTEXT ngram 索引）

### Plan-Execute 推理模式
- **PlanExecuteAgent**: 三阶段组合编排（Planner 分解任务 → Executor 逐步执行 → Summarizer 汇总）
- **agentType 切换**: 配置 `agentType: PlanAndExecute` 即可启用，向后兼容 ReAct
- **JSON 子任务解析**: 支持环绕文本、malformed JSON、超上限截断等边界场景
- **降级机制**: Planner 规划失败时自动降级为直接执行

### 评估回归
- **批量回归测试**: `POST /api/v1/evaluations/regression/batch`，一次跑多个数据集，聚合报告
- **基线标记**: `POST /api/v1/evaluations/runs/{id}/baseline`，标记基线 run 用于回归对比
- **Prompt 版本 diff**: `GET /api/v1/evaluations/prompts/compare`，行级差异对比
- **Prompt 版本绑定**: EvaluationRunDO 新增 `prompt_name`/`prompt_version`/`is_baseline` 字段
- **Flyway V29**: `lumina_evaluation_regression_rule` 表 + evaluation_run 扩展列

### Bug 修复
- **WorkflowDefinition.outputs 反序列化失败**: `MapEntry[]` 改为 `LinkedHashMap<String,String>`，修复所有模板加载失败
- **模板 transform 字段名错误**: `expression` → `transformExpr` 匹配 TransformNode 实际字段名
- **NoopReranker NPE**: null 输入未检查导致空指针
- **CI JDBC URL**: `SPRING_DATASOURCE_URL` 中 `3306:lumina_dev` 修正为 `3306/lumina_dev`

### 测试
- **新增 39 个单元测试**: HybridKnowledge(8)、RerankProvider(8)、PlanExecuteAgent(9)、MultimodalDocument(6)、WorkflowTemplate(2)、EvaluationRegression(5)
- **总计 487 个测试全过**（agent-core 258 + business-agent 170 + 其他 59）

### 端到端验证
- V28/V29 迁移: ✅ 表结构 + FULLTEXT 索引完整
- agent-service 启动: ✅ Bean 注册正常
- 多模态文档注入: ✅ 文档内容确认到达 LLM（containsSecret=true）
- Plan-Execute: ✅ 三阶段完整执行（5 子任务，Token 统计正确）
- 工作流模板: ✅ 7 模板加载 + 占位符替换无残留
- 评估回归: ✅ 版本 diff（3 处差异）+ 基线标记（is_baseline=1）

## [3.2.0] - 2026-07-15

### MCP 协议接入
- **MCP Server 接入**: 支持 Model Context Protocol，通过 stdio/http 两种传输方式连接外部 MCP Server，将其工具自动注册给 Agent（`McpClientRegistry` + `McpToolRegistrar`）
- **MCP echo 验证 server**: 新增 `scripts/mcp/echo_server.py`（零依赖 Python MCP Server），用于验证完整 MCP 接入链路
- **MCP 监控 API**: 新增 `GET /api/v1/mcp/servers` + `GET /api/v1/mcp/tools` 只读监控接口（`McpController`）
- **MCP 监控页面**: 前端新增 `views/monitor/mcp.vue`，展示 MCP Server 连接状态和工具列表
- **MCP 单元测试**: 23 个测试覆盖注册流程、容错策略、调用链路、文本提取、Schema 序列化

### 通用工具系统
- **GeneralToolProvider**: 4 个通用 Agent 工具（HTTP 请求、当前时间、网络搜索、数学计算），从 base 迁移到 agent-core，解决跨服务工具不可见问题
- **搜索 API 适配层**: 策略模式 + 配置驱动，支持智谱/Tavily/SerpAPI/Brave 四种搜索引擎，API key 通过环境变量注入
- **工具执行超时控制**: ToolDefinitionToAgentToolAdapter 支持可配置超时（默认 60s）

### 通知中心
- **独立通知模块**: `lumina-business-notification` 模块，支持站内通知、已读/未读管理、通知偏好设置
- **Flyway V26**: 通知中心数据表迁移

### RAG 增强
- **Qdrant 集成测试**: 6 个真实 Qdrant 集成测试覆盖 CRUD 全链路
- **payload 丢失 bug 修复**: `QdrantRestStore.doSearch` 恢复 source/category 等额外 payload 字段

### Bug 修复
- **流式记忆保存**: `executeStream` 的 `doOnNext` 同时匹配 `FINAL` 和 `AGENT_RESULT` 事件类型，修复流式对话不保存 Redis 热记忆的问题
- **集成测试 Redis 密码**: 三个模块的 `application-test.yml` 补充 Redis 密码配置，修复集成测试因 NOAUTH 全部失败
- **异常规范化**: 17 处裸 `RuntimeException` 改为 `BusinessException` + `ErrorCode`，ErrorCode 新增 7 个枚举值

### CI/CD 修复
- **CI Redis 密码**: CI 环境添加 `SPRING_DATA_REDIS_PASSWORD=""` 适配无密码 Redis
- **Dockerfile 多模块构建**: 后端 3 个 Dockerfile 重构为根目录全量编译模式（`mvn -pl {module} -am`），修复单模块 COPY 导致找不到父 pom 的致命问题
- **前端 Dockerfile**: npm→pnpm + 去掉 `--only=production`
- **CD context**: 后端 Docker 构建上下文改为根目录 + `file` 参数指定 Dockerfile
- **端口映射**: base 修正为 8082、agent 修正为 8081

### 前端
- **LumUploader 组件**: 可复用文件上传组件，支持图片/PDF/Word，从 AgentChat 内联逻辑抽离
- **AgentChat 重构**: 多模态上传支持扩展到文档类型（PDF/Word），删除 166 行内联上传代码
- **@Valid 校验**: 5 个 Controller 端点补充 `@Valid` 注解 + DTO 约束

### 测试扩充
- **新增约 130 个测试**（总计 ~455）：MCP 23、GeneralToolProvider 37、McpController 11、RAG Qdrant 6、NodeExecutor 22、FlowableBpmnConverter 8、SearchProvider 14、AgentExecutionHandlerBridge 6、KnowledgeService 13、ExecutionEngine 7

### 工程化
- **Gateway 路由**: 新增 `lumina-agent-mcp-route`（`/api/v1/mcp/**`）
- **Nacos 配置**: agent-service 新增 MCP 配置模板（默认不启用）

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
