# 第四阶：AI/Agent 专项 — Lumina 的核心卖点

> **这是整个教学体系最大的模块（50 篇）。** Lumina 是 AI Agent 平台，AI 是它的灵魂。
>
> **前置建议**：至少完成[第一阶](../stage-1-foundation/README.md)后端基础（01-10）。
>
> **预计总时长**：20-30 小时

---

## 为什么 AI 有 32 篇

Lumina 的 16 大核心能力**全部围绕 AI/Agent**。从"什么是大模型"到"多 Agent 工作流编排"，概念密度极大、梯度极陡。

如果只给 AI 几篇篇幅，要么讲太浅（只懂概念不懂代码），要么讲太满（小白劝退）。所以按**学习曲线**拆成 10 个子模块，每个子模块自成体系，可独立学习。

---

## AI 概念的类比速查

AI 概念对小白最陌生，这阶**类比密度最高**。先记住这组类比，后面每篇都会用到：

| 概念 | 类比 | 一句话理解 |
|------|------|-----------|
| **LLM** | 一个读过全网书的高材生 | 它什么都知道一点，但不一定对，也不知道实时信息 |
| **Token** | 大模型按字收费的单位 | 约 ¾ 个英文单词或 ½ 个汉字，LLM 按 Token 计价 |
| **上下文窗口** | 高材生的"短期记忆容量" | 最多能同时记住多少 Token，超了就忘前面的 |
| **Prompt** | 给高材生的"任务说明书" | 写得好它干得好，写得烂它瞎发挥 |
| **Agent** | 能自己查资料、用工具的实习生 | 不只是一问一答，会自己思考→行动→再思考 |
| **Tool** | 实习生的工具箱（计算器/浏览器/代码） | LLM 自己只会"说话"，工具让它能"做事" |
| **RAG** | 开卷考试 | 先翻书（知识库）找相关页，再答题 |
| **Embedding** | 把文字变成 GPS 坐标 | 意思相近的文字坐标也相近，可以算距离 |
| **向量库** | 按坐标索引的图书馆 | 找"最近的书"= 找最相关的内容 |
| **MCP** | 工具的 USB 标准 | 装一个 MCP Server，所有支持 MCP 的 AI 都能用 |
| **流式输出** | 打字机效果 | 不等全写完，边想边吐字 |
| **多模态** | 不只看文字，还能看图 | 图片/PDF/Word 混合输入 |

---

## 10 个子模块地图

### 📗 模块 A：AI 基础认知（5 篇）— "什么是 AI"

**适合**：完全不懂 AI 的同学。学完你能用大白话解释 LLM 是什么。

| # | 标题 | 你将学会 |
|---|------|----------|
| A01 | [LLM 大模型基础](A01-llm-fundamentals.md) | 大模型是什么、怎么训练的、能力边界在哪 |
| A02 | [Token 与上下文窗口](A02-token-context-window.md) | Token 计量、上下文窗口、温度/Temperature 等参数 |
| A03 | [Prompt 工程入门](A03-prompt-engineering-basics.md) | System Prompt、模板、占位符——Lumina 的 react.txt |
| A04 | [Prompt 高级技巧](A04-prompt-advanced.md) | CoT 思维链、Few-shot 示例、角色扮演、防注入 |
| A05 | [从 Chatbot 到 Agent](A05-from-chatbot-to-agent.md) | 聊天机器人 vs Agent 的本质区别 |

---

### 📕 模块 B：Agent 核心（5 篇）— "Agent 怎么工作"

**适合**：想理解 Agent 内部循环的同学。

| # | 标题 | 你将学会 |
|---|------|----------|
| B01 | [Agent 是什么](B01-what-is-agent.md) | ReAct 模式：Reason（推理）→ Act（行动）→ 再推理 |
| B02 | [Plan-Execute 模式](B02-plan-execute-pattern.md) | 先拆解任务再逐步执行，对比 ReAct 的取舍 |
| B03 | [AgentScope SDK](B03-agentscope-sdk.md) | Lumina 底层依赖的 Agent 框架，封装层设计 |
| B04 | [Agent 配置体系](B04-agent-config-system.md) | AgentConfig/LLMConfig，ReAct 与 Plan-Execute 切换 |
| B05 | [Provider Failover](B05-provider-failover.md) | 主备链容灾：主模型挂了自动切备用 |

