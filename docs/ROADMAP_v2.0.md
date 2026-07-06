# Lumina v2.0.0 需求文档

> 基于 v1.3.0 项目评价分析制定。v1.3.0 完成了基础设施封装（P0-P3 全部交付），v2.0.0 聚焦 **Agent 平台核心竞争力的跨越式提升**——从"单 Agent 框架"升级为"Agent 编排平台"，同时补齐生产可用性短板。

---

## 评价基线（v1.3.0 现状）

### 已建立的优势

| 维度 | 水平 |
|------|------|
| 分层架构 | common→framework→agent-core→business，严格单向依赖，无环 |
| 基础设施封装 | 8 个 AutoConfiguration，业务模块零配置获得全套能力 |
| AgentScope 集成 | ReAct Agent + 工具动态注册 + 记忆管理 + RAG（自研 Qdrant REST） |
| 企业级特性 | 多租户 RBAC + 审计日志 + 全链路可观测 + LLM/工具双层熔断 |
| 工程治理 | Flyway V1-V7 + 10 个 Skill 规范 + 30 篇文档 + CI/CD 双流水线 |
| 部署体系 | docker-compose 13 服务 + Dockerfile 4 个 + K8s 清单 |
| 测试 | 后端 105 + 前端 30 = 135 个测试用例 |

### 评价发现的不足

| 编号 | 问题 | 影响 |
|------|------|------|
| G1 | 测试覆盖率低（18 文件 / 179 源文件 ≈ 10%） | 使用者信心不足，回归风险高 |
| G2 | Agent 编排能力单一（仅 ReAct） | 与 LangGraph/Dify/AutoGen 存在代差 |
| G3 | Helm Chart 半成品 | K8s 一键部署不可用 |
| G4 | 双 SQL 管理残留 | 文档误导 |
| G5 | 流式场景覆盖不全（多模态无流式、RAG 过程不可见） | 用户体验受限 |
| G6 | 网关动态路由未验证 | Spring Cloud Alibaba 核心能力存疑 |
| G7 | 文档全中文 | 国际化社区受限 |
| G8 | 前端缺少 Agent 调试能力 | 开发体验不完整 |

---

## 总览

| 优先级 | 主题 | 预估工时 | 核心价值 |
|--------|------|----------|----------|
| P0 | 测试补全 + 技术债清理 | 5-7 天 | 补齐框架质量基线，清理遗留问题 |
| P1 | Agent 编排引擎 | 12-15 天 | 从单 Agent 框架升级为编排平台（核心竞争力） |
| P2 | 流式增强 + Agent 调试 | 6-8 天 | 多模态流式 + RAG 过程可视化 + 调试面板 |
| P3 | 生产可用性 | 5-7 天 | 后台任务队列 + 成本管理 + 安全防护 |
| P4 | K8s 部署完善 | 3-4 天 | Helm 全量模板 + 动态路由验证 |
| P5 | 国际化 + 文档 | 2-3 天 | 英文文档 + 开源社区就绪 |
| 探索 | 差异化能力 | 灵活 | Agent 评估 / 插件市场 / 可视化编排 |

**总预估：33-44 天（核心 P0-P4）+ 探索方向灵活投入**

---

## 当前完成状态（截至 2026-07-03）

### 已完成

- P0：测试补全与技术债清理，CI 后端/前端门禁已可通过。
- P1：工作流编排引擎、模板 API、前端工作流管理入口、并行执行器修复。
- P2-1：多模态流式接口与前端流式多模态执行。
- P2-3：Agent 调试面板基础能力。
- Prompt 版本管理：DB 持久化、CRUD、版本发布/激活、前端管理页。
- Prompt 运行时接入：Agent 执行优先使用 DB 激活 Prompt，未命中回退内置 Prompt。
- Prompt 前端可见性：Agent 列表、表单、详情页展示运行时 Prompt 来源。

### 非 P3/P4/P5 剩余项

- P2-2 RAG 来源可视化：暂缓，需先拿到 AgentScope 检索来源回调或暴露检索命中文档片段。
- Prompt 固定版本绑定：暂不做，当前按 `agentType -> active Prompt` 动态生效；如需固定版本，后续再扩展 Agent 字段。

### 下一阶段重点

- 进入 P3/P4/P5：生产可用性、K8s 部署完善、国际化与开源就绪。

---

## P0：测试补全 + 技术债清理（5-7 天）

### P0-1：核心模块测试补全

**现状问题：** 后端 18 测试文件 / 179 源文件（10%），三个核心模块严重不足。

