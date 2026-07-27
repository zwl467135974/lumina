# F06 — 输出护栏：拦截有害内容

> **前置要求**：已完成 [F04 安全防护](F04-security-defense.md)
> **预计阅读**：15 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

[F04](F04-security-defense.md) 的 OutputSanitizer 解决了**隐私脱敏**——把手机号 `13800138000` 变成 `138****8000`。但有些输出问题**不是脱敏能解决的**：

1. **凭证泄露**：Agent 回复里直接吐出 `password: admin123` 或 `api_key=sk-xxx`——脱敏只能打码，不能拒绝
2. **Token 炸弹**：Agent 失控输出 10 万字符——脱敏后依然 10 万字符
3. **死循环**：Agent 卡住，连续重复同一句话 50 次——脱敏不改内容

这些问题需要的是**拦截 / 重写**能力，而不是打码。Lumina v3.8.0 新增 **OutputGuardrail（输出护栏）** 来做这件事。

---

## 先建立直觉：机场海关

把 Lumina 的两层输出防护想成机场的两种检查：

- **OutputSanitizer（[F04](F04-security-defense.md)）** = **海关申报台**：你带出去的东西可以带，但要把敏感部分遮起来（手机号打码）。**修改**内容，不拦你。
- **OutputGuardrail（本节）** = **海关稽查员**：发现你带了**绝对违禁品**（密码、密钥），直接**没收**（拦截）；发现你行李**超重**（输出太长），让你**掏出一部分**（截断重写）。可以**拒绝你出境**。

两者协同：先过稽查员（Guardrail 检查能不能放行 / 要不要改），再过申报台（Sanitizer 打码），最后交付用户。

---

## 区别：Guardrail vs Sanitizer

| 维度 | OutputSanitizer（F04） | OutputGuardrail（F06） |
|------|----------------------|----------------------|
| 职责 | PII 脱敏 | 安全检查 |
| 能力 | **修改**内容（打码） | **拦截** / **重写** |
| 决策 | 无条件改 | 可拒绝返回 |
| 典型场景 | 手机号 `138****8000` | 命中 `password` 直接拒绝 |
| 执行顺序 | 后（先过 Guardrail 再打码） | 先 |

一句话：**Sanitizer 是"化妆师"，Guardrail 是"保安"。**

---

## 三项检查（DefaultOutputGuardrail）

```java
// 文件：lumina-business-agent/.../security/DefaultOutputGuardrail.java
// 三项检查，按顺序短路执行

public GuardrailResult check(String output, Long agentId) {
    // 1. 敏感关键词检测 → 命中即拦截（block）
    //    配置 lumina.agent.guardrail.blocked-keywords: ["password", "secret", "api_key"]
    //    输出 lowercase 后做 contains 匹配

    // 2. 输出长度限制 → 超长则截断（rewrite）
    //    默认 max-output-length=10000 字符
    //    截断后追加：[输出被护栏截断：超过最大长度 10000 字符]

    // 3. 重复内容检测 → 严重重复则拦截（block）
    //    连续 20 行完全相同，或去重后行数 < 原始的 10%
    //    说明 Agent 陷入了死循环
}
```

### 检查 1：敏感关键词拦截

```java
String lowerOutput = output.toLowerCase();
for (String keyword : config.getBlockedKeywords()) {
    if (lowerOutput.contains(keyword.toLowerCase())) {
        return GuardrailResult.block("输出包含敏感内容，已被护栏拦截");
    }
}
```

默认关键词：`password`、`secret`、`api_key`。命中任意一个 → **直接拦截，抛异常**，不返回给用户。

### 检查 2：长度截断

```java
int maxLen = config.getMaxOutputLength();  // 默认 10000
if (maxLen > 0 && output.length() > maxLen) {
    String truncated = output.substring(0, maxLen)
        + "\n\n[输出被护栏截断：超过最大长度 " + maxLen + " 字符]";
    return GuardrailResult.rewrite(truncated, "输出超长，已截断");
}
```

**注意**：超长不拦截，而是**重写**——截断到 10000 字符并加说明。因为长输出不一定是攻击，可能是 Agent 啰嗦，截断后用户还能看到前半部分。

### 检查 3：重复检测（死循环症状）

```java
// 连续 20 行完全相同 → 拦截
// 或去重后行数 < 原始的 10% → 拦截
if (detectRepetition(output)) {
    return GuardrailResult.block("输出包含严重重复内容，Agent 可能陷入循环");
}
```

这是**死循环 Agent** 的典型症状：模型卡在某段文本里反复输出同一行。这种输出对用户毫无价值，直接拦截比返回 500 行重复字符友好得多。

---

## GuardrailResult 的三种结果

```java
// 文件：lumina-business-agent/.../security/OutputGuardrail.java

record GuardrailResult(boolean blocked, String rewritten, String reason) {

    public static GuardrailResult pass()                    // 无问题，原样返回
    public static GuardrailResult block(String reason)      // 拦截，抛异常
    public static GuardrailResult rewrite(String rewritten, // 重写，替代原输出
                                         String reason)
}
```

| 工厂方法 | blocked | rewritten | 效果 |
|---------|---------|-----------|------|
| `pass()` | false | null | 原输出放行 |
| `block(reason)` | true | null | 抛 BusinessException，不返回 |
| `rewrite(s, reason)` | false | s | 用 s 替代原输出 |

> **设计要点**：`blocked` 和 `rewritten` 互斥——拦截了就不重写，重写就不拦截。这让调用方（AgentServiceImpl）的逻辑很清晰：先判 `blocked` 抛异常，再看 `rewritten` 要不要替换。

---

## 在 AgentServiceImpl 中的调用位置

