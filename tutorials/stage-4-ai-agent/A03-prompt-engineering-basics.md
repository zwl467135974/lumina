# A03 — Prompt 工程入门

> **前置要求**：已完成 [A02-Token 与上下文](A02-token-context-window.md)
> **预计阅读**：20 分钟
> **难度**：⭐⭐☆☆☆

---

## 这节解决什么问题

LLM 是"读过全网书的高材生"——但你给他布置任务时，**怎么说**很重要。同样的问题，问法不同效果天差地别。

**Prompt 工程**就是"怎么给 AI 写任务说明书"。这节讲基础——System Prompt 是什么、Lumina 怎么管理 Prompt。

---

## Prompt 是什么？先建立直觉

### 类比：给实习生布置任务

你对实习生说"做个报表"——他可能做出来完全不是你要的。

但你说："用 Excel 做一份销售月报，包含本月每日销售额折线图、TOP10 产品柱状图，数据从数据库 `sales` 表取，格式参考上月的"——结果就靠谱多了。

**Prompt 就是这份"任务说明书"**——说得越清楚，AI 做得越好。

---

## System Prompt：AI 的"人设"

### 什么是 System Prompt

每次调 LLM，你可以设一个"系统级"的指令——定义 AI 的角色、行为规则、限制条件。这叫 **System Prompt**。

```
System Prompt（系统指令）: "你是一个专业的运维工程师，回答技术问题时要准确、简洁。"
User Message（用户消息）: "Nginx 怎么配置 gzip？"
AI 回答: （按运维工程师的人设来回答）
```

### 在 Lumina 里长啥样

```
文件：lumina-agent-core/src/main/resources/prompts/react.txt

You are a helpful AI assistant with access to various tools.

When given a task, think step by step:
1. Understand what the user is asking for
2. Consider which tools might be helpful
3. Use tools to gather information
4. Provide a clear and helpful answer

The tools available to you are provided dynamically by the system.
Think carefully before acting, and explain your reasoning.

User task:
{0}
```

**这段文字就是 ReAct Agent 的 System Prompt**——告诉 AI"你有工具可用，要先思考再行动"。

注意最后的 `{0}`——这是**占位符**，运行时会被实际的用户任务替换。

---

## Prompt 模板与占位符

### 占位符替换

```java
// 文件：lumina-agent-core/.../loader/PromptLoader.java 的 fillTemplate 方法
public String fillTemplate(String template, String task) {
    return template.replace("{0}", task)        // {0} → 用户任务
                   .replace("{task}", task);
}
```

```
模板: "请帮我完成任务: {0}"
任务: "写一个冒泡排序"
最终 Prompt: "请帮我完成任务: 写一个冒泡排序"
```

---

## Lumina 的 Prompt 管理

### 两种来源

1. **classpath 内置**：`prompts/*.txt`（默认，开箱即用）
2. **数据库管理**：通过 Prompt 管理页创建/版本管理/发布

### 数据库版（优先级更高）

```java
// Agent 执行时的 Prompt 加载顺序：
// 1. 先查数据库有没有"激活"的 Prompt（租户优先 + 全局回退）
// 2. 没有就用 classpath 内置的
```

### Prompt 版本管理

Lumina 支持Prompt 的版本管理：
- **创建新版本**：不改旧版，新建一个版本
- **发布**：激活某个版本
- **回滚**：切换到旧版本

> 📖 Prompt 高级技巧（CoT/Few-shot/防注入）见 [A04-Prompt 高级](A04-prompt-advanced.md)。Prompt 管理 API 见 [第二阶 09-实战](../stage-2-application/09-build-a-feature-backend.md)。

---

## Prompt 的基本要素

一个好的 Prompt 应该包含：

| 要素 | 说明 | 示例 |
|------|------|------|
| **角色** | 你是谁 | "你是运维工程师" |
| **任务** | 做什么 | "分析日志找出错误" |
| **规则** | 限制条件 | "只回复 JSON 格式" |
| **上下文** | 背景信息 | "这是今天的 Nginx 日志: ..." |

---

## 动手试试

1. **打开 `prompts/react.txt`**：看看 ReAct Agent 的 System Prompt 长啥样
2. **打开 `prompts/plan-execute-planner.txt`**：看看 Plan-Execute 的 Prompt 有什么不同
3. **在 Lumina 前端创建 Agent**：填 System Prompt，测试不同 Prompt 的效果差异

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| Prompt | 给 AI 的任务说明书 |
| System Prompt | 定义 AI 角色/行为的系统指令 |
| 占位符 `{0}` | 运行时替换为实际内容 |
| Prompt 管理 | classpath 内置 + 数据库版本管理（DB 优先） |
| Prompt 要素 | 角色 + 任务 + 规则 + 上下文 |

> 🚀 [A04 — Prompt 高级 →](A04-prompt-advanced.md)

---

📝 **本篇撰写期间修正的代码**：无。
