# H03 — A/B Testing

> **前置要求**：已完成 [H02-四种评分器](H02-four-scorers.md)
> **预计阅读**：15 分钟
> **难度**：⭐⭐☆☆☆

---

## 这节解决什么问题

你有两个版本的 Prompt（"详细版"和"简洁版"），或者两个模型（"贵的准确版"和"便宜的快速版"）。直觉上不知道哪个效果好——凭感觉选很容易被幸存者偏差误导。

**A/B 测试**是科学的做法：把真实流量按比例分给两个版本，让真实用户和真实场景告诉你答案。本节拆解 Lumina 的 A/B 测试系统：怎么按权重分流、怎么保证同一用户始终走同一版本（粘滞）、怎么把结果记录下来对比。

---

## 类比：可乐盲测 + 专属服务员

A/B 测试的核心思想来自经典的**可乐盲测**：让用户在不知道自己喝的是 A（经典款）还是 B（新配方）的情况下评价，统计哪个得分更高。这样能排除"先入为主的品牌偏好"对评价的干扰。

Lumina 的"盲测"有一个额外约束——**同一会话粘滞**。想象一家餐厅做菜式 A/B 测试：如果同一个客人每次来都换一道菜，他没法评价"这道菜的整体体验"（第一次吃 A、第二次吃 B，体验割裂）。所以餐厅给每个客人分配一个**专属服务员**，这个服务员固定端 A 或 B，保证客人的体验一致。Lumina 的粘滞缓存（`assignmentCache`）就是这个"专属服务员"。

---

## 一、A/B 测试的数据模型

一个**实验（Experiment）**挂在一个 Agent 上，包含多个**变体（Variant）**：

```
AbExperiment（实验）
├── agentId: 42                    挂在哪个 Agent 上
├── trafficPercent: 100            流量百分比（100=全部流量参与实验）
├── status: DRAFT/RUNNING/PAUSED/COMPLETED
└── variants:                      变体列表
    ├── Variant A (control)
    │   ├── weight: 70             权重 70（70% 流量）
    │   ├── llmConfig: {...}       覆盖 Agent 的 LLM 配置
    │   └── promptName: "react"    覆盖使用的 Prompt
    └── Variant B (treatment)
        ├── weight: 30             权重 30（30% 流量）
        ├── llmConfig: {...}
        └── promptName: "react-concise"
```

每次会话命中实验后，产生一条**曝光记录（Exposure）**，记录成功与否、延迟、token 消耗——这就是后续对比效果的数据来源。

---

## 二、分流算法：权重 + 粘滞

### 第一步：流量百分比判断

不是所有会话都参与实验。`assignVariant` 方法（`AbTestServiceImpl.java` 第 155-159 行）先做流量掷骰：

```java
int trafficRoll = new Random().nextInt(100) + 1;   // 1~100
if (trafficRoll > experiment.getTrafficPercent()) {
    return null;   // 不在实验流量内，走 Agent 默认配置
}
```

`trafficPercent = 30` 表示 30% 的会话参与实验，其余 70% 直接返回 null（走默认配置），相当于"这个实验对这 70% 用户不可见"。这是灰度发布的常用手段——先把小流量切到新版本试水。

### 第二步：粘滞查缓存

参与实验的会话，先查粘滞缓存（第 162-163 行）：

```java
String stickyKey = experiment.getId() + ":" + (conversationId != null ? conversationId : UUID.randomUUID().toString());
Long cachedVariantId = assignmentCache.get(stickyKey);
```

`assignmentCache` 是 `ConcurrentHashMap<String, Long>`，key 是 `实验ID:会话ID`。如果这个会话之前分配过变体，直接复用——保证同一用户在多轮对话中始终走同一版本。

### 第三步：权重随机选择

缓存未命中时，按权重随机分配（第 241-255 行的 `selectByWeight`）：

```java
private AbVariantDO selectByWeight(List<AbVariantDO> variants) {
    int totalWeight = variants.stream().mapToInt(v -> v.getWeight()).sum();  // 如 70+30=100
    int roll = new Random().nextInt(totalWeight);   // 0~99
    int cumulative = 0;
    for (AbVariantDO v : variants) {
        cumulative += v.getWeight();                // 累加权重
        if (roll < cumulative) {
            return v;                               // 命中
        }
    }
    return variants.get(variants.size() - 1);
}
```

