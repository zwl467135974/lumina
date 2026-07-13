---
name: lumina_architecture
description: Use this skill when designing module structure, creating new modules, or organizing code layers. This skill enforces Lumina's simplified layered architecture pattern with clear separation of API, Service, Domain, and Infrastructure layers.
---

# Lumina 简化分层架构规范

## 功能概述

本技能包用于确保 Lumina 框架项目遵循简化分层架构，包括层次划分、职责定义、依赖关系等。

## 标准分层结构

```
lumina-modules/
└── lumina-{domain}/
    └── src/main/java/io/lumina/
        └── {domain}/
            ├── api/                    # 接口层 (API Layer)
            │   ├── controller/         # REST 控制器
            │   └── dto/                # 数据传输对象 (Request/Response)
            │
            ├── service/                 # 业务服务层 (Service Layer) - 核心
            │   ├── {业务}Service.java  # 业务服务接口
            │   └── impl/                # 业务服务实现
            │
            ├── domain/                 # 领域模型层 (Domain Layer)
            │   ├── model/               # 领域实体（包含业务方法）
            │   └── enums/               # 领域枚举
            │   │
            │   # 以下为可选（根据业务复杂度决定是否使用）
            │   ├── valueobject/        # 值对象（复杂场景使用）
            │   ├── event/              # 领域事件（如需要事件驱动）
            │   └── repository/         # 仓储接口（如需要抽象持久化）
            │
            └── infrastructure/          # 基础设施层 (Infrastructure Layer)
                ├── mapper/              # MyBatis Mapper
                ├── entity/              # 数据库实体 (DO - Data Object)
                └── converter/           # DO <-> Domain 转换器（可选）
                │
                # 以下为可选
                ├── external/            # 外部服务调用
                └── config/              # 配置类
```

## 层次职责说明

### API 层 (接口层)
- **职责**: 处理 HTTP 请求/响应，参数校验，权限控制
- **包含**: Controller、DTO (Request/Response)
- **规则**:
  - Controller 只负责接收请求、调用 Service、返回响应
  - 不包含业务逻辑
  - 使用 DTO 进行数据传输

### Service 层 (业务服务层) - 核心
- **职责**: 业务逻辑处理，事务管理，业务流程编排
- **包含**: Service 接口和实现
- **规则**:
  - 包含应用服务逻辑（编排、事务）
  - 包含领域服务逻辑（复杂业务规则）
  - 调用 Domain 模型的方法
  - 调用 Infrastructure 的 Mapper
  - 负责 DTO 与 Domain 对象的转换

### Domain 层 (领域模型层)
- **职责**: 领域实体和业务规则
- **包含**: Entity、枚举
- **可选**: Value Object、Domain Event、Repository 接口（根据业务复杂度决定）
- **规则**:
  - Entity 包含业务方法和业务规则
  - 不依赖其他层（保持领域模型的纯净）
  - **简单场景**: 直接使用基本类型（String、Long 等）
  - **复杂场景**: 使用 Value Object 封装业务规则
  - Repository 接口可选，简单场景直接使用 Mapper

### Infrastructure 层 (基础设施层)
- **职责**: 技术实现，数据库访问，外部服务调用
- **包含**: Mapper、DO、Converter（可选）
- **规则**:
  - DO (Data Object) 对应数据库表结构
  - 负责 DO 与 Domain Entity 的转换（简单场景可以直接转换）
  - 如果 DO 和 Domain 字段一致，可以合并使用

## 依赖规则

- **API 层** → 依赖 Service 层
- **Service 层** → 依赖 Domain 层、Infrastructure 层
- **Domain 层** → 不依赖其他层（保持纯净）
- **Infrastructure 层** → 依赖 Domain 层（实现 Domain 的接口）

## 使用场景

- 创建新模块时，确保目录结构符合规范
- 设计新功能时，确保代码放在正确的层次
- 代码审查时，检查层次职责是否清晰
- 重构代码时，确保依赖关系正确

## 检查清单

- [ ] 模块目录结构是否符合规范
- [ ] Controller 是否只调用 Service，不包含业务逻辑
- [ ] Service 是否包含业务逻辑，调用 Domain 方法
- [ ] Domain 是否不依赖其他层
- [ ] Infrastructure 是否正确实现持久化
- [ ] 依赖关系是否符合规范（单向依赖）

## 可用资源

- `references/layer-responsibilities.md`: 详细层次职责说明
- `examples/module-structure/`: 标准模块结构示例
- `examples/good-layering.java`: 正确的分层示例
- `examples/bad-layering.java`: 错误的分层示例

