# B07 — 动态模型路由：简单问题用便宜模型

> **前置要求**：已完成 [B06 Agent 循环控制](B06-agent-loop-control.md)
> **预计阅读**：15 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

如果你的 Agent 只配了一个模型——要么贵、要么弱。

- 用户说"你好"，用 GPT-4 回一句"你好啊"——**一句话烧掉 $0.03**，杀鸡用牛刀。
- 用户说"帮我重构这段 500 行的并发代码"，用 GLM-4-Flash——**省钱了，但分析得一塌糊涂**。

**一个模型打天下，要么浪费钱，要么牺牲质量。** 能不能让简单问题走便宜模型、复杂问题才上强力模型？

这就是**动态模型路由**（Dynamic Model Routing）要解决的事——Lumina 3.8.0 引入。

---

## 先建立直觉：医院分诊台

你去医院看病，不会直接塞进手术室。

```
病人进门
   ↓
分诊台护士（瞄一眼，几秒钟）
   ↓
┌─────────────┬──────────────┐
│ 普通门诊     │ 专家门诊      │
│ 感冒、开药   │ 疑难杂症      │
│ 便宜、快     │ 贵、慢但专业  │
└─────────────┴──────────────┘
```

**分诊台**就是这里的关键——它本身不治病，只判断"这个病人该去哪"。护士花几秒钟看一眼，把感冒病人丢给便宜的普通门诊，把疑难杂症送去专家门诊。

模型路由干的事一模一样：

- **分诊台 = ComplexityModelRouter**（一次轻量 LLM 调用判断复杂度）
- **普通门诊 = simple-model**（glm-4-flash，便宜）
- **专家门诊 = complex-model**（glm-4 / GPT-4，贵但强）

---

## 核心接口：ModelRouter

```java
// 文件：lumina-agent-core/src/main/java/io/lumina/agent/service/ModelRouter.java
public interface ModelRouter {

    /**
     * 根据任务复杂度路由到合适的模型配置
     *
     * @param task   用户任务文本
     * @param config 当前 Agent 配置（含默认 LLM 配置）
     * @return 路由后的 LLM 配置；null 表示不路由（用默认配置）
     */
    AgentConfig.LLMConfig route(String task, AgentConfig config);
}
```

接口就一个方法，关键在 **返回 null 表示"不干预，用默认模型"**。这个设计让路由失败或未启用时可以无感降级——不会因为路由组件出问题导致整个 Agent 挂掉。

---

## 实现：ComplexityModelRouter

`ComplexityModelRouter` 是 Lumina 的默认实现。逻辑分两步：**先用 LLM 判断复杂度，再按复杂度选模型**。

### 第 1 步：用一次轻量 LLM 调用判断复杂度

```java
// 文件：lumina-modules/lumina-business-agent/.../service/impl/ComplexityModelRouter.java:39-49
private static final String COMPLEXITY_PROMPT = """
        判断以下用户请求的复杂度。只输出一个词：SIMPLE 或 COMPLEX。

        判断标准：
        - SIMPLE: 闲聊、简单事实问答、翻译短句、基础数学
        - COMPLEX: 代码分析、长文写作、多步推理、专业领域问题

        用户请求：%s

        只输出 SIMPLE 或 COMPLEX，不要输出其他内容。
        """;
```

这行 prompt 就是"分诊台护士的判断手册"——明确列出什么算 SIMPLE、什么算 COMPLEX，并**强制模型只输出一个词**（SIMPLE 或 COMPLEX）。这样返回结果不用解析，直接字符串比较就行，避免幻觉输出干扰。

### 第 2 步：按复杂度选模型

