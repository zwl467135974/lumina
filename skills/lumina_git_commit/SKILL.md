---
name: lumina_git_commit
description: Use this skill when generating git commit messages for staged changes. This skill enforces consistent commit message formatting, categorization of changes, and best practices for commit documentation.
---

# Lumina Git Commit 信息生成规范

## 功能概述

本技能包用于为 Lumina 框架项目的 Git 暂存区变更生成规范化的提交信息，确保提交历史清晰、可追溯、便于维护。

## Commit 信息格式规范

### 标准格式

```
<type>(<scope>): <subject>

<body>

<footer>
```

### 类型 (Type)

| 类型 | 说明 | 使用场景 | 示例 |
|------|------|---------|------|
| **feat** | 新功能 | 添加新功能、新特性 | `feat: 新增用户管理功能` |
| **fix** | 修复 | 修复 bug | `fix: 修复登录状态过期问题` |
| **docs** | 文档 | 仅文档变更 | `docs: 更新 API 文档` |
| **style** | 格式 | 代码格式调整，不影响逻辑 | `style: 格式化代码` |
| **refactor** | 重构 | 代码重构，不改变功能 | `refactor: 重构用户服务层` |
| **perf** | 性能 | 性能优化 | `perf: 优化查询性能` |
| **test** | 测试 | 添加或修改测试 | `test: 添加用户服务单元测试` |
| **chore** | 构建/工具 | 构建流程、工具链变更 | `chore: 更新依赖版本` |
| **build** | 构建 | 构建系统变更 | `build: 配置 Maven 打包` |

### 作用域 (Scope)

可选字段，用于标识变更影响的范围：

- **模块名**: `agent-core`, `business-base`, `gateway`, `framework`
- **功能域**: `user`, `role`, `permission`, `tenant`
- **技术域**: `api`, `service`, `config`, `security`

示例：
- `feat(agent-core): 新增工具适配器`
- `fix(business-base): 修复租户拦截器问题`

### 主题 (Subject)

- **必填**: 简短描述变更内容
- **格式**: 使用中文，首字母小写，结尾不加句号
- **长度**: 不超过 50 个字符
- **风格**: 使用祈使语气，如"新增"、"修复"、"优化"

### 正文 (Body)

可选，详细描述变更：

- **为什么**: 说明变更的原因和背景
- **怎么做**: 简要说明实现方式
- **影响**: 说明对系统的影响

格式要求：
- 每行不超过 72 个字符
- 使用空行分隔段落
- 使用列表展示多个变更点

### 页脚 (Footer)

可选，用于：

- **关联 Issue**: `Closes #123`, `Fixes #456`
- **破坏性变更**: `BREAKING CHANGE: API 接口变更`
- **影响模块**: `Affects: agent-core, business-base`

## Commit 信息结构模板

### 单功能提交

```
feat(scope): 简短描述

详细说明变更内容，可以包含：
- 变更点1
- 变更点2
- 变更点3
```

### 多模块提交

```
feat: 主要功能描述

## 主要变更

### 模块1
- 变更点1
- 变更点2

### 模块2
- 变更点1
- 变更点2

## 统计数据
- X 个文件变更
- 新增 Y 行代码
- 删除 Z 行代码
```

### 修复提交

```
fix(scope): 修复问题描述

修复的问题：
- 问题1描述
- 问题2描述

解决方案：
- 解决方式1
- 解决方式2

Closes #issue-number
```

## 分析暂存区变更的步骤

### 1. 获取变更统计

```bash
git status                    # 查看暂存文件列表
git diff --cached --stat      # 查看变更统计
git diff --cached --shortstat # 查看简短统计
```

### 2. 分析变更类型

根据暂存文件判断变更类型：

- **新增文件** (`new file`): 通常是 `feat` 或 `docs`
- **修改文件** (`modified`): 可能是 `fix`, `refactor`, `perf`, `feat`
- **删除文件** (`deleted`): 可能是 `refactor` 或 `chore`

### 3. 识别变更模块

根据文件路径识别影响模块：
- `lumina-agent-core/`: Agent 核心模块
- `lumina-business-base/`: 业务基础模块
- `lumina-gateway/`: 网关模块
- `lumina-framework/`: 框架模块
- `lumina-common/`: 公共模块
- `docs/`: 文档

### 4. 提取关键信息

从变更文件中提取：
- **新增的功能**: Controller, Service, DTO, VO 等
- **修复的问题**: 错误处理、异常修复
- **优化的内容**: 性能、代码结构
- **配置变更**: 配置文件、依赖更新

### 5. 组织 Commit 信息

按照标准格式组织信息，包括：
- 确定 Type 和 Scope
- 编写简洁的 Subject
- 列出主要变更点（Body）
- 添加统计数据（可选）

## 常见场景示例

### 场景1: 新增业务功能

