# Lumina v1.3.0 需求文档

> 基于 v1.2.0 代码审查结果制定，聚焦代码层面的健壮性、异步化、模型对接扩展和工程治理。
> 不包含运维/部署类改进（灰度发布、APM Dashboard 等），纯粹是代码层面的提升。

---

## 总览

| 优先级 | 主题 | 预估工时 | 核心价值 |
|--------|------|----------|----------|
| P0 | 代码健壮性补全 | 3-4 天 | 修复现有 bug 和安全漏洞 |
| P1 | MQ + 异步化 | 5-7 天 | 解决同步阻塞，为生产可用打基础 |
| P2 | 模型对接扩展 | 7-10 天 | 多模态 + 更多 Provider + 容错 |
| P3 | 工程治理 | 2-3 天 | 覆盖率 + 前端测试 |

**总预估：17-24 天**

---

## P0：代码健壮性补全（3-4 天）

### P0-1：输入校验修复

**现状问题（代码审查发现）：**

| 问题 | 文件 | 风险 |
|------|------|------|
| `CreateAgentDTO` 零校验注解 | `agent/api/dto/CreateAgentDTO.java` | Controller 的 `@Valid` 形同虚设，agentName/agentType/description 可为 null/空/超长 |
| `ConstraintViolationException` 未被全局异常处理器捕获 | `framework/exception/GlobalExceptionHandler.java` | 分页参数 `@Min(1)` 校验失败返回 HTTP 500 而非 400 |
| `KnowledgeController` 无 `@Validated` | `agent/api/controller/KnowledgeController.java` | 文件上传无类型/大小限制，搜索无 limit 上限 |
| 分页 DTO 无 `@Max` 上限 | `UserQueryDTO` / `RoleQueryDTO` / `TenantQueryDTO` | `size=2147483647` 可致 OOM |
| `IllegalArgumentException` handler 泄露内部信息 | `GlobalExceptionHandler.java:99` | 直接返回 `e.getMessage()` 给客户端 |
| `LoginDTO` 无 `@Size` | 两个模块的 `LoginDTO.java` | 用户名/密码可无限长 |

**改进内容：**

1. `CreateAgentDTO` 加 `@NotBlank` / `@Size(max=100)` / `@Size(max=500)` 等约束
2. `GlobalExceptionHandler` 新增 6 个 ExceptionHandler：
   - `ConstraintViolationException` → 400 + 校验详情
   - `MissingServletRequestParameterException` → 400
   - `MethodArgumentTypeMismatchException` → 400
   - `HttpMessageNotReadableException` → 400
   - `MaxUploadSizeExceededException` → 400
   - `HttpRequestMethodNotSupportedException` → 405
3. `KnowledgeController` 加 `@Validated` + 文件大小限制（`spring.servlet.multipart.max-file-size`）+ 文件扩展名白名单（pdf/docx/txt/md）
4. 分页 DTO 加 `@Min(1)` / `@Max(100)` 约束
5. `LoginDTO` 加 `@Size(min=3, max=50)` 用户名 / `@Size(max=128)` 密码
6. `IllegalArgumentException` handler 返回通用消息，不暴露 `e.getMessage()`
7. 修正 `base/AuthController` 和 `agent/AuthController` 手动 token 校验 → 统一抛 `BusinessException(ErrorCode.TOKEN_INVALID)`

**验收标准：**
- 所有 Controller 入参有校验约束
- 非法输入返回 400 + 结构化错误信息，不返回 500
- 不泄露内部异常信息

---

### P0-2：Token 统计接通

**现状问题：**

`ExecuteResult.TokenUsage` 已定义 `promptTokens` / `completionTokens` / `totalTokens` 三个字段，
DB 表 `lumina_conversation_message` 也有对应列。但代码中**从未调用 `setTokenUsage`**，
`AgentServiceImpl` 读到的 `totalTokens` 永远为 0。

**改进内容：**

1. 在 `DefaultAgentExecutionEngine.executeSync` 中，从 AgentScope `Response`/`Result` 提取 usage 元数据
2. 填充到 `ExecuteResult.TokenUsage`
3. 确保 `AgentServiceImpl` 正确持久化到 DB
4. 流式模式（`executeStream`）在 `doOnComplete` 中累加 token 用量

**涉及文件：**
- `lumina-agent-core/.../engine/impl/DefaultAgentExecutionEngine.java`
- `lumina-modules/lumina-business-agent/.../service/impl/AgentServiceImpl.java`