```java
// 文件：ComplexityModelRouter.java:64-90（核心路由逻辑）
String complexity = judgeComplexity(task);                // 一次 LLM 调用

String targetModel = "COMPLEX".equalsIgnoreCase(complexity)
        ? routingConfig.getComplexModel()                 // 复杂 → glm-4
        : routingConfig.getSimpleModel();                 // 简单 → glm-4-flash

// 构建路由后的 LLM 配置（继承原配置，只改模型名）
AgentConfig.LLMConfig routed = new AgentConfig.LLMConfig();
routed.setModelType(original.getModelType());
routed.setModelName(targetModel);                         // 只换这一个字段
routed.setApiKey(original.getApiKey());
routed.setBaseUrl(original.getBaseUrl());
routed.setTemperature(original.getTemperature());
routed.setMaxTokens(original.getMaxTokens());
```

**关键设计**：路由后的 `LLMConfig` **只改 modelName，其他字段（apiKey / baseUrl / temperature / maxTokens）全部继承原配置**。这保证 API Key、温度等用户自定义设置不会被路由覆盖。

### 容错：失败就当没路由

```java
// 文件：ComplexityModelRouter.java:92-95
} catch (Exception e) {
    log.warn("模型路由失败，使用默认模型: {}", e.getMessage());
    return null;    // 返回 null → 引擎用默认配置，不影响业务
}
```

任何异常（LLM 调用失败、网络抖动、解析错误）都兜底成 `return null`——**路由是增值功能，不能拖垮主流程**。

---

## 引擎层的接入点

路由发生在 Agent 执行的最开始，**在 `createReActAgent` 之前**：

```java
// 文件：lumina-agent-core/.../engine/impl/DefaultAgentExecutionEngine.java:214-220
// 动态模型路由（可选）：根据复杂度选择模型
if (modelRouter != null) {
    AgentConfig.LLMConfig routed = modelRouter.route(task, agentConfig);
    if (routed != null) {
        agentConfig.setLlmConfig(routed);    // 覆盖默认配置
    }
}
// ↓ 下面才进入 createReActAgent，用（可能已被路由过的）agentConfig
```

**为什么 `modelRouter` 可能是 null？** 看它的注入方式：

```java
// 文件：DefaultAgentExecutionEngine.java:131-132
@Autowired(required = false)   // ← 可选注入
private ModelRouter modelRouter;
```

是 `required = false` 的可选注入。配合实现类的条件装配：

```java
// 文件：ComplexityModelRouter.java:33
@ConditionalOnProperty(prefix = "lumina.agent.model-routing", name = "enabled", havingValue = "true")
public class ComplexityModelRouter implements ModelRouter { ... }
```

**只有配置 `enabled=true` 时，Spring 才会创建 `ComplexityModelRouter` 这个 Bean**，引擎才能注入到 `modelRouter` 字段。默认 `enabled=false` → Bean 不存在 → `modelRouter = null` → 引擎跳过路由逻辑。

这是一套**零侵入的可选功能开关**——不启用时连 Bean 都不存在，不占启动时间、不占内存。

---

## 配置（application.yml）

```yaml
# 文件：lumina-standalone/src/main/resources/application.yml:150-154
lumina:
  agent:
    # 动态模型路由（启用后根据复杂度自动切换便宜/强力模型）
    model-routing:
      enabled: ${MODEL_ROUTING_ENABLED:false}          # 默认关闭
      simple-model: ${MODEL_ROUTING_SIMPLE:glm-4-flash}   # 简单问题用 Flash
      complex-model: ${MODEL_ROUTING_COMPLEX:glm-4}        # 复杂问题用 GLM-4
```

**默认关闭**——因为路由本身有成本（见下节），需要根据业务实际流量决定是否开启。

---

## 成本分析：什么时候才划算

路由的代价是**每次请求多一次 LLM 调用**（判断复杂度），收益是**简单请求走便宜模型**。来算笔账：

| 项 | 数值 |
|----|------|
| 分类调用成本（约 50 Token） | ~$0.0001（用 Flash 判断） |
| 简单问题走 Flash | $0.001/次 |
| 简单问题走 GPT-4 | $0.03/次 |
| **单个简单请求省下** | **$0.029** |

### 盈亏平衡点

设你的请求中有 `p` 比例是简单请求：