```
feat(business-base): 新增用户管理CRUD功能

## 主要变更

### 用户管理
- 新增 UserController 用户控制器
- 新增 UserService 用户服务层
- 新增用户相关 DTO/VO 类
  - CreateUserDTO
  - UpdateUserDTO
  - UserQueryDTO
  - UserVO

### 基础设施
- 新增 BaseFeignClient 基础 Feign 客户端
- 优化 WebMvcConfig 配置

## 统计数据
- 15 个文件变更
- 新增 1200 行代码
```

### 场景2: 修复 Bug

```
fix(agent-core): 修复内存管理器泄漏问题

修复的问题：
- 修复 MemoryManager 中未释放的资源
- 修复线程池未正确关闭的问题

解决方案：
- 添加 try-finally 确保资源释放
- 实现优雅关闭机制

Closes #123
```

### 场景3: 重构代码

```
refactor(business-base): 重构租户拦截器实现

## 重构内容
- 提取 TenantLineHandler 接口
- 重构 TenantLineInterceptor 逻辑
- 优化 SQL 解析性能

## 影响范围
- 所有使用租户功能的模块

## 统计数据
- 3 个文件变更
- 新增 150 行，删除 200 行
```

### 场景4: 文档更新

```
docs: 新增项目状态分析报告

## 新增文档
- PROJECT_STATUS_ANALYSIS.md: 项目完成度分析
- P0_P1_PROGRESS.md: P0/P1 任务进度跟踪

## 文档内容
- 模块完成度评估
- 已完成功能清单
- 待完善功能说明
```

### 场景5: 多模块混合变更

```
feat: 完成业务基础模块并增强Agent核心能力

## 主要变更

### 📚 文档
- 新增项目状态分析报告 (PROJECT_STATUS_ANALYSIS.md)
- 新增 P0/P1 任务进度跟踪文档 (P0_P1_PROGRESS.md)

### 🔧 Agent Core 增强
- 新增 AgentScopeToolAdapter 工具适配器
- 新增 ToolDefinitionToAgentToolAdapter 工具定义适配器
- 优化 DefaultAgentExecutionEngine 执行引擎
- 优化 MemoryManager 内存管理

### 🏢 业务基础模块 (lumina-business-base)
#### 权限管理
- 新增 PermissionController、PermissionService 及相关 DTO/VO
- 新增 @RequirePermission 注解

#### 角色管理
- 新增 RoleController、RoleService 及相关 DTO/VO
- 新增 @RequireRole 注解
- 支持角色权限分配功能

#### 租户管理
- 新增 TenantController、TenantService 及相关 DTO/VO
- 新增 TenantLineHandlerImpl 租户行处理器
- 新增 MybatisPlusTenantConfig 租户配置

#### 用户管理
- 新增 UserController、UserService 及相关 DTO/VO
- 支持用户角色分配、密码重置等功能

#### 基础设施
- 新增 PermissionCheckInterceptor 权限检查拦截器
- 新增 BaseFeignClient 和 BaseFeignClientFallback
- 新增 BaseToolProvider 基础工具提供者
- 优化 TenantLineInterceptor 租户行拦截器
- 优化 WebMvcConfig 配置

## 统计数据
- 46 个文件变更
- 新增 4921 行代码
- 删除 30 行代码
```

## 最佳实践

### ✅ 推荐做法

1. **一次提交一个功能**: 每个 commit 应该是一个完整的功能单元
2. **使用清晰的类型**: 准确使用 type，便于过滤和查找
3. **添加必要的作用域**: 明确标识影响范围
4. **详细描述复杂变更**: 对于大型变更，使用详细的 body
5. **关联 Issue**: 使用 footer 关联相关 Issue
6. **使用中文**: 符合项目文档规范，使用中文描述

### ❌ 避免做法

1. **避免过长的 subject**: 超过 50 字符
2. **避免模糊的描述**: 如"更新"、"修复"、"优化"等过于笼统
3. **避免混合不相关的变更**: 一个 commit 应该是一个逻辑单元
4. **避免使用过去式**: 使用"新增"而非"新增了"
5. **避免省略类型**: 必须有明确的 type

## 使用场景

- 生成 Git 暂存区变更的 commit 信息
- 审查 commit 信息是否符合规范
- 指导团队成员编写规范的 commit 信息
- 自动化生成 commit 信息的工具参考

## 检查清单

生成 commit 信息前，确认：

- [ ] 已分析暂存区的所有变更
- [ ] 确定了正确的 type（feat/fix/docs/etc.）
- [ ] 标识了合适的 scope（如果适用）
- [ ] Subject 简洁清晰，不超过 50 字符
- [ ] Body 详细描述了主要变更（如需要）
- [ ] 列出了关键的新增/修改文件或功能
- [ ] 包含了变更统计信息（大型变更）
- [ ] 关联了相关 Issue（如有）
- [ ] 使用了中文描述
- [ ] 遵循了格式规范

## 自动化工具

可以使用以下命令快速分析暂存区：

```bash
# 查看暂存区文件列表
git status --short

# 查看变更统计
git diff --cached --stat

# 查看简短统计
git diff --cached --shortstat

# 查看具体变更内容（用于分析）
git diff --cached --name-status
```