**验收标准：**
- 同步/流式执行后，DB 中 `prompt_tokens` / `completion_tokens` / `total_tokens` 不为 0
- 单元测试验证 TokenUsage 正确提取

---

### P0-3：配置 Bug 修复

**问题清单：**

| Bug | 说明 |
|-----|------|
| `type: claude` vs `anthropic` | `LuminaAgentProperties.LLMConfig` 注释写 `claude`，`ChatModelFactory` 只认 `anthropic`。用户配 `claude` 直接报错 |
| 审计日志 Javadoc 误导 | `AuditAspect` Javadoc 写"由审计监听器异步持久化"，实际 `@EventListener` 是同步的 |
| `DASHSCOPE_API_KEY` 硬编码为通用 fallback | `DefaultAgentExecutionEngine.getApiKey()` 和 `RagKnowledgeFactory.getApiKey()` 用 `System.getenv("DASHSCOPE_API_KEY")` 作为所有 provider 的兜底，非 DashScope 部署也必须设这个变量 |

**改进内容：**
1. `ChatModelFactory` 兼容 `claude` 和 `anthropic` 两种值（或统一为 `anthropic` 并修复文档）
2. 修正 `AuditAspect` Javadoc（标注为同步，待 P1 改异步）
3. API Key fallback 按 `LLM_TYPE` 区分环境变量名

---

## P1：MQ + 异步化（5-7 天）

### P1-1：引入消息队列基础设施

**现状：** 项目零 MQ 基础设施，零 `@Async`，零线程池配置。

**技术选型：**
- **推荐 RocketMQ**（`spring-cloud-starter-stream-rocketmq`）— 与现有 Spring Cloud Alibaba 栈一致
- 备选 RabbitMQ（`spring-boot-starter-amqp`）— 如果团队更熟悉

**改进内容：**

1. docker-compose.yml 新增 MQ broker 服务
2. 根 pom.xml 新增 MQ starter 的 `dependencyManagement`
3. 新增 `lumina-framework/.../config/MQConfig.java` — 队列/交换机/绑定声明
4. 新增 `lumina-framework/.../config/AsyncConfig.java` — `@EnableAsync` + `ThreadPoolTaskExecutor` 配置
   ```yaml
   spring.task.execution.pool.core-size: 8
   spring.task.execution.pool.max-size: 32
   spring.task.execution.pool.queue-capacity: 200
   spring.task.execution.thread-name-prefix: lumina-async-
   ```

---

### P1-2：知识库文档处理异步化（最高 ROI）

**现状问题：**

`KnowledgeServiceImpl.uploadDocument` 全链路同步阻塞：
```
HTTP 请求 → PDF/Word 解析 → Embedding API 调用 → 向量库写入 → DB 插入 → 返回响应
```
大文件上传可阻塞 Tomcat worker 数十秒。`KnowledgeDocumentDO.status` 字段已存在（当前硬编码为 1=完成），设计上就是为异步准备的。

**改进内容：**

1. 上传接口改为立即返回 `jobId`，文档状态置为 `PROCESSING(0)`
2. 发送 `DocumentIngestMessage` 到 MQ 队列
3. 新增 `DocumentIngestConsumer` 消费者：
   - 解析文档（PDFBox/POI）
   - 调用 Embedding API 生成向量
   - 写入 Qdrant 向量库
   - 更新文档状态为 `COMPLETED(1)`
   - 失败时状态置为 `FAILED(2)` + 记录错误信息
4. 新增 `GET /api/v1/agent/knowledge/documents/{uuid}/status` 状态查询接口
5. 前端知识库页面改为上传后轮询状态

**验收标准：**
- 上传接口响应时间 < 200ms（不含解析/Embedding）
- 大文件（10MB+ PDF）上传不阻塞其他请求
- 处理失败可查询到失败原因
- 状态流转：PROCESSING → COMPLETED / FAILED

---

### P1-3：审计日志异步化

**现状问题：**

`AuditAspect` 发布 `AuditEvent` → `AuditLogEventListener` 同步执行 `auditLogMapper.insert()`。
每个被审计的业务请求都多一次 DB 写延迟。Javadoc 写了"异步"但实际不是。

**改进内容：**