**改进内容：**

| 模块 | 现状 | 目标 | 重点测试类 |
|------|------|------|-----------|
| `lumina-gateway` | 0 测试 | ≥ 5 个测试文件 | `JwtAuthenticationFilter`（token 解析/过期/白名单）、`RateLimitGlobalFilter`（限流阈值/Redis 交互）、`DynamicRouteConfig`（Nacos 监听） |
| `lumina-framework` | 1 测试 | ≥ 6 个测试文件 | `GlobalExceptionHandler`（所有异常分支）、`LogContextInterceptor`（MDC 注入/清理）、`AuditAspect`（切面织入）、`RedisCacheManager`、`StorageClient`（LocalDisk + MinIO mock）、`TenantLineHandlerImpl`（SQL 改写） |
| `lumina-agent-core` | 3 测试 | ≥ 8 个测试文件 | `DefaultAgentExecutionEngine`（同步/流式/多模态）、`ChatModelFactory`（provider 路由）、`EnhancedToolManager`（动态注册/注销）、`MemoryManager`（热/冷记忆切换）、`QdrantRestStore`（CRUD + 搜索）、`LlmResilienceWrapper`（重试/熔断/降级）、`ConfigLoader`（YAML 解析）、`RagKnowledgeFactory` |

**验收标准：**
- JaCoCo 行覆盖率 ≥ 40%（起步目标）
- `lumina-gateway` 核心过滤器 100% 覆盖
- CI 中 JaCoCo check 布门禁失败

---

### P0-2：前端核心路径测试补全

**现状问题：** 前端 4 个测试文件仅覆盖工具函数，核心交互无测试。

**改进内容：**

| 测试对象 | 测试文件 | 覆盖点 |
|----------|----------|--------|
| `request.ts` | `request.test.ts` | Token 注入、401 跳转、错误码映射、超时处理 |
| `stores/user.ts` | `user.test.ts` | 登录/登出、token 管理、权限状态 |
| `stores/permission.ts` | `permission.test.ts` | 动态菜单生成、权限校验 |
| `composables/useTable.ts` | （已有，扩展） | 分页参数、排序、刷新 |
| `AgentChat.vue` | `AgentChat.test.ts` | 消息发送、流式接收、图片上传、历史加载 |
| `agent.ts` API | `agent.test.ts` | 接口参数构造、错误处理 |

**验收标准：**
- `pnpm test` ≥ 60 个用例（当前 30 → 60+）
- CI 前端 job 门禁 `pnpm test --passWithNoTests` 不允许降级

---

### P0-3：技术债清理

**清理清单：**

| 项目 | 说明 | 操作 |
|------|------|------|
| `sql/` 遗留脚本 | 已被 Flyway V1-V7 取代 | 删除整个 `sql/` 目录，README 指向 Flyway |
| `README.md` 版本信息 | "103 tests" 过时，Flyway V1-V6 过时 | 更新为 v1.3.0 → v2.0.0 实际数据 |
| `sql/README.md` | 废弃声明但文件仍在 | 随 `sql/` 目录一并删除 |
| `deploy/helm/` 空模板 | 只有 `_helpers.tpl` | P4 补全或暂时移除避免误导 |
| AgentScope 事件类型硬编码 | `AGENT_RESULT` / `REASONING` 硬编码字符串 | 提取为常量类 `StreamEventType` |

**验收标准：**
- 无废弃文件残留
- README 数据与实际一致
- 代码无硬编码魔法字符串

---

## P1：Agent 编排引擎（12-15 天）

> **这是 v2.0.0 的核心交付，决定 Lumina 从"框架"到"平台"的跨越。**

### P1-1：编排引擎核心抽象

**设计目标：** 支持多 Agent 协作、条件分支、循环控制、并行执行。

**核心模型设计：**

```
WorkflowDefinition（工作流定义）
├── nodes[]                    # 节点列表
│   ├── AgentNode              # Agent 执行节点（引用已注册 Agent）
│   ├── ToolNode               # 工具直接调用节点（不经 LLM）
│   ├── ConditionNode          # 条件分支节点（if/else/switch）
│   ├── LoopNode               # 循环节点（while/foreach）
│   ├── ParallelNode           # 并行执行节点（fan-out + fan-in）
│   ├── TransformNode          # 数据转换节点（JSONPath / 表达式）
│   └── HumanNode              # 人工审批节点（暂停 + 回调）
├── edges[]                    # 边（from → to + 条件表达式）
├── inputs                     # 工作流输入参数定义
├── outputs                    # 工作流输出映射
└── variables                  # 全局变量空间
```

