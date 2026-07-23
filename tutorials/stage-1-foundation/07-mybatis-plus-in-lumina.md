# 07 — MyBatis-Plus 在 Lumina 的实践

> **前置要求**：已完成 [06-MyBatis-Plus 基础](06-mybatis-plus-basics.md)
> **预计阅读**：20 分钟
> **难度**：⭐⭐☆☆☆

---

## 这节解决什么问题

上一节你学会了基本的 CRUD 和条件查询。这节看 Lumina 怎么用 MyBatis-Plus 的高级特性解决企业级问题：

- **自动填充**：create_time / update_time / tenant_id 不用手动写，自动填
- **分页拦截器**：selectPage 怎么自动算总数、自动加 LIMIT
- **多租户拦截器初识**：为什么所有 SQL 自动带 `WHERE tenant_id = ?`

> 📖 多租户拦截器的深层原理（SQL 怎么被改写的）详见[第二阶 07-多租户隔离](../stage-2-application/07-multi-tenancy.md)和[第三阶 05-MyBatis 拦截器](../stage-3-mastery/05-mybatis-interceptor-internals.md)。

---

## 自动填充：告别手动 setCreateTime

### 问题

每张表都有 `create_time`、`update_time`、`create_by` 等审计字段。如果每次 insert/update 都要手动写：

```java
agent.setCreateTime(LocalDateTime.now());
agent.setUpdateTime(LocalDateTime.now());
agent.setCreateBy(BaseContext.getUserId());
// 每个方法都要写这几行……
```

太烦了。**MyBatis-Plus 的自动填充帮你解决**。

### 怎么标记

在实体类字段上标 `fill` 属性：

```java
// 文件：lumina-modules/lumina-business-agent/.../entity/LlmProviderDO.java
@TableField(value = "create_by", fill = FieldFill.INSERT)          // ← 插入时自动填
private Long createBy;

@TableField(value = "create_time", fill = FieldFill.INSERT)        // ← 插入时自动填
private LocalDateTime createTime;

@TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE) // ← 插入和更新都自动填
private LocalDateTime updateTime;
```

| fill 值 | 含义 |
|---------|------|
| `FieldFill.INSERT` | 只在 INSERT 时填充 |
| `FieldFill.UPDATE` | 只在 UPDATE 时填充 |
| `FieldFill.INSERT_UPDATE` | INSERT 和 UPDATE 都填充 |
| `FieldFill.DEFAULT` | 默认（不自动填充） |

### 怎么实现

Lumina 有一个 `MetaObjectHandler` 实现类，告诉 MyBatis-Plus "填充时填什么值"：

```java
// 简化示意（实际实现位置在 lumina-framework 或 lumina-business-base）
@Component
public class LuminaMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        // INSERT 时自动填这些
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "createBy", Long.class, BaseContext.getUserId());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // UPDATE 时自动填这些
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
```

**效果**：你只管 `agentMapper.insert(agent)`，create_time / update_time / create_by 全部自动填好。

---

## 分页拦截器：selectPage 为什么能自动分页

### 问题

上一节你写了 `selectPage(page, wrapper)`，但它为什么能自动算总数、自动加 LIMIT？

### 答案：PaginationInnerInterceptor

Lumina 注册了分页拦截器，它会在 SQL 执行前**自动改写 SQL**：

```java
// 文件：lumina-modules/lumina-business-base/.../config/MybatisPlusTenantConfig.java
@Bean(name = "mybatisPlusInterceptor")
@Primary
public MybatisPlusInterceptor mybatisPlusInterceptor(TenantLineHandlerImpl tenantLineHandler) {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

    // 多租户拦截器（后面讲）
    TenantLineInnerInterceptor tenantInterceptor = new TenantLineInnerInterceptor();
    tenantInterceptor.setTenantLineHandler(tenantLineHandler);
    interceptor.addInnerInterceptor(tenantInterceptor);

    // 分页拦截器 ← 就是它让 selectPage 生效
    PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
    paginationInterceptor.setMaxLimit(100L);   // ← 每页最多 100 条，防止恶意大查询
    interceptor.addInnerInterceptor(paginationInterceptor);

    return interceptor;
}
```

**执行过程**（你看不到，但确实发生了）：
```
你写：agentMapper.selectPage(new Page<>(1, 10), wrapper)

拦截器改写后实际执行两条 SQL：
1. SELECT COUNT(*) FROM agent WHERE tenant_id = 1 AND deleted = 0  → 算总数
2. SELECT * FROM agent WHERE tenant_id = 1 AND deleted = 0 LIMIT 10 → 取第一页
```

> ⚠️ **注意第 28-29 行的注释**：**多租户拦截器必须在分页拦截器之前**！否则分页 count 重写时会与租户条件冲突，触发 `Parameter index out of range`。这是 Lumina 踩过的坑，注释特意记录了。

---

## 多租户拦截器初识：SQL 自动加 tenant_id

### 这是什么？

这是 Lumina 作为**企业级多租户平台**的核心机制。简单说：**A 租户的数据，B 租户绝对查不到**。

### 怎么做到的？

