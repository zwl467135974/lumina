# J03 — Lumina AI 架构全景

> **前置要求**：已完成 [模块 A-K 全部](README.md)
> **预计阅读**：20 分钟
> **难度**：⭐⭐⭐⭐☆

---

## 这节解决什么问题

你已经学完了 58 篇 AI 教程。这节把所有知识**串成一张全景图**——一个 Agent 从创建到执行的完整旅程。

**这是整个 AI 专项的收官篇。**

---

## 全景图：一个 Agent 的完整旅程

```
┌─────────────────────────────────────────────────────────────────┐
│                      用户创建 Agent                                │
│  AgentConfig: { agentType, llmConfig, tools, knowledgeBaseIds }  │
└──────────────────────────┬──────────────────────────────────────┘
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                      用户执行 Agent                                │
│  "帮我分析上周销售数据"                                            │
└──────────────────────────┬──────────────────────────────────────┘
                           ▼
┌─────────────── 安全防护层 ────────────────────────────────────────┐
│  PromptInjectionFilter（输入检测 11 种注入模式）                    │
│  AgentRateLimiter（限流：每分钟≤30次）                              │
│  AgentConcurrencyLimiter（并发：同时≤5个）                          │
│  BudgetService（预算检查：含在途 RUNNING）                          │
└──────────────────────────┬──────────────────────────────────────┘
                           ▼
┌─────────────── 上下文构建 ────────────────────────────────────────┐
│  buildContextMessages():                                          │
│  1. System Prompt（react.txt + PromptInjection 防护）             │
│  2. 长期记忆（ReflectiveMemory 提取的关键事实）                     │
│  3. 短期记忆（MemoryManager 从 Redis 取最近 N 轮）                  │
│  4. RAG 检索（如果配了知识库）                                      │
│     → Embedding 向量化 → Qdrant 检索（tenant_id 隔离）             │
│     → 混合检索（向量+关键词）→ RRF 融合 → Reranker 精排             │
│  5. 当前用户消息                                                   │
└──────────────────────────┬──────────────────────────────────────┘
                           ▼
┌─────────────── ReAct 循环（核心）─────────────────────────────────┐
│  AgentScope 2.0 ReActAgent                                         │
│  ├── AgentStateStore: 从 Redis 加载/保存 Agent 完整状态（跨实例）   │
│  └── Tracer SPI: LuminaTraceTracer 全链路拦截                       │
│                                                                   │
│  ┌─► Reason: LLM 分析任务（ChatModelFactory + LlmResilienceWrapper）│
│  │     ↓ Trace: 记录 REASONING 步骤（Token + 耗时）                │
│  │   Act: 调用工具                                                │
│  │     ├── 内置工具（getCurrentTime/webSearch/calculate/httpRequest）│
│  │     ├── 自定义工具（@AgentTool 注解的方法）                     │
│  │     ├── MCP 工具（远程 Server，stdio/http/streamable-http）     │
│  │     └── Code Interpreter（Docker 沙箱执行代码）                │
│  │     ↓ Trace: 记录 TOOL_CALL 步骤（输入/输出/耗时）              │
│  │   Observe: 看工具返回结果                                      │
│  │     ↓                                                          │
│  └── 够回答了？──No──→ 回到 Reason                                │
│        │Yes                                                        │
│        ▼                                                          │
│  最终回答（流式输出 StreamChunk: REASONING/ACTING/FINAL）          │
└──────────────────────────┬──────────────────────────────────────┘
                           ▼
┌─────────────── 后处理 ────────────────────────────────────────────┐
│  OutputSanitizer（PII 脱敏：手机/身份证/邮箱/银行卡）               │
│  Token 统计（ExecuteResult.TokenUsage）                            │
│  成本计算（CostService: 输入价×Token/1000 + 输出价×Token/1000）     │
│  持久化（agent_task 表：token/model/cost）                          │
│  记忆更新（ReflectiveMemory 提取关键事实存 DB）                     │
│  Trace 落库（异步写入 lumina_agent_trace：steps JSON + Token 统计） │
│  审计记录（@Audit AOP 异步写入）                                    │
│  通知推送（预算告警→Webhook/企微）                                  │
└──────────────────────────┬──────────────────────────────────────┘
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                      返回给用户                                    │
│  前端 SSE 流式渲染（推理过程→工具调用→最终回答）                     │
└─────────────────────────────────────────────────────────────────┘
```