**新增模块/包结构：**

```
lumina-agent-core/src/main/java/io/lumina/agent/orchestration/
├── model/
│   ├── WorkflowDefinition.java       # 工作流 DSL 定义
│   ├── WorkflowNode.java             # 节点基类（abstract）
│   ├── WorkflowEdge.java             # 边定义
│   ├── WorkflowContext.java          # 运行时上下文（变量空间 + 消息传递）
│   ├── WorkflowStatus.java           # PENDING / RUNNING / PAUSED / COMPLETED / FAILED
│   ├── AgentNode.java                # Agent 节点
│   ├── ConditionNode.java            # 条件节点
│   ├── LoopNode.java                 # 循环节点
│   ├── ParallelNode.java             # 并行节点
│   ├── TransformNode.java            # 数据转换节点
│   └── HumanNode.java                # 人工节点
├── engine/
│   ├── WorkflowEngine.java           # 引擎接口
│   ├── DefaultWorkflowEngine.java    # DAG 拓扑排序 + 调度器
│   ├── NodeExecutor.java             # 节点执行器接口（策略模式）
│   ├── AgentNodeExecutor.java        # Agent 节点执行
│   ├── ConditionNodeExecutor.java    # 条件求值
│   ├── LoopNodeExecutor.java         # 循环控制
│   ├── ParallelNodeExecutor.java     # 并行调度（Reactor Flux.merge）
│   └── TransformNodeExecutor.java    # 数据转换
├── expression/
│   ├── ExpressionEvaluator.java      # 表达式引擎接口
│   └── SpelExpressionEvaluator.java  # Spring Expression 实现
├── loader/
│   ├── WorkflowLoader.java           # 工作流加载接口
│   └── YamlWorkflowLoader.java       # YAML 定义加载
└── persistence/
    ├── WorkflowInstanceDO.java       # 工作流实例（运行时状态）
    ├── WorkflowInstanceMapper.java
    └── WorkflowExecutionLogDO.java   # 执行日志（每步输入/输出/耗时）
```

**工作流 YAML 示例：**

```yaml
name: "customer-complaint-handler"
description: "客户投诉处理流程"
inputs:
  - name: complaint
    type: string
    required: true
nodes:
  - id: classify
    type: agent
    agentId: 1                    # 意图分类 Agent
    input: "$.complaint"
    output: "category"
  
  - id: route
    type: condition
    expression: "#category == 'refund'"
    branches:
      - condition: true
        to: refund-agent
      - condition: false
        to: general-agent
  
  - id: refund-agent
    type: agent
    agentId: 2                    # 退款处理 Agent
    input: "$.complaint"
    output: "refund_result"
  
  - id: general-agent
    type: agent
    agentId: 3
    input: "$.complaint"
    output: "general_result"
  
  - id: notify
    type: parallel
    branches:
      - node: send-email
      - node: update-crm
    waitAll: true
edges:
  - from: classify
    to: route
  - from: refund-agent
    to: notify
  - from: general-agent
    to: notify
outputs:
  result: "$.refund_result ?: $.general_result"
```

**验收标准：**
- 支持串行、并行、条件分支、循环四种基本模式
- 工作流可 YAML 定义，可 DB 持久化
- 流式输出工作流执行进度（SSE 推送节点状态变更）
- 单元测试覆盖所有节点类型

---

### P1-2：多 Agent 协作模式

**在 P1-1 编排引擎基础上，内置常用协作模式：**

| 模式 | 说明 | 实现方式 |
|------|------|----------|
| **Supervisor-Worker** | 主管 Agent 分解任务 → 多个 Worker Agent 并行执行 → 主管汇总 | `ParallelNode` + 首尾两个 `AgentNode` |
| **辩论（Debate）** | 两个 Agent 对同一问题给出不同观点 → 裁判 Agent 综合判断 | `LoopNode`（多轮）+ 条件终止 |
| **流水线（Pipeline）** | Agent A 处理 → Agent B 加工 → Agent C 输出，前一个的输出是后一个的输入 | 串行 `AgentNode` 链 |
| **Router（路由）** | 分类 Agent 判断意图 → 路由到不同专业 Agent | `ConditionNode` + 多 `AgentNode` |
| **Human-in-the-Loop** | Agent 执行到关键步骤暂停 → 人工审批/修改 → 继续/终止 | `HumanNode`（暂停 + Webhook 回调） |

**预设模板：** 内置上述 5 种模式的工作流模板，用户可一键创建并修改。

