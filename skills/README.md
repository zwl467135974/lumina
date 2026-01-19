# Lumina 框架开发 Skill 集合

本目录包含 Lumina 框架项目的开发 Skill，用于指导 AI 助手在开发过程中遵循项目规范。

## Skill 列表

### 1. lumina_code_style
**用途**: 代码风格和命名规范
**触发条件**: 编写 Java 代码、创建新类、代码审查时
**包含内容**: 包命名、类命名、方法命名、变量命名、注释规范

### 2. lumina_architecture
**用途**: 简化分层架构规范
**触发条件**: 设计模块结构、创建新模块、组织代码层次时
**包含内容**: 标准分层结构、层次职责、依赖规则

### 3. lumina_mybatis_plus
**用途**: MyBatis-Plus 使用规范
**触发条件**: 编写数据库访问代码、创建 Mapper、编写 SQL 时
**包含内容**: MyBatis-Plus 基础使用、复杂查询、SQL 编写禁止事项

### 4. lumina_api_design
**用途**: API 接口设计规范
**触发条件**: 设计 REST API、创建 Controller、定义 DTO 时
**包含内容**: RESTful 设计、统一响应格式、参数校验

### 5. lumina_domain_model
**用途**: 领域模型实践规范
**触发条件**: 设计领域实体、创建业务逻辑、实现领域方法时
**包含内容**: 实体设计、业务方法封装、值对象使用

### 6. lumina_json_serialization
**用途**: JSON 序列化规范
**触发条件**: 处理 JSON 序列化、创建 DTO、配置 Jackson 时
**包含内容**: Jackson 使用、注解规范、日期格式化

## 使用方法

### 在 Cursor / Claude Code 中使用

1. **自动识别**: AI 助手会根据上下文自动识别需要使用的 Skill
2. **按需加载**: Skill 采用渐进式加载，先加载元数据，需要时加载完整内容
3. **资源访问**: 可以访问 Skill 中的示例代码和参考文档

### 在 AgentScope 中使用

```java
// 从文件系统加载 Skill
FileSystemSkillRepository repository = new FileSystemSkillRepository(
    Path.of("lumina/skills")
);

// 注册 Skill
SkillBox skillBox = new SkillBox();
skillBox.registration()
    .skill(repository.loadSkill("lumina_code_style"))
    .apply();
```

## Skill 结构

每个 Skill 包含：
- `SKILL.md`: 必需，包含 YAML frontmatter 和技能说明
- `references/`: 可选，详细参考文档
- `examples/`: 可选，示例代码
- `scripts/`: 可选，可执行脚本

## 更新和维护

- 当开发规范更新时，需要同步更新对应的 Skill
- 建议定期审查 Skill 内容，确保与实际开发规范一致
- 可以根据项目需要添加新的 Skill

## 参考文档

- [Lumina 开发规范与编码标准](../Lumina开发规范与编码标准.md)
- [AgentScope Skill 文档](https://java.agentscope.io/en/task/agent-skill.html)
- [Claude Agent Skills 文档](https://platform.claude.com/docs/zh-CN/agents-and-tools/agent-skills/overview)

