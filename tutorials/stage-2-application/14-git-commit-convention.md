# 14 — Git 工作流与提交规范

> **前置要求**：已完成 [13-测试实践](13-testing-practice.md)
> **预计阅读**：10 分钟
> **难度**：⭐☆☆☆☆

---

## 这节解决什么问题

团队协作时，提交信息乱七八糟（"fix bug"、"update"、"111"）——没人知道每个提交做了什么。Lumina 用 **Conventional Commits** 规范解决。

---

## 提交格式

```
<type>(<scope>): <subject>

<body>
```

### type 类型

| type | 含义 | 示例 |
|------|------|------|
| `feat` | 新功能 | `feat(agent): 新增多模态执行` |
| `fix` | 修 bug | `fix(workflow): 修复 PAUSED 丢上下文` |
| `docs` | 文档 | `docs: 更新 README 版本号` |
| `refactor` | 重构 | `refactor(common): 统一构造器注入` |
| `test` | 测试 | `test(budget): 补充预算服务单元测试` |
| `chore` | 杂项 | `chore: 升级依赖版本` |
| `style` | 格式 | `style: 统一缩进` |
| `perf` | 性能 | `perf(agent): 优化 RAG 检索速度` |

### scope 范围（可选）

表示改了哪个模块：`agent` / `base` / `gateway` / `frontend` / `common` 等。

### subject 描述

- 中文描述
- 不超过 50 字
- 不加句号

---

## Lumina 真实提交示例

```bash
git log --oneline -5

# 实际输出：
725d116 fix: FlowableWorkflowEngine add @ConditionalOnBean(RepositoryService.class)
a716055 docs: update all documentation for v3.1 changes
500470e fix: R5 final audit 5 issues
474198a fix: final 4 low-priority issues
1c26cc7 fix(all): remaining 5 low-priority issues
```

---

## 分支策略

```
master          ← 稳定主线
  │
  ├── feature/xxx     ← 功能开发分支
  ├── fix/xxx         ← 修复分支
  └── hotfix/xxx      ← 紧急修复
```

- 新功能 → `feature/announcement-management`
- 修 bug → `fix/workflow-paused-context`
- 合并到 master 前 → 确保 `mvn compile` 通过

---

## 小结

格式：`type(scope): 中文描述`。类型选对，scope 标模块，描述说人话。

> 🚀 [15 — 技术选型 →](15-tech-selection-why.md)

---

📝 **本篇撰写期间修正的代码**：无。