**验收标准：**
- 5 种协作模式各有 YAML 模板
- 每种模式有对应的集成测试
- 前端可查看协作过程（多 Agent 对话气泡 + 流向图）

---

### P1-3：工作流持久化与可观测

**数据模型：**

```sql
-- V8__add_workflow_tables.sql

CREATE TABLE lumina_workflow_definition (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    name            VARCHAR(100) NOT NULL,
    description     VARCHAR(500),
    definition_yaml TEXT NOT NULL,         -- 完整 YAML 定义
    version         INT NOT NULL DEFAULT 1,
    status          TINYINT NOT NULL DEFAULT 0,  -- 0=草稿 1=已发布
    tenant_id       BIGINT NOT NULL,
    create_by       BIGINT,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted      TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_name_version (name, version, tenant_id)
);

CREATE TABLE lumina_workflow_instance (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    definition_id       BIGINT NOT NULL,
    definition_version  INT NOT NULL,
    status              VARCHAR(20) NOT NULL,   -- PENDING/RUNNING/PAUSED/COMPLETED/FAILED
    input               TEXT,                    -- JSON 输入
    output              TEXT,                    -- JSON 输出
    error_message       TEXT,
    current_node_id     VARCHAR(50),             -- 当前执行节点
    tenant_id           BIGINT NOT NULL,
    create_by           BIGINT,
    create_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_definition (definition_id),
    INDEX idx_status (status, tenant_id)
);

CREATE TABLE lumina_workflow_execution_log (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    instance_id         BIGINT NOT NULL,
    node_id             VARCHAR(50) NOT NULL,
    node_type           VARCHAR(30) NOT NULL,
    node_name           VARCHAR(100),
    status              VARCHAR(20) NOT NULL,   -- RUNNING/COMPLETED/FAILED/SKIPPED
    input               TEXT,                    -- JSON
    output              TEXT,                    -- JSON
    duration_ms         INT,
    error_message       TEXT,
    create_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_instance (instance_id, node_id)
);
```

**可观测能力：**
- 每个节点的输入/输出/耗时/状态持久化
- 流式推送节点状态变更（SSE）：
  - `NODE_STARTED` / `NODE_COMPLETED` / `NODE_FAILED` / `WORKFLOW_COMPLETED`
- 工作流执行历史可回放（查看每个节点的详细数据）
- Micrometer 指标：工作流执行次数 / 平均耗时 / 失败率 / 节点级耗时分布

---

### P1-4：工作流管理 API + 前端

**后端 API：**

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/workflows` | 创建工作流（YAML 或 JSON） |
| PUT | `/api/v1/workflows/{id}` | 更新工作流定义 |
| POST | `/api/v1/workflows/{id}/publish` | 发布工作流（草稿 → 已发布） |
| GET | `/api/v1/workflows` | 工作流列表（分页 + 按名称/状态搜索） |
| GET | `/api/v1/workflows/{id}` | 工作流详情 |
| DELETE | `/api/v1/workflows/{id}` | 删除工作流 |
| POST | `/api/v1/workflows/{id}/execute` | 执行工作流（同步返回 instanceId） |
| GET | `/api/v1/workflows/instances/{instanceId}` | 查询执行状态 |
| GET | `/api/v1/workflows/instances/{instanceId}/stream` | SSE 流式推送执行进度 |
| GET | `/api/v1/workflows/instances/{instanceId}/logs` | 执行日志详情 |
| GET | `/api/v1/workflows/templates` | 预设模板列表 |

**前端页面：**

```
views/workflow/
├── index.vue              # 工作流列表（卡片式 + 状态标签）
├── designer.vue           # 工作流设计器（画布拖拽 + YAML 双向编辑）
├── detail.vue             # 执行详情（节点流向图 + 每节点输入输出）
└── components/
    ├── WorkflowCanvas.vue  # 节点画布（基于 Vue Flow 或 AntV X6）
    ├── NodePanel.vue       # 节点拖拽面板
    └── ExecutionTrace.vue  # 执行轨迹可视化