举例：变体 A 权重 70、B 权重 30，`totalWeight=100`。`roll` 是 0~99 的随机数：
- `roll` 在 0~69 → 累加到 70 时 `roll < 70` 命中 A（70% 概率）
- `roll` 在 70~99 → 累加到 70 时 `roll >= 70` 不命中，继续累加到 100 时 `roll < 100` 命中 B（30% 概率）

分配后写入缓存（第 185 行 `assignmentCache.put(stickyKey, selected.getId())`），后续该会话都走这个变体。

---

## 三、变体注入：覆盖 Agent 的 LLM 配置和 Prompt

分配到变体后，怎么让它生效？答案在 `AgentServiceImpl.buildExecutionConfig`（第 814-843 行）。这是 A/B 测试与 Agent 执行的**集成点**：

```java
if (abTestService != null) {
    VariantContext variant = abTestService.assignVariant(agent.getAgentId(), conversationId);
    if (variant != null) {
        abVariantHolder.set(variant);              // ThreadLocal 暂存，执行后用于记录曝光
        if (variant.llmConfig() != null) {
            // 变体 LLM 配置覆盖 Agent 默认配置（变体未指定的字段保留 Agent 原配置）
            AgentConfig.LLMConfig merged = variant.llmConfig();
            AgentConfig.LLMConfig base = config.getLlmConfig();
            if (base != null && merged.getModelType() == null) merged.setModelType(base.getModelType());
            // ... 其他字段同理 ...
            config.setLlmConfig(merged);
        }
        if (variant.promptName() != null) {
            // 变体指定了 Prompt，加载该 Prompt 覆盖默认
            PromptDO variantPrompt = promptService.getActive(variant.promptName().toLowerCase());
            if (variantPrompt != null) {
                config.setPromptTemplate(variantPrompt.getContent());
            }
        }
    }
}
```

关键细节：
- **覆盖而非替换**：变体的 `llmConfig` 只覆盖它指定的字段（如只改了 `modelName`），未指定的字段（如 `apiKey`）保留 Agent 原配置。这样新建变体时只需写差异部分。
- **ThreadLocal 暂存**：`abVariantHolder` 把变体上下文存在当前线程，执行完成后 `recordAbExposure`（第 861-879 行）取出来记录曝光结果，然后 `remove` 清理。

---

## 四、曝光记录与效果对比

每次会话执行完，`recordExposure` 写一条曝光记录（第 206-225 行）：

```java
public void recordExposure(Long experimentId, Long variantId, String variantName,
                            String conversationId, boolean success, long latencyMs,
                            Integer tokens, String errorMsg) {
    AbExposureDO exposure = new AbExposureDO();
    exposure.setExperimentId(experimentId);
    exposure.setVariantId(variantId);
    exposure.setConversationId(conversationId);
    exposure.setUserId(BaseContext.getUserId());
    exposure.setSuccess(success ? 1 : 0);
    exposure.setLatencyMs(latencyMs);
    exposure.setTokens(tokens);
    exposure.setErrorMsg(errorMsg != null ? errorMsg.substring(0, Math.min(500, errorMsg.length())) : null);
    exposureMapper.insert(exposure);
}
```

注意：曝光记录写入失败**只 warn 不抛异常**（try-catch 包裹），保证不影响 Agent 主流程——A/B 测试是"附加观测"，绝不能因为它让用户请求失败。

### 效果报告

`buildReport`（第 278-314 行）按变体聚合曝光数据，计算每个变体的：
- **曝光数**（`exposures`）
- **成功率**（`successRate` = 成功次数 / 曝光数）
- **平均延迟**（`avgLatencyMs`）
- **平均 token 消耗**（`avgTokens`）

对比两个变体的这四个指标，就能判断哪个版本更好。比如"简洁版"成功率持平但 token 少 40%——那它就是更优选择（成本更低）。

---

## 五、真实场景：详细 Prompt vs 简洁 Prompt

假设你有一个客服 Agent，想测试两种 Prompt 风格：

1. **创建实验**：`POST /agents/{id}/ab-experiments`，两个变体——control（`promptName: "customer-service"`，权重 70）、treatment（`promptName: "customer-service-concise"`，权重 30）。
2. **启动实验**：`POST /ab-experiments/{id}/start`，状态变 `RUNNING`。
3. **运行期间**：每个会话自动分流——70% 走详细版，30% 走简洁版。同一用户的多轮对话始终走同一版本（粘滞）。
4. **收集数据**：每次会话自动记录曝光（成功/失败、延迟、token）。
5. **查看报告**：`GET /ab-experiments/{id}` 返回报告。如果简洁版成功率 92%（vs 详细版 90%）、token 少 35%——结论：简洁版更优，可以全量切换。
6. **完成实验**：`POST /ab-experiments/{id}/complete`，把简洁版 Prompt 设为 Agent 默认。

