# Changelog

本文件记录 Lumina 项目的版本变更历史。

格式基于 [Keep a Changelog](https://keepachangelog.com/)，版本号遵循 [Semantic Versioning](https://semver.org/)。

## [2.0.0] - 2026-07

### P0：测试补全 + 技术债清理
- 后端测试从 135 扩展到 300+ 用例
- 前端 vitest 66 用例
- 删除遗留 `sql/` 目录，Flyway V1-V11 统一管理
- 提取 `StreamEventType` 常量类
- CI 后端/前端门禁全量通过

### P1：Agent 编排引擎
- DAG 工作流引擎（`DefaultWorkflowEngine`）
- 6 种节点：Agent / Condition / Loop / Parallel / Transform / Human
- SpEL 表达式求值
- YAML 工作流加载器 + 5 个预设模板
- 工作流定义/实例/执行日志持久化（Flyway V8）
- Workflow API + 前端管理页

### P2：流式增强 + Agent 调试
- 多模态流式 SSE 接口
- 前端流式多模态执行
- Agent 调试面板（工具调用/Token/推理过程）

### P3：生产可用性
- **P3-1 后台异步任务**：`lumina_agent_task` 表（V10）、专用线程池、状态流转、提交/查询 API
- **P3-2 成本管理**：模型价格表（V11）、CostService 费用计算、消费汇总 API、前端成本仪表盘
- **P3-3 安全防护**：PromptInjectionFilter 注入检测、OutputSanitizer PII 脱敏

### Prompt 管理
- Flyway V9 `lumina_prompt` 表 + 种子数据
- Prompt CRUD + 版本管理 + 发布/激活
- Agent 执行链路接入 DB 激活 Prompt（租户优先 + 全局回退）
- 前端 Prompt 管理页 + Agent 运行时 Prompt 可见性

### P4：K8s 部署完善
- Helm Chart 全量模板（gateway/business-base/agent-service/frontend）
- ConfigMap / Secret / Ingress / HPA
- NOTES.txt 安装后提示

### P5：国际化 + 开源就绪
- LICENSE（Apache 2.0）
- CONTRIBUTING.md 贡献指南
- CHANGELOG.md 变更日志
- GitHub Issue / PR 模板

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