```

---

## P2：流式增强 + Agent 调试（6-8 天）

### P2-1：流式多模态

**现状问题：** 多模态只有同步接口（`executeMultimodalSync`），用户体验差。

**改进内容：**

1. `AgentExecutionEngine` 新增 `executeMultimodalStream` 方法：
   ```java
   Flux<StreamChunk> executeMultimodalStream(
       Long agentId,
       String task,
       List<MultimodalImage> images,
       String conversationUuid
   );
   ```
2. `DefaultAgentExecutionEngine` 实现：构造含 `ImageBlock` 的 `Msg`，走 AgentScope `streamReActAgent` 流式链路
3. `AgentController` 新增 `POST /api/v1/agents/{id}/execute/multimodal/stream`（SSE）
4. 前端 `AgentChat.vue`：有图片时也走流式接口，实时显示回复

**验收标准：**
- 多模态消息支持流式输出（逐 token 返回）
- 图片 + 文本混合输入，LLM 流式回复正常
- Token 统计正确

---

### P2-2：RAG 过程可视化

**现状问题：** RAG 检索过程对用户完全黑盒，用户不知道"回复基于哪些文档"。

**改进内容：**

1. 后端 `RagKnowledgeFactory` 执行检索后，将命中的文档片段封装到 `StreamChunk`：
   ```java
   // 新增 StreamChunk 类型
   public enum StreamChunkType {
       REASONING, AGENT_RESULT, FINAL,
       TOOL_CALL, TOOL_RESULT,
       RAG_SOURCES    // 新增：RAG 检索来源
   }
   ```
2. `StreamChunk` 扩展 `ragSources` 字段：
   ```java
   private List<RagSource> ragSources;
   
   public static class RagSource {
       private String documentName;
       private String snippet;          // 匹配文本片段
       private double score;            // 相似度分数
       private int chunkIndex;          // 分片序号
   }
   ```
3. 前端 `AgentChat.vue`：
   - 收到 `RAG_SOURCES` 事件时，在回复上方显示"引用来源"折叠面板
   - 每个来源显示文档名 + 相似度 + 文本片段（高亮匹配关键词）
   - 点击来源可展开查看完整片段

**验收标准：**
- 用户能看到 Agent 回复引用了哪些知识库文档
- 每个引用显示相似度分数和匹配片段
- 流式过程中先推送来源，再推送生成内容

---

### P2-3：Agent 调试面板

**现状问题：** README 声称"Agent 调试面板"，实际只有对话界面。

**改进内容：**

在 `AgentChat.vue` 右侧增加可折叠的调试面板：

| 面板区块 | 显示内容 |
|----------|----------|
| **工具调用** | 每次工具调用的名称、参数（JSON）、返回值（JSON）、耗时（ms）。可展开/折叠 |
| **Token 统计** | 实时显示 promptTokens / completionTokens / totalTokens + 费用估算 |
| **模型配置** | 当前使用的 provider / model / temperature / maxTokens（只读） |
| **推理过程** | `REASONING` 事件独立展示区域（与最终回复分开） |
| **执行时间线** | 各阶段耗时：上下文构建 → LLM 调用 → 工具调用 → 总耗时 |

**前端组件结构：**

```
components/agent/
├── AgentChat.vue           # 主对话区（已有，增强）
├── AgentDebugPanel.vue     # 调试面板（新增）
│   ├── ToolCallList.vue    # 工具调用列表
│   ├── TokenStats.vue      # Token 统计
│   ├── ModelConfig.vue     # 模型配置展示
│   ├── ReasoningTrace.vue  # 推理过程
│   └── ExecutionTimeline.vue # 执行时间线
└── AgentLayout.vue         # 左右分栏布局（对话 + 调试）
```

**验收标准：**
- 工具调用详情完整可见（参数 + 返回值 + 耗时）
- Token 用量实时更新
- 推理过程独立展示，不混入最终回复
- 面板可折叠/展开，不影响对话体验

---

## P3：生产可用性（5-7 天）

### P3-1：Agent 后台任务队列

**现状问题：** Agent 执行完全请求驱动，长任务持有 HTTP 连接，断线即丢失。v1.3.0 P1-4 推迟项。

**改进内容：**

1. 新增 `POST /api/v1/agents/{id}/execute/async` — 提交异步任务，返回 `taskId`
2. 新增 `AgentTaskMessage` → 发送到 RocketMQ
3. 新增 `AgentTaskConsumer` — 消费者执行 Agent，结果持久化到 DB
4. 新增 `GET /api/v1/agents/tasks/{taskId}` — 查询任务状态和结果
5. 新增 `GET /api/v1/agents/tasks/{taskId}/stream` — SSE 推送实时进度（可选）
6. 任务状态流转：`QUEUED → RUNNING → COMPLETED / FAILED / CANCELLED`

**数据模型：**

```sql
-- V9__add_agent_task_table.sql