方案 A（简单）：`AuditLogEventListener` 加 `@Async` + `AsyncConfig` 线程池
方案 B（可靠）：审计事件发到 MQ，消费者批量写入 DB

推荐先做方案 A（1 小时），后续视需求升级方案 B。

**验收标准：**
- 审计日志写入不阻塞业务请求
- 异常情况下审计日志不丢失（至少方案 A 有 try-catch + 降级日志）

---

### P1-4：Agent 后台任务队列（可选，视需求）

**现状问题：**

Agent 执行完全请求驱动，无任务队列。长耗时 ReAct 任务（多步工具调用）持有 HTTP/SSE 连接，
客户端断线则任务丢失。

**改进内容：**

1. 新增 `AgentTask` 消息体（agentId, input, conversationId, tenantId, userId）
2. 新增 `POST /api/v1/agent/{id}/execute/async` 接口 — 提交任务到队列，返回 taskId
3. 新增 `AgentTaskConsumer` — 消费者执行 Agent，结果持久化
4. 新增 `GET /api/v1/agent/tasks/{taskId}` — 查询任务状态和结果
5. 支持 Redis Pub/Sub 或 SSE 推送实时进度

**优先级说明：** 如果当前用户场景都是短任务（< 30s），此项可推迟到 v1.4。

---

## P2：模型对接扩展（7-10 天）

### P2-1：多模态消息支持

**现状问题：**

AgentScope 的 `Msg` / `ContentBlock` 本身支持图片/音频，但 Lumina 全链路只构造 `textContent`：
- `DefaultAgentExecutionEngine.buildContextMessages` 只用 `Msg.builder().textContent(...)`
- 前端 Agent 对话只支持文本输入
- `OpenAICompatibleEmbeddingModel` / `QdrantRestStore` 中 `ContentBlock` 非 `TextBlock` 时直接 `.toString()`

**改进内容：**

1. 后端：
   - `buildContextMessages` 支持 `ImageBlock`（URL / Base64）
   - 新增 `POST /api/v1/agent/{id}/execute/multimodal` 接口（或扩展现有接口参数）
   - 支持的图片格式：URL、Base64、上传文件（MultipartFile → 临时 URL）
2. 前端：
   - `AgentChat.vue` 对话框支持图片粘贴/上传
   - 图片预览 + 发送
3. 模型适配：自动路由到支持 Vision 的模型（qwen-vl / GLM-4V / GPT-4o / Claude Vision）

**验收标准：**
- 用户可在对话中发送图片
- Agent 能识别图片内容并回复
- 流式输出正常工作

---

### P2-2：更多 LLM Provider 原生适配

**现状：** 仅 4 个 chat provider（dashscope / openai / anthropic / ollama）。

**目标新增 Provider（按优先级）：**

| Provider | 适配方式 | 特殊处理 |
|----------|----------|----------|
| 智谱 GLM | OpenAI 兼容 | `base-url: https://open.bigmodel.cn/api/paas/v4` |
| 月之暗面 Kimi | OpenAI 兼容 | `base-url: https://api.moonshot.cn/v1` |
| 字节豆包 | OpenAI 兼容 | `base-url: https://ark.cn-beijing.volces.com/api/v3` |
| Minimax | OpenAI 兼容 | `base-url: https://api.minimax.chat/v1` |
| 百度文心 ERNIE | 独立 SDK | 独立 auth 流程（API Key + Secret Key → access_token） |
| 讯飞星火 | WebSocket | 独立签名机制（HMAC + 时间戳） |

**改进内容：**

1. OpenAI 兼容的 Provider（GLM/Kimi/豆包/Minimax）：
   - 在文档和配置示例中明确各家的 `base-url` 和 `model-name`
   - 验证流式输出兼容性
   - 补充单元测试
2. 文心 / 星火（非 OpenAI 兼容）：
   - 新增 `ErnieChatModel` / `SparkChatModel` 实现 AgentScope `Model` 接口
   - 或扩展 `ChatModelFactory` 增加对应分支
   - 配置化：`lumina.agent.llm.type=ernie` / `lumina.agent.llm.type=spark`

**验收标准：**
- 至少 4 个新 Provider 可正常对话（同步 + 流式）
- 每个 Provider 有配置示例和文档说明

---

### P2-3：LLM 容错（Resilience4j）

**现状问题：** LLM API 调用无重试、无超时、无熔断、无降级。DeepSeek 5xx 或硅基流动超时直接 500 给用户。

