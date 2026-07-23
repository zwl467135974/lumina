# 13 — 测试实践

> **前置要求**：已完成 [12-配置管理](12-config-management.md)
> **预计阅读**：20 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

"代码写完了，怎么知道没问题？"——答案是测试。Lumina 有 770+ 个测试。这节讲两种测试怎么写、JaCoCo 覆盖率怎么卡。

---

## 两种测试

| 类型 | 隔离方式 | 速度 | 测什么 |
|------|----------|------|--------|
| **单元测试** | Mock 掉外部依赖 | 快（毫秒） | 业务逻辑 |
| **集成测试** | 连真实 MySQL/Redis | 慢（秒） | 全链路 |

### 单元测试（Mockito）

```java
@ExtendWith(MockitoExtension.class)
class BudgetServiceUnitTest {

    @Mock                          // ← 模拟 Mapper（不连数据库）
    private BudgetRuleMapper ruleMapper;

    @Mock
    private RedisCacheManager redisCacheManager;

    @InjectMocks                   // ← 把 Mock 注入到被测对象
    private BudgetServiceImpl service;

    @Test
    void shouldCreateRule() {
        // given：准备数据
        BudgetRuleDTO dto = new BudgetRuleDTO();
        dto.setRuleName("测试规则");

        // when：调方法
        BudgetRule result = service.createRule(dto);

        // then：验证
        assertThat(result.getRuleName()).isEqualTo("测试规则");
        verify(ruleMapper).insert(any());    // 验证 insert 被调了
    }
}
```

**关键**：`@Mock` 创建假对象，`@InjectMocks` 把假对象注入被测类。不连数据库，测试飞快。

### 集成测试（真实 MySQL）

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {
    // 基类：所有集成测试继承它
}

class AgentExecutionChainIntegrationTest extends BaseIntegrationTest {

    @Test
    @Transactional                      // 测试后自动回滚（不污染数据）
    void shouldPersistTokenAfterExecution() {
        // 真实连 MySQL + Redis
        // 验证执行 Agent 后 task 表确实有 token 数据
    }
}
```

**关键**：`@Transactional` 让测试数据自动回滚——每个测试跑完数据库恢复原样，互不影响。

---

## 测试三板斧（AAA 模式）

```java
@Test
void testSomething() {
    // Arrange（准备）
    given(condition);

    // Act（执行）
    Result result = when(youCallMethod());

    // Assert（断言）
    assertThat(result).isEqualTo(expected);
}
```

---

## JaCoCo：覆盖率门控

```xml
<!-- 文件：pom.xml（简化）-->
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <executions>
        <execution>
            <id>check</id>
            <phase>verify</phase>          <!-- verify 阶段检查 -->
            <goals><goal>check</goal></goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <minimum>0.25</minimum>   <!-- 行覆盖率 ≥ 25% -->
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

**效果**：`mvn verify` 时如果覆盖率 < 25%，**构建失败**。

---

## BaseContext 在测试里的设置

很多代码依赖 `BaseContext.getTenantId()`（ThreadLocal）。测试里要手动初始化：

```java
@BeforeEach
void setUp() {
    BaseContext.initFromHeaders(1L, "admin", 0L, "SUPER_ADMIN", "agent:list");
    // userId=1, username=admin, tenantId=0, roles, permissions
}

@AfterEach
void tearDown() {
    BaseContext.clear();    // 清理 ThreadLocal
}
```

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| 单元测试 | Mock 掉依赖，测业务逻辑，快 |
| 集成测试 | 连真实 DB，测全链路，慢 |
| @Transactional | 测试数据自动回滚 |
| JaCoCo | 覆盖率 < 25% 构建失败 |
| BaseContext | 测试里手动 init + clear |

> 🚀 [14 — Git 规范 →](14-git-commit-convention.md)

---

## 自测题

1. **单元测试为什么要 Mock 掉 Mapper？**
   <details><summary>答案</summary>不连数据库→快（毫秒级）、可重复（不依赖环境状态）、隔离（测的是业务逻辑不是数据库）。</details>

2. **集成测试的 @Transactional 有什么作用？**
   <details><summary>答案</summary>测试方法执行后自动回滚——数据不残留，每个测试互不影响。</details>

---

📝 **本篇撰写期间修正的代码**：无。