CREATE TABLE lumina_agent_task (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_uuid       VARCHAR(36) NOT NULL UNIQUE,
    agent_id        BIGINT NOT NULL,
    conversation_uuid VARCHAR(36),
    input_text      TEXT NOT NULL,
    file_ids        VARCHAR(500),
    status          VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    result          TEXT,
    error_message   TEXT,
    prompt_tokens   INT DEFAULT 0,
    completion_tokens INT DEFAULT 0,
    total_tokens    INT DEFAULT 0,
    duration_ms     INT,
    tenant_id       BIGINT NOT NULL,
    create_by       BIGINT,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_agent (agent_id, status),
    INDEX idx_tenant (tenant_id, status)
);
```

**验收标准：**
- 异步任务提交后立即返回 `taskId`（< 100ms）
- 任务断线后不丢失，Consumer 继续执行
- 可查询任务状态和结果
- 失败任务可查看错误信息

---

### P3-2：成本管理

**现状问题：** Token 统计已有，但无预算管控和成本分析。

**改进内容：**

1. **价格配置表** — 每个模型的 input/output token 单价（元/千 token）
   ```sql
   CREATE TABLE lumina_model_pricing (
       id              BIGINT PRIMARY KEY AUTO_INCREMENT,
       provider        VARCHAR(50) NOT NULL,
       model_name      VARCHAR(100) NOT NULL,
       input_price     DECIMAL(10, 6) NOT NULL,   -- 元/千 token
       output_price    DECIMAL(10, 6) NOT NULL,
       currency        VARCHAR(10) DEFAULT 'CNY',
       effective_from  DATE NOT NULL,
       tenant_id       BIGINT,                     -- NULL=全局，指定=租户级
       UNIQUE KEY uk_model (provider, model_name, effective_from)
   );
   ```
2. **预算规则** — 按 Agent / 租户 / 用户维度设置日/月预算上限
3. **消费记录** — 每次 Agent 执行后记录费用（Token 用量 × 单价）
4. **告警机制** — 预算达到 80% / 100% 时推送通知
5. **报表 API** — 按时间/Agent/租户维度查询消费汇总
6. **前端仪表盘** — `views/dashboard/cost.vue`：消费趋势图 + Top Agent + 预算使用率

**验收标准：**
- 每次 Agent 执行自动计算费用
- 预算超限时拒绝执行并返回友好提示
- 消费报表可按多维度查询

---

### P3-3：安全防护

**改进内容：**

| 防护层 | 措施 | 实现方式 |
|--------|------|----------|
| **输入过滤** | Prompt 注入检测 | `PromptInjectionFilter`（规则引擎 + 关键词检测） |
| **输出过滤** | 敏感信息脱敏 | `OutputSanitizeFilter`（正则匹配手机号/身份证/银行卡） |
| **频率控制** | 单 Agent/用户执行频率限制 | Redis 滑动窗口（补充网关全局限流） |
| **内容审核** | 可选接入内容审核 API | `ContentModerationService` 接口 + 可插拔实现 |
| **SQL 注入** | RAG 查询参数转义 | Qdrant 搜索 payload 安全编码 |

**验收标准：**
- Prompt 注入尝试被拦截并返回安全提示
- LLM 输出中的手机号/身份证自动脱敏
- 单用户对同一 Agent 的调用有频率限制

---

## P4：K8s 部署完善（3-4 天）

### P4-1：Helm Chart 补全

**现状问题：** `deploy/helm/lumina/` 只有 `_helpers.tpl`，无任何工作负载模板。

**改进内容：**

```
deploy/helm/lumina/
├── Chart.yaml
├── values.yaml                      # 全局默认值
├── values-prod.yaml                 # 生产环境覆盖
├── templates/
│   ├── _helpers.tpl                 # 已有
│   ├── gateway-deployment.yaml
│   ├── gateway-service.yaml
│   ├── gateway-hpa.yaml
│   ├── business-base-deployment.yaml
│   ├── business-base-service.yaml
│   ├── agent-service-deployment.yaml
│   ├── agent-service-service.yaml
│   ├── frontend-deployment.yaml
│   ├── frontend-service.yaml
│   ├── configmap.yaml               # 应用配置
│   ├── secret.yaml                  # 敏感信息（API Key / JWT Secret）
│   ├── ingress.yaml                 # Ingress 路由
│   └── NOTES.txt                    # 安装后提示
└── charts/                          # 依赖子 Chart（可选）
    ├── mysql/
    ├── redis/
    └── nacos/
```

**values.yaml 核心结构：**

```yaml
global:
  imageRegistry: ""
  imagePullSecrets: []
  storageClass: ""