- 路由成本：每次都付 `$0.0001`（不管简不简单）
- 路由收益：`p × $0.029`（简单请求才省）

盈亏平衡：`p × 0.029 = 0.0001` → **`p ≈ 0.34%`**

也就是说——**只要你的请求里有超过 0.34% 是简单的，开路由就省钱**。这个门槛极低，几乎任何面向真实用户的 Agent（总有"你好"、"谢谢"、闲聊）都该开。

> ⚠️ 上面的数字是示例值（基于公开的 GPT-4 定价）。实际单价要看你用的 Provider。但**结论不变**：只要简单请求占比 > 0.5%，开路由基本稳赚。

---

## 权衡：什么时候**不该**用

| 维度 | 说明 |
|------|------|
| ✅ Pro | 省钱（简单请求走便宜模型）；对的任务用对的模型，质量更好 |
| ⚠️ Con | 每次多一次 LLM 调用（**+200~500ms 延迟**）；分类可能误判（把复杂问题判成 SIMPLE） |
| ❌ 不该用 | **同质化流量**——全是简单请求（开路由白付分类费，直接配 Flash 即可）；或全是复杂请求（每次都判 COMPLEX，分类费白花，直接配 GLM-4） |

> **判断公式**：如果你的流量分布是"两极分化"（既有大量简单的、又有少量极复杂的），开路由最划算。如果流量集中在单一极，直接配对应模型就行。

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| ModelRouter 接口 | `route(task, config)` 返回新配置或 null（null = 不干预） |
| ComplexityModelRouter | 一次 LLM 调用判断 SIMPLE/COMPLEX，再选模型 |
| 接入点 | `DefaultAgentExecutionEngine` 第 215 行，`createReActAgent` 之前 |
| 配置开关 | `lumina.agent.model-routing.enabled`（默认 false，Bean 才创建） |
| 容错 | 任何异常都 `return null`，降级到默认模型 |
| 盈亏平衡 | 简单请求占比 > ~0.5% 就省钱 |

---

## 自测题

1. **为什么用一次单独的 LLM 调用来判断复杂度，而不是用规则（比如按字数、按关键词）？**
   <details><summary>答案</summary>规则太脆弱：用户问"1+1"是简单，问"分析黎曼猜想的证明思路"也是 10 个字——纯字数判断不出来。关键词列表永远列不全（"帮我看看这段代码"该算简单还是复杂？）。LLM 能理解语义，判断更准；而且分类调用用便宜模型 + 强制只输出一个词，成本极低（~50 Token）。</details>

2. **路由失败时（比如分类 LLM 调用超时），Agent 会怎样？**
   <details><summary>答案</summary>正常执行，用默认模型。`ComplexityModelRouter.route()` 用 try-catch 兜底，异常时返回 null；引擎收到 null 就跳过覆盖，agentConfig 保持原样。路由是"增值功能"，不能拖垮主流程。</details>

3. **配置 `enabled=false` 时，`ComplexityModelRouter` 这个 Bean 还存在吗？为什么？**
   <details><summary>答案</summary>不存在。类上有 `@ConditionalOnProperty(... havingValue="true")`，配置不匹配时 Spring 根本不会创建这个 Bean。引擎里用 `@Autowired(required = false)` 注入，所以 `modelRouter` 字段是 null，路由逻辑被 `if (modelRouter != null)` 跳过。零侵入。</details>

4. **什么场景下**不**应该启用模型路由？**
   <details><summary>答案</summary>流量同质化的场景：(a) 全是简单请求（如纯闲聊机器人）——直接配 Flash，开路由反而每次白付分类费；(b) 全是复杂请求（如代码审查 Agent）——每次都判 COMPLEX，分类费白花，直接配 GLM-4。路由最适合"两极分化"的混合流量。</details>

> 🚀 [B08 — 输出护栏 →](B08-output-guardrail.md)

---

📝 **本篇撰写期间修正的代码**：无（动态模型路由为 v3.8.0 新增功能）。
