# B04 — Agent 配置体系

> **前置要求**：已完成 [B03-AgentScope SDK](B03-agentscope-sdk.md)
> **预计阅读**：15 分钟
> **难度**：⭐⭐☆☆☆

---

## 这节解决什么问题

创建一个 Agent 时，你要配名称、类型、LLM 模型、工具、知识库……这些配置在代码里怎么表示？怎么从数据库加载到引擎？

---

## AgentConfig：一切配置的容器

```java
// 文件：lumina-agent-core/.../model/AgentConfig.java
@Data
public class AgentConfig implements Serializable {
    private Long agentId;
    private String agentName;
    private String agentType;            // ReAct / PlanAndExecute
    private LLMConfig llmConfig;         // 大模型配置
    private ToolConfig toolConfig;       // 工具配置
    private MemoryConfig memoryConfig;   // 记忆配置
    private String promptTemplate;       // Prompt 模板
    private List<Long> knowledgeBaseIds; // 挂载的知识库
    private Map<String, Object> extraParams;

    // —— v3.8+ 新增：Agent 行为控制 ——
    private Integer maxIterations;       // ReAct 循环上限（防止死循环烧 Token）
    private String structuredOutputMode; // 结构化输出：JSON_OBJECT / TEXT
    private String structuredOutputClass;// 结构化输出的目标 Java 类名
    private List<SubAgentConfig> subAgents; // 多 Agent 协作的专家列表（Supervisor 模式）
}
```

> 💡 **v3.8+ 新字段说明**：
> - `maxIterations`：ReAct 循环的最大迭代次数（每次=推理+工具调用），防止 Agent 陷入死循环。详见 [B06-Agent 循环控制](B06-agent-loop-control.md)
> - `structuredOutputMode`：约束 LLM 返回合法 JSON，详见 [F05-结构化输出](F05-structured-output.md)
> - `subAgents`：多 Agent 协作时，每个专家的独立配置（name/description/sysPrompt/llmConfig/toolConfig）

### LLMConfig（大模型配置）

```java
@Data
public static class LLMConfig implements Serializable {
    private String modelType;       // dashscope/openai/anthropic/ollama，或预设 glm/kimi/doubao 等
    private String modelName;       // qwen-max/gpt-4/claude-3
    private String apiKey;          // API 密钥
    private Double temperature;     // 温度（0-1）
    private Integer maxTokens;      // 最大输出
    private String baseUrl;         // API 地址（兼容场景）
    private Boolean stream;         // 流式输出

    // —— v3.3+ 扩展参数（均可选，null 则不传给 LLM）——
    private Boolean enableThinking; // 思考模式（DeepSeek-R1 / Claude 扩展思考）
    private Double topP;            // 核采样
    private Double frequencyPenalty;// 频率惩罚（-2.0~2.0）
    private Double presencePenalty; // 存在惩罚（-2.0~2.0）
    private Long seed;              // 随机种子（可复现输出）
    private Integer topK;           // Top-K 采样
    private Integer thinkingBudget; // 思考 Token 预算（Anthropic/Gemini）
    private String reasoningEffort; // 推理强度（OpenAI o-series: low/medium/high）
    private List<FallbackProvider> fallbackProviders; // 备选 Provider 链（Failover）

    // —— Gemini Vertex AI 模式 ——
    private Boolean vertexAi;       // 启用后用 GCP 服务账号而非 apiKey
    private String projectId;       // GCP 项目 ID
    private String location;        // GCP 区域（如 us-central1）
}
```

> 💡 **FallbackProvider**：当主 Provider 调用失败时，按优先级依次尝试备选。详见 [B05-Provider Failover](B05-provider-failover.md)

---

## 从数据库到引擎的完整流程

```
1. 前端创建 Agent（填表单）
     ↓ CreateAgentDTO
2. AgentController 接收
     ↓ 转 Domain
3. AgentServiceImpl 存库
     ↓ AgentDO → lumina_agent 表（llm_config 存 JSON 字符串）
4. 执行 Agent 时
     ↓ 从 DB 读 AgentDO
5. AgentServiceImpl.buildConfig()
     ↓ 解析 llm_config JSON → AgentConfig.LLMConfig
6. DefaultAgentExecutionEngine.execute(config)
     ↓ 用 config 创建 ChatModel + Toolkit + Memory
7. ReActAgent.builder().build()
     ↓ 执行
```

---

## 关键：llm_config 存 JSON

数据库 `lumina_agent` 表里，`llm_config` 列存的是 JSON 字符串：

```sql
-- lumina_agent 表的 llm_config 列
{"modelType":"dashscope","modelName":"qwen-max","apiKey":"sk-xxx","temperature":0.7}
```

读取时反序列化：
```java
// AgentServiceImpl.buildConfig()
AgentConfig.LLMConfig llmConfig = objectMapper.readValue(
    agentDO.getLlmConfig(),
    AgentConfig.LLMConfig.class
);
```

---

## 小结

| 配置 | 存哪 | 格式 |
|------|------|------|
| agentName/agentType | lumina_agent 表列 | 普通字段 |
| llmConfig | lumina_agent.llm_config | JSON 字符串 |
| tools | lumina_agent.tools | 逗号分隔或 JSON |
| knowledgeBaseIds | lumina_agent_knowledge_base 关联表 | 多对多 |

> 🚀 [B05 — Provider Failover →](B05-provider-failover.md)

---

📝 **本篇撰写期间修正的代码**：无。