---

## 你学到的 18 大能力

| # | 能力 | 对应模块 |
|---|------|----------|
| 1 | LLM 大模型调用 | A |
| 2 | Prompt 工程 + 防注入 | A |
| 3 | ReAct / Plan-Execute Agent | B |
| 4 | AgentScope SDK 2.0 封装 | B |
| 5 | Provider Failover 容错 | B |
| 6 | Function Calling 工具调用 | C |
| 7 | MCP 协议接入 | C |
| 8 | RAG 混合检索 | D |
| 9 | 向量层租户隔离 | D |
| 10 | 双轨记忆（短期+长期） | E |
| 11 | 跨实例状态共享（AgentStateStore） | E |
| 12 | 流式输出 + 多模态 | F |
| 13 | Token 计费 + 预算管控 | G |
| 14 | 评估 + A/B + 回归 | H |
| 15 | 工作流编排（6 节点） | I |
| 16 | 人工审批 + Cron 触发 | I |
| 17 | Webhook + 代码沙箱 | J |
| 18 | 推理链可观测性（Trace） | K |

---

## 这些能力怎么组合

### 场景 1：智能客服
```
ReAct Agent + RAG（产品手册） + 短期记忆（多轮） + Prompt 防注入
```

### 场景 2：定时运维巡检
```
Cron 触发器 → 工作流（采集→分析→条件判断→P0人工审批/自动报告）
             → Webhook 告警推送
```

### 场景 3：数据分析助手
```
ReAct Agent + Code Interpreter（写代码分析） + RAG（数据字典）
           + Token 计费 + 预算管控
```

### 场景 4：Prompt 优化迭代
```
评估框架 + A/B Testing + 回归测试 → 持续优化
```

---

## 你现在是什么水平

学完 58 篇 AI 专项 + 前面 32 篇全栈基础，你现在是：

> **既懂企业级 Java 全栈开发（Spring Boot/MyBatis/Redis/Vue），又懂 AI Agent 全链路（LLM/RAG/工具/工作流/评估）的稀缺人才。**

---

## 下一步

还剩最后一块：[第三阶：原理深潜](../stage-3-mastery/README.md)（15 篇）——Spring IoC/AOP/事务/分布式锁/响应式编程的底层原理。学完你就是"八股文大师"。

> 🚀 [第三阶 → 八股文大师](../stage-3-mastery/README.md)

---

## 🎉 AI 专项 58 篇全部完成！

```
模块 A 基础认知    5 篇 ✅
模块 B Agent 核心  7 篇 ✅  （+B06 循环控制 / B07 模型路由）
模块 C 工具系统    6 篇 ✅  （+C06 工具错误恢复）
模块 D RAG 知识库  9 篇 ✅  （+D09 KB 分块策略）
模块 E 记忆对话    7 篇 ✅  （+E05 状态共享 / E06 上下文压缩 / E07 冷启恢复）
模块 F 输出交互    7 篇 ✅  （+F05 结构化输出 / F06 输出护栏 / F07 自动会话）
模块 G 成本管控    4 篇 ✅
模块 H 质量保障    4 篇 ✅
模块 I 编排自动化  5 篇 ✅
模块 J 集成全景    3 篇 ✅
模块 K 可观测性    1 篇 ✅  （推理链 Trace）
                  ─────
总计              58 篇 ✅
```

---

📝 **本篇撰写期间修正的代码**：无。
