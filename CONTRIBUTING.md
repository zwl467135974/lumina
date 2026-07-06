# 贡献指南

感谢你对 Lumina 项目的关注！本文档说明如何参与贡献。

## 开发环境准备

- JDK 21+
- Maven 3.9+
- MySQL 8.0+
- Redis 7+
- Node.js 20+ / pnpm
- IDE: IntelliJ IDEA（推荐）

## 开发流程

1. **Fork & Clone**
   ```bash
   git clone https://github.com/your-username/lumina.git
   cd lumina
   ```

2. **创建分支**
   ```bash
   git checkout -b feat/your-feature
   ```

3. **编码**
   - 遵循 `skills/lumina_code_style/` 中的命名和代码风格规范
   - 遵循 `skills/lumina_architecture/` 中的分层架构规范
   - 新功能必须包含单元测试

4. **验证**
   ```bash
   # 后端
   mvn verify -B

   # 前端
   cd lumina-frontend && pnpm test && pnpm build
   ```

5. **提交**
   - 遵循 Commit 规范：`<type>(<scope>): <subject>`
   - 类型：feat / fix / docs / refactor / test / chore
   - 使用中文描述

6. **Pull Request**
   - 填写 PR 模板，说明变更内容和验证方式
   - 确保 CI 全部通过

## 代码规范

| 规范 | 参考文件 |
|------|----------|
| 代码风格 | `skills/lumina_code_style/SKILL.md` |
| 分层架构 | `skills/lumina_architecture/SKILL.md` |
| MyBatis-Plus | `skills/lumina_mybatis_plus/SKILL.md` |
| API 设计 | `skills/lumina_api_design/SKILL.md` |
| 测试规范 | `skills/lumina_testing/SKILL.md` |
| Git Commit | `skills/lumina_git_commit/SKILL.md` |

## 项目结构

```
lumina-common          公共工具类
lumina-framework       框架配置（审计/存储/异常/异步）
lumina-agent-core      Agent 核心引擎
lumina-gateway         API 网关
lumina-modules/
  lumina-business-base   用户/角色/权限/租户
  lumina-business-agent  Agent/会话/知识库/工作流/Prompt
lumina-frontend       Vue 3 前端
```

## 反馈问题

- Bug 报告：使用 GitHub Issue Bug 模板
- 功能建议：使用 GitHub Issue Feature Request 模板
- 安全漏洞：请私发邮件，不要公开 Issue

## License

贡献的代码将在 [Apache License 2.0](LICENSE) 下发布。
