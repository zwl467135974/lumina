# Agent 开发指南

## 概述

Lumina Agent 基于 AgentScope 的 ReAct 模式构建。每个 Agent 可以使用工具、访问 RAG 知识库、维护多轮对话记忆。

## 创建 Agent

### 通过 UI
1. 进入 **Agent 管理** → **新建**
2. 填写名称和类型（如 `assistant`、`react`、`customer-service`）
3. 启用 Agent（状态 = 启用）

### 通过 API
```bash
curl -X POST http://localhost:8080/api/v1/agents \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"agentName": "my-agent", "agentType": "assistant"}'
```

## Agent 类型

`agentType` 决定使用的 Prompt 模板：

| 类型 | Prompt 来源 | 适用场景 |
|------|------------|---------|
| `assistant` | `prompts/assistant.txt` 或 DB 激活版本 | 通用问答 |
| `react` | `prompts/react.txt` | 工具调用推理 |
| 自定义 | `prompts/{type}.txt` | 领域专用 |

## Prompt 管理

Prompt 在数据库中版本化管理，运行时解析顺序：

1. **DB 激活 Prompt**：按 `agentType.toLowerCase()` 查找 → 租户优先 → 全局回退
2. **内置回退**：`agent-core/src/main/resources/prompts/{type}.txt`

自定义 Prompt：
1. 进入 **Prompt 管理** → **新建**
2. 名称与 Agent 类型匹配（如 `assistant`）
3. 编写系统提示词，用 `{0}` 占位用户输入
4. 发布并激活

## 执行方式

### 同步执行
```bash
POST /api/v1/agents/{id}/execute?task=你好
```

### 流式执行（SSE 打字机效果）
```bash
POST /api/v1/agents/{id}/execute/stream?task=你好
# 返回事件流：REASONING_CHUNK → ACTING_CHUNK → FINAL
```

### 异步任务
```bash
POST /api/v1/agents/{id}/execute/async   # 立即返回 taskUuid
GET /api/v1/agents/tasks/{uuid}/stream    # SSE 进度推送
```

### 多模态（图片 + 文本）
```bash
POST /api/v1/agents/{id}/execute/multimodal/stream
# Body: {"task": "描述这张图片", "fileUuids": ["uuid-1"]}
```

## 安全管线

所有执行请求依次通过：

1. **频率限制** — Redis 分布式令牌桶（默认 30 次/分钟/用户+Agent）
2. **预算检查** — 超出日/月预算上限时拒绝执行
3. **内容审核** — 规则引擎检测暴力/违法/仇恨/敏感信息
4. **Prompt 注入防护** — 11 种注入模式 + 4 个高风险标记
5. **执行** — AgentScope ReAct 引擎
6. **输出脱敏** — 自动遮盖手机号/身份证/银行卡/邮箱

## 工具开发

实现工具接口即可自动注册：

```java
@Component
public class WeatherTool implements AgentTool {
    @ToolMethod(description = "查询城市天气")
    public String getWeather(@ToolParam(description = "城市名称") String city) {
        return "晴天，25°C";
    }
}
```

工具通过 `EnhancedToolManager` 自动注册，调用记录在调试面板可见。

## RAG 知识库

1. 通过 **知识库管理** → **上传** 添加文档
2. 文档自动切片 → 向量化（DashScope/OpenAI/Ollama）→ 存入 Qdrant
3. Agent 自动检索相关片段（GENERIC 模式）或按需检索（AGENTIC 模式）
4. 检索来源在对话中显示为 **引用来源**

## 模型配置

通过环境变量配置 LLM：

```bash
LLM_TYPE=dashscope        # dashscope / openai / deepseek / anthropic / ollama
LLM_API_KEY=your-key
LLM_MODEL=qwen-plus
LLM_TEMPERATURE=0.7
LLM_MAX_TOKENS=2000
```

所有 LLM 调用由 Resilience4j 包装：重试 3 次 + 熔断器（50% 失败率阈值）。