```java
// 文件：lumina-business-agent/.../service/impl/AgentServiceImpl.java（约 330 行）

// ① 输出护栏检查（拦截/重写）
String outputToSanitize = result.getResult();
if (outputGuardrail != null) {
    GuardrailResult gr = outputGuardrail.check(outputToSanitize, agentId);
    if (gr.blocked()) {
        throw new BusinessException(ErrorCode.AGENT_EXECUTE_FAILED,
                "输出被护栏拦截: " + gr.reason());
    }
    if (gr.rewritten() != null) {
        outputToSanitize = gr.rewritten();   // 用重写后的内容
    }
}

// ② 输出脱敏（PII 打码）
String sanitizedResult = outputSanitizer.sanitize(outputToSanitize);
result.setResult(sanitizedResult);
```

**执行顺序**：`Guardrail.check()` → `Sanitizer.sanitize()`。先决定"能不能给 / 要不要改"，再把能给的内容打码。这保证 Sanitizer 永远不会处理被拦截的内容，也不会把"重写说明"误打码。

---

## 配置

```yaml
lumina:
  agent:
    guardrail:
      enabled: false                    # 默认关闭
      max-output-length: 10000          # 最大字符数（0=不限制）
      blocked-keywords:                 # 命中即拦截
        - password
        - secret
        - api_key
```

```java
// 文件：lumina-agent-core/.../config/LuminaAgentProperties.java

@Data
public static class GuardrailConfig {
    private boolean enabled = false;                        // 默认关
    private int maxOutputLength = 10000;                    // 默认 1 万字符
    private java.util.List<String> blockedKeywords;         // 默认空
}
```

实现类用条件装配，只有 `enabled=true` 才生效：

```java
// DefaultOutputGuardrail 类上
@ConditionalOnProperty(prefix = "lumina.agent.guardrail",
                       name = "enabled", havingValue = "true")
public class DefaultOutputGuardrail implements OutputGuardrail { ... }
```

`AgentServiceImpl` 里 `outputGuardrail` 是可选注入（没开护栏时为 `null`），调用前判空——所以默认关也不会出错。

---

## 三个典型场景

### 场景 1：防止凭证泄露

Agent 在回答"如何配置数据库"时，从记忆/知识库里翻出了真实的生产凭证：

```
Agent 原始输出: "你可以在 application.yml 里设置 password: P@ssw0rd2024 ..."
护栏检查:       命中 "password" → block
用户看到:       错误提示 "输出被护栏拦截: 输出包含敏感内容"
```

### 场景 2：阻止失控 Agent（Token 炸弹）

Agent 因为 Prompt 设计问题，开始无限罗列：

```
Agent 原始输出: 98000 字符的报告
护栏检查:       长度 > 10000 → rewrite（截断到 10000 + 说明）
用户看到:       前 10000 字符 + "[输出被护栏截断：超过最大长度 10000 字符]"
```

### 场景 3：检测死循环

Agent 在 ReAct 循环里卡住，最终输出全是同一句：

```
Agent 原始输出:
  我正在思考...
  我正在思考...
  （重复 30 行）
护栏检查:       连续 20 行相同 → block
用户看到:       错误提示 "输出包含严重重复内容，Agent 可能陷入循环"
```

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| OutputGuardrail | 输出侧的安全检查员，能拦截也能重写 |
| vs OutputSanitizer | Sanitizer 修改（打码），Guardrail 检查（拦截/重写） |
| GuardrailResult | `pass` / `block` / `rewrite` 三态，互斥 |
| 执行顺序 | Guardrail 先 → Sanitizer 后 |
| 三项检查 | 关键词拦截 / 长度截断 / 重复检测 |
| 默认开关 | `enabled=false`（误拦正常输出代价大，可选开启） |

### 自测题

1. OutputGuardrail 和 OutputSanitizer 的核心区别是什么？
   <details><summary>答案</summary><b>能力不同</b>。Sanitizer 只能<b>修改</b>内容（PII 打码，无条件执行）；Guardrail 能<b>拦截</b>（拒绝返回、抛异常）和<b>重写</b>（替换内容），是有决策能力的检查。两者协同：Guardrail 先检查能否放行/是否要改，再由 Sanitizer 对放行的内容打码。</details>

2. 什么情况下 Guardrail 会选择 REWRITE 而不是 BLOCK？
   <details><summary>答案</summary>当输出"有问题但仍有价值"时重写。典型是<b>长度超限</b>：输出只是太啰嗦，前半部分对用户有用，所以截断到 max-output-length 并加说明，而不是整个丢弃。相比之下，命中敏感关键词或死循环的输出对用户毫无价值，直接 block。</details>

3. 重复检测（连续 20 行相同）为什么有用？
   <details><summary>答案</summary>这是 <b>Agent 陷入死循环</b>的典型症状——模型卡在某段文本里反复输出同一行。这种输出对用户毫无价值（甚至误导），而且会浪费大量 Token。直接拦截比返回 50 行重复字符更友好，也方便运维发现问题 Agent。</details>

4. 为什么 OutputGuardrail 默认是关闭的（enabled=false）？
   <details><summary>答案</summary>因为护栏有<b>误拦风险</b>。比如关键词 "secret" 可能误伤正常业务（"这是 secret sauce 配方"）；长度限制可能截断合理的长报告；重复检测可能误判诗歌/列表。对大多数应用，OutputSanitizer 的无侵入打码已经够用。护栏是给<b>高风险场景</b>（含凭证的内部工具、对成本敏感的部署）的可选增强，由用户按需开启。</details>

---

> 🚀 返回 [AI 专项导读](README.md)

---

📝 **本篇撰写期间修正的代码**：无（输出护栏为 v3.8.0 已有能力，本节仅做解读）。