**改进内容：**

1. 新增 `spring-boot-starter-aop` 已有，加 `resilience4j-spring-boot3` 依赖
2. 新增 `ResilienceConfig.java`：
   ```java
   @Bean Retry llmRetry()         // 3 次，指数退避 100ms/200ms/400ms
   @Bean CircuitBreaker llmCb()   // 错误率 > 50% 开启熔断，10s 后半开
   @Bean TimeLimiter llmTimeout() // 60s 超时
   ```
3. `ChatModelFactory` 或 `DefaultAgentExecutionEngine` 包装 LLM 调用：
   - 重试：仅对 `IOException` / `TimeoutException` 重试，`BusinessException` 不重试
   - 熔断：连续失败开启熔断，返回降级提示
   - 降级：可选 fallback 模型（配置 `lumina.agent.llm.fallback-type` / `lumina.agent.llm.fallback-model`）
4. Embedding 调用同样包装（`RagKnowledgeFactory` / `OpenAICompatibleEmbeddingModel`）

**验收标准：**
- LLM API 临时不可用时自动重试，不直接报错
- 熔断开启后快速失败，不堆积请求
- 有降级提示或 fallback 模型响应

---

### P2-4：GenerateOptions 扩展

**现状问题：** `buildGenerateOptions` 只传 `temperature` + `maxTokens`。

**改进内容：**

`AgentConfig.LLMConfig` 和 `LuminaAgentProperties.LLMConfig` 新增可选字段：
- `topP`（核采样）
- `stop`（停止序列）
- `seed`（可复现）
- `responseFormat`（JSON 模式）
- `presencePenalty` / `frequencyPenalty`

`buildGenerateOptions` 按非 null 值传入 AgentScope `GenerateOptions.Builder`。

---

## P3：工程治理（2-3 天）

### P3-1：JaCoCo 覆盖率

**改进内容：**

1. 根 pom.xml `<pluginManagement>` 加 `jacoco-maven-plugin`
2. `prepare-agent` goal 绑定 `initialize` phase
3. `report` goal 绑定 `test` phase
4. CI（`.github/workflows/ci.yml`）上传 `target/site/jacoco/jacoco.xml` 到 Codecov
5. 设置覆盖率门禁：line coverage ≥ 60%（起步）

---

### P3-2：前端核心路径测试

**现状：** 后端 103 测试，前端 0 测试（`vitest` 已安装但未使用）。

**改进内容：**

1. `lumina-frontend/package.json` 添加 `"test": "vitest run"` 脚本
2. 测试覆盖范围（不追求全面，覆盖关键路径）：
   - `api/request.ts` — Token 注入、401 跳转、错误处理
   - `stores/modules/user.ts` — 登录/登出、权限状态
   - `composables/useTable.ts` — 分页逻辑（如果有）
   - `utils/` — 日期格式化、权限检查等纯函数
3. CI 前端 job 增加 `pnpm test` 步骤

**验收标准：**
- `pnpm test` 通过
- 关键路径（request 拦截器、user store）有测试覆盖

---

### P3-3：Maven 版本管理持续维护

> v1.3.0 已完成版本集中化（本次提交），后续需建立维护机制。

**改进内容：**

1. CI 增加 `mvn versions:display-dependency-updates` 步骤（月度执行）
2. 升级 Spring Boot 时检查 BOM 覆盖项（commons-lang3 / jackson / guava / lombok）是否需要对齐
3. AgentScope 版本升级时回归测试 QdrantRestStore 兼容性

---

## 实施顺序建议

```
第 1 周：P0 全部（校验修复 + Token 统计 + Bug 修复）
第 2 周：P1-1 + P1-2（MQ 基础设施 + 知识库异步化）
第 3 周：P1-3 + P2-3（审计异步 + LLM 容错）
第 4 周：P2-1 + P2-2（多模态 + Provider 扩展）
灵活：  P3（覆盖率 + 前端测试，穿插进行）
```

---

## 不包含在本期范围

以下改进有价值但不属于 v1.3.0 代码层面任务，推迟到后续版本：

- Agent 缓存策略（需先有性能基准数据）
- RAG 批量/并行搜索（需先有多集合场景）
- K8s 灰度发布 / Flagger（运维层面）
- APM Dashboard（需先有自定义 Micrometer 指标）
- 文档国际化 / 社区规范（非代码层面）