gateway:
  enabled: true
  replicaCount: 2
  image:
    repository: lumina/gateway
    tag: "2.0.0"
  service:
    type: ClusterIP
    port: 8080
  ingress:
    enabled: true
    host: api.lumina.example.com
  hpa:
    enabled: true
    minReplicas: 2
    maxReplicas: 6
    cpuUtilization: 70

businessBase:
  enabled: true
  replicaCount: 2
  # ...

agentService:
  enabled: true
  replicaCount: 2
  # ...

frontend:
  enabled: true
  replicaCount: 2
  # ...

mysql:
  enabled: true              # false 时使用外部 MySQL
  auth:
    rootPassword: ""
    database: lumina_dev

redis:
  enabled: true

externalMySQL: {}            # 外部 MySQL 连接配置
externalRedis: {}            # 外部 Redis 连接配置
```

**验收标准：**
- `helm install lumina ./deploy/helm/lumina` 可成功部署全部服务
- 支持 `values-prod.yaml` 覆盖
- HPA 按 CPU 自动扩缩容
- Ingress 正确路由

---

### P4-2：Nacos 动态路由验证

**现状问题：** 声称支持 Nacos 动态路由但从未实际验证。

**改进内容：**

1. 本地启动 Nacos（docker-compose 已有）
2. 将 gateway 路由配置推到 Nacos Config（Data ID: `lumina-gateway-routes.json`）
3. 验证 Nacos 配置变更 → 网关路由热更新（无需重启）
4. 验证 Nacos 服务发现 → 新服务实例自动注册 → 网关自动路由
5. 编写验证文档 + 操作手册

**验收标准：**
- 在 Nacos 控制台修改路由配置，网关 5 秒内生效
- 新增 backend 实例，网关自动负载均衡到新实例
- 文档记录完整操作步骤

---

## P5：国际化 + 开源就绪（2-3 天）

### P5-1：文档国际化

**改进内容：**

1. `README.md` 重写为英文（保留中文版 `README.zh-CN.md`）
2. `docs/` 核心文档出英文版：
   - `ARCHITECTURE.md`（项目结构 + 模块设计 + 技术选型合并精简版）
   - `QUICK_START.md`（快速开始，5 分钟跑起来）
   - `AGENT_DEVELOPMENT.md`（Agent 开发指南）
   - `WORKFLOW_DESIGN.md`（工作流设计指南，P1 交付物）
   - `CONTRIBUTING.md`（贡献指南）
3. 代码注释保留中文（不阻塞），但公共 API Javadoc 补充英文摘要
4. `skills/` SKILL.md 保留中文（内部开发规范）

---

### P5-2：开源社区就绪

**检查清单：**

| 项目 | 说明 |
|------|------|
| LICENSE | 已有 Apache 2.0 ✓ |
| CONTRIBUTING.md | 新增：代码规范、PR 流程、Commit 规范 |
| CODE_OF_CONDUCT.md | 新增：社区行为准则 |
| ISSUE_TEMPLATE/ | 新增：Bug Report / Feature Request 模板 |
| PULL_REQUEST_TEMPLATE.md | 新增：PR 检查清单 |
| CHANGELOG.md | 新增：版本变更记录（v1.0 → v1.3 → v2.0） |
| .github/FUNDING.yml | 可选：赞助信息 |

---

## 探索方向（差异化竞争力）

> 以下方向有较高战略价值，但不设定 v2.0.0 强制交付时间线，根据资源和社区反馈灵活推进。

### E1：Agent 评估框架

**目标：** 内置自动化评估流水线，量化 Agent 质量。

**设计思路：**
1. 定义评估数据集（input + expected_output + 评分标准）
2. 批量运行 Agent → 收集实际输出
3. 自动评分：
   - 精确匹配（Exact Match）
   - 语义相似度（Embedding Cosine）
   - LLM 评分（用另一个 LLM 打分，如 GPT-4 as Judge）
4. 生成评估报告：准确率 / 平均延迟 / Token 消耗 / 失败案例

**新增模块：** `lumina-evaluation`（独立模块，可选依赖）

---

### E2：插件市场

**目标：** 第三方工具 / Prompt / Agent 模板 / 工作流模板的注册和分发。

**设计思路：**
1. 插件打包格式：`.lumina-plugin.zip`（含 manifest.json + 实现 jar + 资源文件）
2. 插件注册中心：`POST /api/v1/plugins/install`（上传 → 校验 → 热加载）
3. 插件市场前端：浏览 / 搜索 / 一键安装
4. 安全沙箱：插件工具运行在隔离 ClassLoader

---

### E3：可视化编排

**目标：** 前端画布式 Agent 工作流设计器。

**设计思路：**
1. 基于 [Vue Flow](https://vueflow.dev/) 或 [AntV X6](https://x6.antv.antgroup.com/) 实现节点画布
2. 拖拽节点 → 连线 → 配置属性 → 导出 YAML
3. 与 P1 工作流引擎无缝衔接：画布 ↔ YAML 双向同步
4. 实时执行预览：画布上高亮当前执行节点

**优先级：** P1 工作流引擎完成后的自然延伸，预计 P1 后立即推进。

---

### E4：多 Embedding 模型路由

**目标：** 按文档类型 / 语言自动选择最优 Embedding 模型。

**设计思路：**
1. 新增 `EmbeddingRouter` — 根据文档元数据（语言/类型/长度）选择模型
2. 中文文档 → DashScope text-embedding-v3
3. 英文文档 → OpenAI text-embedding-3-small
4. 代码文档 → 专用 code embedding 模型
5. 混合检索：多模型向量并行查询 → 分数融合

---

### E5：Agent 知识库联邦

**目标：** 跨租户 / 跨 Agent 的知识共享与权限隔离。

**设计思路：**
1. 知识库可见性级别：私有 / 团队 / 公共
2. 公共知识库由管理员维护（如法规库、产品文档库）
3. Agent 配置时可挂载多个知识库（私有 + 公共）
4. 检索时合并查询，结果标注来源知识库

---

## 实施顺序建议

```
阶段 1（第 1-2 周）：P0 测试补全 + 技术债清理
  → 建立质量基线，清理遗留，为后续开发扫清障碍

