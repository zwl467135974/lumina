# F04 — 安全防护

> **前置要求**：已完成 [F03-OpenAI 兼容](F03-openai-compat.md)
> **预计阅读**：10 分钟
> **难度**：⭐⭐☆☆☆

---

## 这节解决什么问题

AI 系统有两个独特的安全风险：用户**注入恶意 Prompt**（Prompt Injection）、AI 输出**泄露隐私**（PII）。Lumina 在输入和输出两端都做了防护。

---

## 输入侧：Prompt 注入检测

```java
// 文件：.../security/PromptInjectionFilter.java
// 检测 11 种注入模式：
// - "ignore previous instructions"
// - "you are now..."
// - 角色覆盖 / 分隔符注入 / 编码绕过...
```

匹配到任何模式 → 直接拒绝（`throw BusinessException(ErrorCode.PROMPT_INJECTION_DETECTED)`）。

> 📖 详见 [A04-Prompt 高级](A04-prompt-advanced.md)的"防注入"章节。

---

## 输出侧：PII 脱敏

```java
// 文件：.../security/OutputSanitizer.java
// AI 回复里的敏感信息自动打码：
// 手机号: 138****8888
// 身份证: 110***********1234
// 邮箱: a***@xxx.com
// 银行卡: 6222****1234
```

即使 LLM"说漏嘴"泄露了隐私，也自动脱敏。

---

## 小结

| 防护 | 方向 | 作用 |
|------|------|------|
| PromptInjectionFilter | 输入 | 检测 11 种注入模式 |
| OutputSanitizer | 输出 | 手机/身份证/邮箱/银行卡打码 |

---

## 🎉 模块 F 完成

> 🚀 [G01 — 模型价格管理 →](G01-model-pricing.md)

---

📝 **本篇撰写期间修正的代码**：无。