整个过程**不需要改一行代码**——创建实验、分配变体、注入配置、记录曝光全部由框架自动完成。

---

## 小结

| 概念 | 一句话记忆 | 代码位置 |
|------|-----------|---------|
| 流量百分比 | `trafficPercent=30` → 30% 会话参与实验 | `assignVariant` 第 155-159 行 |
| 权重分流 | 按 `weight` 累加掷骰，70:30 分配 | `selectByWeight` 第 241-255 行 |
| 同会话粘滞 | `ConcurrentHashMap<实验ID:会话ID, 变体ID>` | `assignmentCache` 第 49 行 |
| 变体注入 | 覆盖 Agent 的 LLM 配置和 Prompt | `buildExecutionConfig` 第 814-843 行 |
| 曝光记录 | 写 success/latency/tokens，失败不阻断主流程 | `recordExposure` 第 206-225 行 |
| 效果报告 | 按变体聚合成功率/延迟/token | `buildReport` 第 278-314 行 |

---

## 自测

<details>
<summary><b>1. 为什么要做"同会话粘滞"？不粘滞会怎样？</b></summary>

不粘滞的话，同一用户的多轮对话可能前一轮走变体 A、后一轮走变体 B。问题：(1) **体验不一致**——用户感觉 Agent"前后性格分裂"，尤其是 Prompt 风格差异大时（如详细 vs 简洁），用户体验割裂；(2) **数据污染**——曝光记录混在一起，无法区分某个结果到底是 A 还是 B 产生的，对比失去意义。粘滞保证同一会话始终走同一变体，既保证用户体验一致，又保证数据可归因。实现上用 `ConcurrentHashMap` 缓存 `实验ID:会话ID → 变体ID`，进程内有效（重启后重新分配，可接受）。
</details>

<details>
<summary><b>2. 变体的 <code>llmConfig</code> 覆盖 Agent 原配置时，为什么要"合并"而不是"替换"？</b></summary>

因为变体的 `llmConfig` 可能只指定了**差异字段**（比如只想换 `modelName` 从 `gpt-4` 到 `gpt-3.5-turbo`，但不想重新填 `apiKey`、`temperature` 等公共配置）。如果直接替换，变体没写的字段就会变成 null，导致 LLM 调用缺 apiKey 而失败。合并逻辑是：变体有的字段覆盖，变体没有的字段（null）保留 Agent 原配置。这样配置变体时只需写差异部分，降低出错概率。
</details>

<details>
<summary><b>3. <code>recordExposure</code> 写入失败时为什么不抛异常？这会不会导致数据不准？</b></summary>

不抛异常是因为 A/B 测试是**附加观测能力**，绝不能因为观测失败而影响用户的主请求（Agent 执行）。如果 `recordExposure` 抛异常，会导致用户明明 Agent 回答成功了，却因为记录曝光失败而收到 500 错误——这是不可接受的。代价是：确实可能出现"某次曝光没记录到"的数据缺失，但由于曝光量大（统计意义），少量缺失不影响整体对比结论。这是典型的"可用性优先于精确性"的权衡。
</details>

<details>
<summary><b>4. <code>trafficPercent</code> 和变体的 <code>weight</code> 是两个不同维度的百分比，它们怎么协作？</b></summary>

`trafficPercent` 控制**多少流量参与实验**（实验的"入口闸门"），`weight` 控制**参与实验的流量在变体之间怎么分**（内部的分配比例）。举例：`trafficPercent=50`、两个变体 `weight` 各 50——结果是：50% 流量不参与实验（走默认配置），剩余 50% 流量参与实验，其中一半走 A、一半走 B。即全局来看 25% 走 A、25% 走 B、50% 走默认。`trafficPercent` 用于灰度（先小流量试水），`weight` 用于变体间配比。代码上 `trafficPercent` 先判断（第 155-159 行），通过后再按 `weight` 分配（第 184 行 `selectByWeight`）。
</details>

---

> 🚀 [H04 — 评估回归 →](H04-evaluation-regression.md)

---

📝 **本篇撰写期间修正的代码**：无。