---

### 📦 模块 C：工具系统（5 篇）— "让 AI 能做事"

**适合**：想让 Agent 调用自定义工具的同学。

| # | 标题 | 你将学会 |
|---|------|----------|
| C01 | [工具调用原理](C01-tool-calling-principle.md) | Function Calling 协议：LLM 怎么决定调哪个工具 |
| C02 | [@AgentTool 注解](C02-agenttool-annotation.md) | 一个注解把普通 Java 方法变成 Agent 工具 |
| C03 | [内置工具系统](C03-built-in-tools.md) | HTTP 请求/当前时间/网络搜索/数学计算 |
| C04 | [MCP 协议](C04-mcp-protocol.md) | Model Context Protocol：统一工具接入标准 |
| C05 | [MCP 三种传输](C05-mcp-transports.md) | stdio/http/streamable-http 的区别和选型 |

---

### 📚 模块 D：RAG 知识库（8 篇）— 重点中的重点

**RAG 是 Lumina 最核心的 AI 能力之一，也是篇幅最大的子模块。**

| # | 标题 | 你将学会 |
|---|------|----------|
| D01 | [RAG 从零理解](D01-rag-from-scratch.md) | 用"开卷考试"类比讲清 RAG 全流程 |
| D02 | [Embedding 向量化](D02-embedding-vectors.md) | 文字→向量→相似度计算，EmbeddingModel |
| D03 | [向量数据库 Qdrant](D03-vector-database.md) | 向量存储与检索，QdrantRestStore 实现 |
| D04 | [混合检索](D04-hybrid-retrieval.md) | 向量检索 + 关键词 FULLTEXT 双路并行 |
| D05 | [RRF 融合 + Rerank](D05-rrf-rerank.md) | RRF 算法融合两路结果 + 重排序模型 |
| D06 | [OCR 文档解析](D06-ocr-document-parsing.md) | 5 种 OCR 引擎（百度/腾讯/阿里/本地/None） |
| D07 | [知识库联邦](D07-knowledge-base-federation.md) | 多知识库管理 + Per-Agent 挂载隔离 |
| D08 | [向量层租户隔离](D08-rag-tenant-isolation.md) | Qdrant payload 下推 + tenant_id 索引（安全关键） |

---

### 🧠 模块 E：记忆与对话（5 篇）

| # | 标题 | 你将学会 |
|---|------|----------|
| E01 | [短期记忆](E01-short-term-memory.md) | Redis 热存储 + Caffeine 内存降级 |
| E02 | [长期记忆](E02-long-term-memory.md) | Reflective Memory：LLM 提取关键事实 |
| E03 | [多轮上下文管理](E03-multiturn-context.md) | 窗口裁剪策略、上下文注入顺序 |
| E04 | [会话生命周期](E04-conversation-lifecycle.md) | conversationId UUID、创建/续聊/删除 |
| E05 | [跨实例状态共享](E05-agent-state-store.md) | AgentScope 2.0 AgentStateStore + Redis 持久化 |

---

### 📡 模块 F：输出与交互（4 篇）

| # | 标题 | 你将学会 |
|---|------|----------|
| F01 | [流式输出](F01-streaming-sse.md) | SSE 打字机 + Reactor Flux + StreamChunk |
| F02 | [多模态](F02-multimodal.md) | 图片/PDF/Word 输入，ImageBlock/TextBlock |
| F03 | [OpenAI 兼容出口](F03-openai-compat.md) | /v1/chat/completions，标准 SDK 直接对接 |
| F04 | [安全防护](F04-security-defense.md) | Prompt 注入检测 11 模式 + PII 脱敏 |

---

### 💰 模块 G：成本与管控（4 篇）

| # | 标题 | 你将学会 |
|---|------|----------|
| G01 | [模型价格管理](G01-model-pricing.md) | CRUD + 18 条预置价格（Flyway V44） |
| G02 | [Token 计费](G02-token-billing.md) | 成本计算公式 + 租户归集 |
| G03 | [预算管控](G03-budget-control.md) | 规则/在途追踪/告警去重 |
| G04 | [限流与并发](G04-rate-limit-concurrency.md) | 滑动窗口限流 + 信号量并发控制 |