```java
// 你写的代码（完全没提 tenant_id）：
agentMapper.selectList(new LambdaQueryWrapper<AgentDO>()
    .like(AgentDO::getAgentName, "助手"));

// 拦截器改写后实际执行的 SQL：
SELECT * FROM lumina_agent
WHERE deleted = 0
  AND agent_name LIKE '%助手%'
  AND tenant_id = 1          ← ← 拦截器自动加的！你完全没写
```

**业务代码完全无感**——你不用每次查询都记得加 `WHERE tenant_id = ?`，拦截器自动帮你加。

### 它怎么知道当前是哪个租户？

从 `BaseContext`（ThreadLocal 上下文）取。每个 HTTP 请求进来时，JWT 认证会把用户信息（包括 tenant_id）存到 `BaseContext`：

```java
// TenantLineHandlerImpl 的核心逻辑（简化）
@Override
public Expression getTenantId() {
    Long tenantId = BaseContext.getTenantId();   // ← 从当前请求上下文取租户 ID
    return new LongValue(tenantId);
}
```

> 📖 完整的多租户机制（TenantLineHandler 怎么判断哪些表要拦截、怎么防绕过）详见[第二阶 07-多租户隔离](../stage-2-application/07-multi-tenancy.md)。

---

## 全局配置：逻辑删除、驼峰映射

Lumina 在 `application.yml` 里的全局配置：

```yaml
# 文件：lumina-standalone/src/main/resources/application.yml（简化）
mybatis-plus:
  global-config:
    db-config:
      logic-delete-field: deleted      # 全局逻辑删除字段名
      logic-delete-value: 1            # 已删除的值
      logic-not-delete-value: 0        # 未删除的值
      id-type: auto                    # 全局主键策略：自增
  configuration:
    map-underscore-to-camel-case: true # 下划线 ↔ 驼峰自动转换
```

**`map-underscore-to-camel-case`** 意味着：数据库的 `agent_name` 列自动映射到 Java 的 `agentName` 字段，不用每个字段都写 `@TableField`。

---

## 在 Lumina 里总结 MyBatis-Plus 的层次

```
你的 Service 代码
    ↓ 调用
AgentMapper extends BaseMapper<AgentDO>     ← 你写的：定义 Mapper
    ↓ 触发 SQL 执行
MybatisPlusInterceptor 拦截器链              ← 框架的：自动改写 SQL
    ├── TenantLineInnerInterceptor           ←   自动加 WHERE tenant_id = ?
    └── PaginationInnerInterceptor           ←   自动加 LIMIT、算 COUNT
    ↓ 改写后的 SQL
MySQL 数据库
```

你写的只有最上面一层（Mapper 接口 + Wrapper 条件），下面全是框架自动处理的。

---

## 动手试试

1. **打开 `MybatisPlusTenantConfig.java`**：找到注释说的"多租户→分页顺序"注意事项
2. **打开任意 DO 文件**（如 `AgentDO.java`）：找 `@TableField(fill = ...)` 标注的字段
3. **思考**：如果没有自动填充，你在 `createAgent()` 方法里要手动写几行 set？

---

## 小结

| 特性 | 一句话记忆 |
|------|-----------|
| 自动填充 | `fill = FieldFill.INSERT` + MetaObjectHandler = createTime/updateTime 自动填 |
| 分页拦截器 | selectPage 自动算 COUNT + 加 LIMIT，不用手写 |
| 多租户拦截器 | 所有 SQL 自动加 `WHERE tenant_id = ?`，业务无感 |
| 拦截器顺序 | **多租户 → 分页**，顺序错了 count 重写会报错 |
| 全局配置 | application.yml 配逻辑删除值、驼峰映射、主键策略 |

---

## 下一步

后端数据库访问就讲完了。接下来进入 [Redis 基础](08-redis-basics.md)——不只是缓存，还是分布式锁、限流、消息广播的基础设施。

> 🚀 **现在继续**：[08 — Redis 基础 →](08-redis-basics.md)

---

## 自测题

1. **`@TableField(fill = FieldFill.INSERT_UPDATE)` 标注的 updateTime 字段，什么时候被自动填充？**
   <details><summary>答案</summary>INSERT 和 UPDATE 都会自动填充为当前时间。INSERT 时不填 update_time 是常见的坑，用 INSERT_UPDATE 就能避免。</details>

2. **为什么多租户拦截器必须在分页拦截器之前注册？**
   <details><summary>答案</summary>分页拦截器会执行 COUNT(*) 重写，如果租户条件还没加进去，COUNT 和实际数据的 WHERE 条件不一致，会导致参数索引错乱（Parameter index out of range）。</details>

3. **你在 Service 代码里完全没写 tenant_id，为什么查询结果自动过滤了？**
   <details><summary>答案</summary>TenantLineInnerInterceptor 在 SQL 执行前拦截，自动在 WHERE 条件里加 `tenant_id = 当前租户ID`。当前租户 ID 从 BaseContext（ThreadLocal）取。</details>

4. **`paginationInterceptor.setMaxLimit(100L)` 是什么意思？为什么要设？**
   <details><summary>答案</summary>每页最多 100 条。防止恶意用户传 pageSize=999999 拉爆数据库。是防御性编程措施。</details>

---

📝 **本篇撰写期间修正的代码**：无。
