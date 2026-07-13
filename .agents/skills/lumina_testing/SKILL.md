---
name: lumina_testing
description: Use this skill when writing tests (unit or integration). Covers Mockito mocking patterns, BaseContext ThreadLocal setup, @Transactional rollback isolation, and integration testing with real MySQL via test profile.
---

# Lumina 测试规范

## 功能概述

本技能包用于确保 Lumina 框架项目的测试代码质量，涵盖单元测试与集成测试的编写规范，包括 Mockito Mock 模式、BaseContext ThreadLocal 上下文设置、`@Transactional` 回滚隔离，以及基于真实 MySQL 的集成测试。

## 单元测试规范

### 基本结构

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserServiceImpl userService;

    // ...
}
```

- 使用 `@ExtendWith(MockitoExtension.class)` 替代 `@SpringBootTest`，避免启动容器
- Mapper 层用 `@Mock` 模拟，Service 层用 `@InjectMocks` 自动注入
- 禁止在单元测试中使用 `@Autowired` 注入真实 Bean

### BaseContext 上下文设置

```java
@BeforeEach
void setUp() {
    BaseContext.setCurrentTenantId(1L);
    BaseContext.setCurrentUserId(100L);
}

@AfterEach
void tearDown() {
    BaseContext.clear();
}
```

- 每个 `@BeforeEach` 设置租户/用户上下文（ThreadLocal）
- 每个 `@AfterEach` 必须调用 `BaseContext.clear()` 清理 ThreadLocal，防止线程污染
- 忘记清理会导致后续测试的租户隔离失效

### 断言规范

```java
// 正常用例
assertThat(result.getId()).isNotNull();
assertThat(result.getUsername()).isEqualTo("testuser");

// 异常用例：使用 assertThatThrownBy 验证 BusinessException
assertThatThrownBy(() -> userService.create(dto))
    .isInstanceOf(BusinessException.class)
    .hasMessageContaining("用户名已存在");

// 租户隔离验证
assertThatThrownBy(() -> userService.getById(otherTenantId))
    .isInstanceOf(BusinessException.class);
```

- 正常用例：使用 AssertJ 的 `assertThat` 流式断言
- 异常用例：使用 `assertThatThrownBy` 验证抛出的 `BusinessException`
- 必须覆盖的场景：租户隔离、admin 保护、边界校验（空值/重复/越权）

## 集成测试规范

### 基类继承

```java
class UserIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserService userService;

    @Test
    @Transactional
    void createUserAndQuerySuccess() {
        // ...
    }
}
```

- 所有集成测试继承 `BaseIntegrationTest`
- `BaseIntegrationTest` 配置：`@SpringBootTest` + `@ActiveProfiles("test")`
- test profile 连接本地 MySQL `lumina_dev` 数据库
- 使用 `@Transactional` 保证测试数据自动回滚，不污染数据库

### 集成测试原则

- 集成测试验证完整调用链路（Controller → Service → Mapper → DB）
- 使用真实数据库验证 SQL 正确性、租户拦截器、权限检查
- 每个 `@Transactional` 测试方法执行后自动回滚
- 禁止在集成测试中 Mock 数据库层

## 命名规范

| 类型 | 类名格式 | 示例 |
|------|---------|------|
| 单元测试 | `{Class}Test` | `UserServiceTest` |
| 集成测试 | `{Class}IntegrationTest` | `UserIntegrationTest` |

方法命名使用行为描述（场景 + 期望结果）：

- `createUserSuccess` — 正常创建用户
- `duplicateUsernameThrows` — 用户名重复抛异常
- `tenantIsolationCrossTenant` — 跨租户隔离验证
- `adminProtectionBlocksDelete` — admin 保护阻止删除

## 现有测试覆盖

| 模块 | 单元测试数 | 说明 |
|------|-----------|------|
| lumina-common | 28 | 工具类、异常、上下文 |
| lumina-agent-core | 15 | Agent 引擎、工具、记忆 |
| lumina-framework | 4 | 框架配置、拦截器 |
| lumina-business-base | 32 | 用户/角色/权限/租户 CRUD |
| lumina-business-agent | 10 | Agent 业务服务 |
| **合计** | **89 单元 + 6 集成** | — |

## 最佳实践

1. **每个公开方法至少一个测试**：覆盖正常路径和异常路径
2. **Mock 最小化**：只 Mock 直接依赖（Mapper），不要 Mock 间接依赖
3. **测试数据自包含**：在测试方法内构造数据，不依赖其他测试的执行顺序
4. **租户隔离必测**：涉及数据查询的方法必须验证跨租户隔离
5. **ThreadLocal 必清理**：`@AfterEach` 中调用 `BaseContext.clear()`