---

### 📊 模块 H：质量保障（4 篇）

| # | 标题 | 你将学会 |
|---|------|----------|
| H01 | [评估框架](H01-evaluation-framework.md) | 数据集 + 运行 + 报告全流程 |
| H02 | [四种评分器](H02-four-scorers.md) | 精确匹配/包含/语义相似度/LLM Judge |
| H03 | [A/B Testing](H03-ab-testing.md) | 流量分发 + 同会话粘滞 + 效果对比 |
| H04 | [评估回归](H04-evaluation-regression.md) | 基线标记 + Prompt 版本对比 |

---

### 🔀 模块 I：编排与自动化（5 篇）

| # | 标题 | 你将学会 |
|---|------|----------|
| I01 | [工作流编排](I01-workflow-orchestration.md) | 多 Agent 协作：YAML 定义 + DAG 执行 |
| I02 | [六种节点类型](I02-six-node-types.md) | Agent/Condition/Loop/Parallel/Transform/Human |
| I03 | [Flowable BPMN](I03-flowable-bpmn-engine.md) | @Primary 替换默认引擎，YAML→BPMN 转换 |
| I04 | [人工审批](I04-human-in-the-loop.md) | PAUSED 暂停 + resume 恢复 + 上下文持久化 |
| I05 | [Cron 触发器](I05-cron-trigger.md) | 定时执行 + Redisson 分布式锁防重复 |

---

### 🔌 模块 J：集成与全景（3 篇）

| # | 标题 | 你将学会 |
|---|------|----------|
| J01 | [Webhook + 企业微信](J01-webhook-wechat-bot.md) | HMAC 签名 + 企微 markdown 渲染 + 限频 |
| J02 | [Code Interpreter](J02-code-interpreter.md) | 代码执行沙箱：Docker 隔离 + 资源限制 |
| J03 | [Lumina AI 架构全景](J03-lumina-ai-architecture.md) | 16 大能力串联总结：一个 Agent 从生到死 |

---

### 🔬 模块 K：可观测性（1 篇）— "看见 Agent 的思考过程"

**Agent 是黑盒？Trace 让推理链可见。** 这是 Lumina 3.7.0 新增的深度能力，涉及 Reactor 响应式编程的核心机制。

| # | 标题 | 你将学会 |
|---|------|----------|
| K01 | [推理链可观测性](K01-trace-observability.md) | Tracer SPI 原理 + Reactor Context 跨线程传播 + Trace 可视化 |

---

## 推荐学习路径

### 路径一：循序渐进（推荐）
```
A（基础认知）→ B（Agent 核心）→ C（工具）→ D（RAG）
→ E（记忆）→ F（输出）→ G（成本）→ H（评估）
→ I（编排）→ J（全景）→ K（可观测性）
```

### 路径二：项目驱动
先看 **J03 架构全景**建立鸟瞰图，然后按兴趣跳到任何子模块。

### 路径三：面试驱动
直接看 **J03 架构全景** + **D05 RRF/Rerank** + **B01 Agent** + **I02 节点类型**——这 4 篇覆盖面试最高频的 AI 问题。

---

## 这阶学完你能做什么

- [ ] 能用大白话给非技术人解释"AI Agent 是什么"
- [ ] 能在 Lumina 里创建一个 Agent 并配置工具
- [ ] 能讲清 RAG 的完整链路（文档→分块→向量→检索→增强）
- [ ] 能理解 SSE 流式输出的前后端配合
- [ ] 能解释 Token 计费和预算管控逻辑
- [ ] 能设计一个多 Agent 工作流
- [ ] 能评估 Agent 效果并做 A/B 测试
- [ ] 面试遇到 AI 相关问题，都能结合项目实例回答

> 🚀 **现在开始**：如果你是 AI 新手，从 [A01-llm-fundamentals.md](A01-llm-fundamentals.md) 开始。
> 如果你已经懂 LLM 基础，直接跳到 [B01-what-is-agent.md](B01-what-is-agent.md) 或 [D01-rag-from-scratch.md](D01-rag-from-scratch.md)。