阶段 2（第 3-5 周）：P1 Agent 编排引擎
  → v2.0.0 核心交付，分 4 步：
    2a. 引擎核心抽象 + DAG 调度（P1-1）
    2b. 多 Agent 协作模式 + 模板（P1-2）
    2c. 持久化 + 可观测（P1-3）
    2d. 管理 API + 前端画布（P1-4 + E3）

阶段 3（第 6-7 周）：P2 流式增强 + 调试面板
  → 提升现有 Agent 体验

阶段 4（第 8-9 周）：P3 生产可用性
  → 后台任务 + 成本管理 + 安全防护

阶段 5（第 10 周）：P4 + P5 部署完善 + 国际化
  → K8s 部署就绪 + 开源社区就绪

探索方向：根据社区反馈和资源灵活投入
```

---

## v2.0.0 发布检查清单

- [x] P0：测试覆盖率补全（后端 300+，前端 80），无技术债残留
- [x] P1：工作流引擎可用，6 种节点 + 5 种协作模式 YAML 模板
- [x] Prompt：版本管理、运行时接入、前端生效状态展示
- [x] P2-1：多模态流式、Agent 调试面板基础能力
- [ ] P2-2：RAG 来源可视化（等待检索来源回调/暴露）
- [x] P3-1：异步任务队列（线程池实现，RocketMQ 待后续升级）
- [x] P3-2：成本管理（价格表 + 计费 + 汇总仪表盘，预算管控待后续）
- [x] P3-3：安全防护（Prompt 注入检测 + 输出 PII 脱敏）
- [x] P4：Helm Chart 全量模板
- [x] P5：英文 README、Apache 2.0 License + CHANGELOG.md
- [x] 后端全量测试通过（300+ 单元测试）
- [x] 前端全量测试通过（80 测试）
- [x] CHANGELOG.md 更新
- [x] README.md 更新版本和特性

---

## 版本对比

| 维度 | v1.3.0 | v2.0.0 目标 |
|------|--------|------------|
| Agent 模式 | 单 Agent ReAct | 多 Agent 协作 + 工作流编排 |
| 流式能力 | 文本流式 | 文本 + 多模态流式 + RAG 来源 |
| 调试能力 | 无 | 工具调用 + Token + 推理过程 |
| 异步执行 | 无 | 后台任务队列 + 断线续传 |
| 成本管理 | Token 统计 | 预算管控 + 消费报表 |
| 安全防护 | 输入校验 | Prompt 注入防护 + 输出脱敏 |
| 测试覆盖 | ~10%（135 用例） | ≥ 40%（300+ 用例） |
| K8s 部署 | 清单模板 | Helm Chart 一键部署 |
| 国际化 | 中文 | 英文文档 + 开源就绪 |
