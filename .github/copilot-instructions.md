<!-- AI Rules Sync: This file mirrors .cursorrules content. Keep .cursorrules / CLAUDE.md / AGENTS.md / .github/copilot-instructions.md in sync.
# Lumina 项目开发规则

本文件定义了 Lumina 框架项目的开发规范和 AI 助手应遵循的规则。

## 项目技能包 (Skills)

本项目包含多个开发技能包，位于 `skills/` 目录下。AI 助手应根据上下文自动识别并使用相关技能包：

### 1. lumina_code_style
- **用途**: 代码风格和命名规范
- **路径**: `skills/lumina_code_style/SKILL.md`
- **触发条件**: 编写 Java 代码、创建新类、代码审查时

### 2. lumina_architecture
- **用途**: 简化分层架构规范
- **路径**: `skills/lumina_architecture/SKILL.md`
- **触发条件**: 设计模块结构、创建新模块、组织代码层次时

### 3. lumina_mybatis_plus
- **用途**: MyBatis-Plus 使用规范
- **路径**: `skills/lumina_mybatis_plus/SKILL.md`
- **触发条件**: 编写数据库访问代码、创建 Mapper、编写 SQL 时

### 4. lumina_api_design
- **用途**: API 接口设计规范
- **路径**: `skills/lumina_api_design/SKILL.md`
- **触发条件**: 设计 REST API、创建 Controller、定义 DTO 时

### 5. lumina_domain_model
- **用途**: 领域模型实践规范
- **路径**: `skills/lumina_domain_model/SKILL.md`
- **触发条件**: 设计领域实体、创建业务逻辑、实现领域方法时

### 6. lumina_json_serialization
- **用途**: JSON 序列化规范
- **路径**: `skills/lumina_json_serialization/SKILL.md`
- **触发条件**: 处理 JSON 序列化、创建 DTO、配置 Jackson 时

### 7. lumina_git_commit
- **用途**: Git Commit 信息生成规范
- **路径**: `skills/lumina_git_commit/SKILL.md`
- **触发条件**: 生成 Git 暂存区变更的提交信息时

### 8. lumina_testing
- **用途**: 测试规范（单元/集成）
- **路径**: `skills/lumina_testing/SKILL.md`
- **触发条件**: 编写测试、创建测试类、验证业务逻辑时

### 9. lumina_observability
- **用途**: 可观测性规范（日志/审计/指标）
- **路径**: `skills/lumina_observability/SKILL.md`
- **触发条件**: 添加日志、审计标注、指标埋点时

### 10. lumina_conversation
- **用途**: 会话与记忆管理规范
- **路径**: `skills/lumina_conversation/SKILL.md`
- **触发条件**: 处理多轮对话、记忆管理、流式输出时

### 11. lumina_frontend_design
- **用途**: 前端 UI 设计规范与设计系统（自进化）
- **路径**: `skills/lumina_frontend_design/SKILL.md`
- **触发条件**: 创建/修改 Vue 组件、CSS/SCSS、布局、主题、动画时
- **自进化**: 每次使用后更新 `lumina-frontend/DESIGN.md` 和 `.agents/ui-learnings.md`

### 12. lumina_redis
- **用途**: Redis 操作规范
- **路径**: `skills/lumina_redis/SKILL.md`
- **触发条件**: 任何涉及 Redis 读写的后端代码（禁止直接用 RedisTemplate/RedissonClient，必须走 RedisCacheManager）

### 13. lumina_flyway
- **用途**: Flyway 迁移规范
- **路径**: `skills/lumina_flyway/SKILL.md`
- **触发条件**: 创建数据库迁移文件、INSERT 种子数据、ALTER TABLE 时

## 技能包使用说明

1. **自动识别**: AI 助手应根据用户请求和代码上下文自动识别需要使用的技能包
2. **按需加载**: 优先加载技能包的元数据，需要时再加载完整内容
3. **引用规范**: 使用 `@skills/lumina_xxx/SKILL.md` 或 `skills/lumina_xxx/SKILL.md` 来引用特定技能包

## 项目结构规范

- **基础包名**: `io.lumina`
- **模块包名**: `io.lumina.{domain}`
- **模块划分**: 
  - `lumina-common`: 公共工具类
  - `lumina-framework`: 框架配置
  - `lumina-agent-core`: Agent 核心
  - `lumina-gateway`: 网关模块
  - `lumina-business-base`: 业务基础模块
  - `lumina-business-agent`: Agent 业务模块
  - `lumina-frontend`: 前端模块

## 代码规范要点

1. **命名规范**: 遵循 Lumina 代码风格规范（见 `lumina_code_style` skill）
2. **架构规范**: 遵循简化分层架构（见 `lumina_architecture` skill）
3. **API 设计**: 遵循 RESTful 规范（见 `lumina_api_design` skill）
4. **数据库操作**: 使用 MyBatis-Plus（见 `lumina_mybatis_plus` skill）
5. **JSON 处理**: 使用 Jackson（见 `lumina_json_serialization` skill）
6. **领域建模**: 遵循领域模型实践（见 `lumina_domain_model` skill）
7. **Redis 操作**: 必须走 RedisCacheManager（见 `lumina_redis` skill）
8. **Flyway 迁移**: 写 SQL 前先检查已有约定（见 `lumina_flyway` skill）

## 后端编码前置检查清单（Pre-Delivery Checklist）

每次新增后端模块/功能时，必须执行以下检查：

- [ ] **加载技能包**: 写代码前 load `lumina_architecture` + `lumina_api_design` + `lumina_mybatis_plus` + `lumina_redis`(如涉及Redis) + `lumina_flyway`(如涉及SQL)
- [ ] **先读已有代码**: 读一个同类已有模块（如 Tenant）作为模板，确认 import 路径、基类、命名约定
- [ ] **分层完整**: Controller → Service → ServiceImpl → Mapper（禁止 Controller 直接注入 Mapper）
- [ ] **Domain 层**: 有业务逻辑的模块必须有 Domain Entity（参考 Agent/Tenant）
- [ ] **写操作事务**: Service 的 create/update/delete 方法标注 `@Transactional(rollbackFor = Exception.class)`
- [ ] **异常用 ErrorCode**: `throw new BusinessException(ErrorCode.XXX)` 而非硬编码字符串
- [ ] **校验注解**: DTO 用 `@NotBlank`/`@Size`/`@Min`/`@Max`，Controller 用 `@Valid`
- [ ] **Controller 注解**: `@Slf4j` + `@Validated` + `@RequiredArgsConstructor`
- [ ] **@Audit 规范**: module 用小写（如 `"llm_provider"`），action 用标准枚举（CREATE/UPDATE/DELETE）
- [ ] **Redis 操作**: 通过 `RedisCacheManager`，禁止直接用 `RedisTemplate`/`RedissonClient`
- [ ] **SQL 列名**: INSERT 语句的列名必须与已有迁移一致（先 grep 检查）
- [ ] **API 路径**: RESTful 命名 + 检查 Gateway 路由是否覆盖
- [ ] **编译验证**: `mvn compile` 通过

## Git Commit 规范

生成 Git Commit 信息时，遵循 `lumina_git_commit` skill 中的规范：

- 格式: `<type>(<scope>): <subject>`
- 类型: feat, fix, docs, style, refactor, perf, test, chore, build
- 使用中文描述
- 详细描述大型变更

## 开发工作流

1. 创建新功能时，先检查相关技能包
2. 编写代码时，自动应用相关规范
3. 提交代码前，生成符合规范的 Commit 信息
4. 代码审查时，检查是否符合项目规范

