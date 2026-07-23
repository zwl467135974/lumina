# 06 — MyBatis-Plus 基础

> **前置要求**：已完成 [05-Spring Boot 在 Lumina](05-spring-boot-in-lumina.md)
> **预计阅读**：25 分钟
> **难度**：⭐⭐☆☆☆

---

## 这节解决什么问题

后端开发 80% 的工作是"读写数据库"。传统方式你得手写 SQL，然后手动把查询结果一行行映射到 Java 对象——枯燥且重复。

MyBatis-Plus 说：**"这些增删改查我包了，你只管写复杂查询。"**

这节讲清 MyBatis-Plus 的基本用法：怎么定义一个 Mapper 就免费获得增删改查、怎么用 Wrapper 拼查询条件、怎么做分页。

---

## MyBatis-Plus 是什么？先建立直觉

### 传统 MyBatis（手写 SQL）

```java
// 你要自己写 SQL，自己映射结果
@Select("SELECT * FROM agent WHERE id = #{id}")
AgentDO selectById(Long id);

@Insert("INSERT INTO agent (name, type) VALUES (#{name}, #{type})")
int insert(AgentDO agent);
```

每张表都要写一遍。20 张表 = 100+ 个 SQL 方法。

### MyBatis-Plus（继承 BaseMapper，免费拿 CRUD）

```java
// 文件：lumina-modules/lumina-business-agent/.../mapper/AgentMapper.java
@Mapper
public interface AgentMapper extends BaseMapper<AgentDO> {
    // 空的！什么都不用写！
}
```

继承一个 `BaseMapper<T>`，你**免费获得**：
- `insert(entity)` — 插入
- `deleteById(id)` — 按 ID 删除
- `updateById(entity)` — 按 ID 更新
- `selectById(id)` — 按 ID 查询
- `selectList(wrapper)` — 条件查询
- `selectPage(page, wrapper)` — 分页查询
- ……共 20+ 个方法

> 💡 **MyBatis-Plus 是 MyBatis 的增强版**。MyBatis 能做的它都能做，但它额外帮你省掉了基础 CRUD 的样板代码。Lumina 全项目用它。

---

## 第一步：定义实体类（DO）

实体类和数据库表对应，用注解告诉 MyBatis-Plus 映射关系：

```java
// 文件：lumina-modules/lumina-business-agent/.../entity/LlmProviderDO.java
@Data                                    // ← Lombok：自动生成 getter/setter
@TableName("lumina_llm_provider")        // ← 指定数据库表名
public class LlmProviderDO implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)    // ← 主键，自增
    private Long id;

    @TableField("name")                  // ← 字段映射（列名 = name）
    private String name;

    @TableField("base_url")              // ← 驼峰 baseUrl ↔ 下划线 base_url
    private String baseUrl;

    @TableField("api_key_enc")
    private String apiKeyEnc;

    // --- 自动填充字段 ---
    @TableField(value = "create_time", fill = FieldFill.INSERT)          // ← 插入时自动填
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)   // ← 插入和更新都自动填
    private LocalDateTime updateTime;

    // --- 逻辑删除 ---
    @TableLogic                           // ← 标记这是逻辑删除字段
    @TableField("deleted")
    private Integer deleted;              // deleted=0 未删, deleted=1 已删
}
```

### 核心注解速查

| 注解 | 作用 | 示例 |
|------|------|------|
| `@TableName("表名")` | 指定表名 | `@TableName("lumina_llm_provider")` |
| `@TableId` | 标记主键 | `@TableId(type = IdType.AUTO)` 自增主键 |
| `@TableField("列名")` | 指定列名（驼峰↔下划线） | `@TableField("base_url")` |
| `@TableField(fill = ...)` | 自动填充 | `FieldFill.INSERT` 插入时填 |
| `@TableLogic` | 逻辑删除标记 | 删除变 UPDATE，不是真删 |

### 逻辑删除是什么？

```java
@TableLogic
@TableField("deleted")
private Integer deleted;
```

加了 `@TableLogic` 后，MyBatis-Plus 的 `deleteById()` **不会真的执行 DELETE**，而是执行：
```sql
UPDATE lumina_llm_provider SET deleted = 1 WHERE id = ?
```

而且所有查询自动加 `WHERE deleted = 0`——**已删除的数据你查不到，但它还在数据库里**。这在企业系统里很重要：数据不能真删（审计需要），但要"看起来删了"。

---

## 第二步：定义 Mapper（继承 BaseMapper）

```java
// 文件：lumina-modules/lumina-business-agent/.../mapper/AgentMapper.java
@Mapper                                    // ← 告诉 MyBatis 这是一个 Mapper 接口
public interface AgentMapper extends BaseMapper<AgentDO> {
    // 空的！继承 BaseMapper 就有了全部基础 CRUD
}
```

**3 行代码**，你就拥有了完整的单表增删改查能力。

> 💡 `@Mapper` 可以不加——如果配置了 `@MapperScan`（Lumina 在配置类里配了），MyBatis 会自动扫描。但加上更明确。

---

## 第三步：在 Service 里使用

### 基本 CRUD

```java
@Service
public class SomeServiceImpl {

    private final AgentMapper agentMapper;  // ← 注入 Mapper

    // 插入
    public void create() {
        AgentDO agent = new AgentDO();
        agent.setAgentName("助手1");
        agentMapper.insert(agent);           // 自动生成 INSERT SQL
    }

    // 按 ID 查
    public AgentDO getById(Long id) {
        return agentMapper.selectById(id);   // 自动生成 SELECT ... WHERE id = ?
    }

    // 按 ID 更新
    public void update(AgentDO agent) {
        agentMapper.updateById(agent);       // 自动生成 UPDATE ... WHERE id = ?
    }

    // 按 ID 删除（逻辑删除）
    public void delete(Long id) {
        agentMapper.deleteById(id);          // 自动生成 UPDATE SET deleted=1 WHERE id = ?
    }
}
```

### 条件查询：LambdaQueryWrapper

这是 MyBatis-Plus 最强大的特性——**用 Java 代码拼 SQL 条件，不用写字符串 SQL**：

```java
// 传统方式：手写 SQL 字符串（容易拼错、SQL 注入风险）
@Select("SELECT * FROM agent WHERE tenant_id = #{tenantId} AND name LIKE #{name} ORDER BY create_time DESC")

// MyBatis-Plus：LambdaQueryWrapper（类型安全、编译期检查）
LambdaQueryWrapper<AgentDO> wrapper = new LambdaQueryWrapper<>();
wrapper.eq(AgentDO::getTenantId, 1L);           // WHERE tenant_id = 1
wrapper.like(AgentDO::getAgentName, "助手");      // AND name LIKE '%助手%'
wrapper.orderByDesc(AgentDO::getCreateTime);     // ORDER BY create_time DESC
List<AgentDO> agents = agentMapper.selectList(wrapper);
```

#### Lumina 真实案例：动态条件查询

```java
// 文件：lumina-modules/lumina-business-agent/.../LlmProviderServiceImpl.java，list() 方法
public List<LlmProviderVO> list(QueryLlmProviderDTO query) {
    LambdaQueryWrapper<LlmProviderDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(LlmProviderDO::getTenantId, currentTenantId());    // ← 必加：租户隔离

    if (StringUtils.hasText(query.getName())) {
        wrapper.like(LlmProviderDO::getName, query.getName());    // ← 有值才加条件
    }
    if (StringUtils.hasText(query.getProvider())) {
        wrapper.eq(LlmProviderDO::getProvider, query.getProvider());
    }
    if (query.getStatus() != null) {
        wrapper.eq(LlmProviderDO::getStatus, query.getStatus());
    }

    wrapper.orderByDesc(LlmProviderDO::getCreateTime);            // ← 排序
    return llmProviderMapper.selectList(wrapper);                 // ← 执行查询
}
```

这就是"动态查询"——**条件按需拼接，不传就不加**。比拼 SQL 字符串安全得多。

### 常用条件方法

| 方法 | 对应 SQL | 示例 |
|------|----------|------|
| `eq(field, value)` | `= value` | `eq(AgentDO::getStatus, 1)` |
| `ne(field, value)` | `!= value` | `ne(AgentDO::getStatus, 0)` |
| `gt(field, value)` | `> value` | `gt(AgentDO::getCreateTime, yesterday)` |
| `like(field, value)` | `LIKE '%value%'` | `like(AgentDO::getName, "助手")` |
| `in(field, list)` | `IN (...)` | `in(AgentDO::getId, List.of(1,2,3))` |
| `isNull(field)` | `IS NULL` | `isNull(AgentDO::getApiKey)` |
| `orderByDesc(field)` | `ORDER BY field DESC` | `orderByDesc(AgentDO::getCreateTime)` |
| `last("LIMIT 1")` | 末尾追加 SQL | `last("LIMIT 1")` |

### 分页查询

```java
// 创建 Page 对象（当前页 + 每页大小）
Page<AgentDO> page = new Page<>(pageNum, pageSize);

// 执行分页查询
Page<AgentDO> result = agentMapper.selectPage(page, wrapper);

// 获取结果
List<AgentDO> records = result.getRecords();    // 当前页数据
long total = result.getTotal();                  // 总记录数
```

> ⚠️ 分页需要配置 `PaginationInnerInterceptor`，否则不会真的分页。Lumina 已配好，详见下一篇。

---

## 为什么用 `DO::getXxx` 而不是字符串 "xxx"？

```java
// ❌ 不推荐：写字符串，拼错了运行时才报错
wrapper.eq("tenant_id", tenantId);

// ✅ 推荐：用方法引用，编译期就能检查字段名
wrapper.eq(AgentDO::getTenantId, tenantId);
```

`AgentDO::getTenantId` 是**方法引用**（Java 语法），如果 `AgentDO` 没有 `getTenantId` 方法，编译时就报错。字符串 `"tenant_id"` 拼错了要等运行时才发现。

---

## 动手试试

1. **打开 `AgentMapper.java`**：确认它只有 3 行代码
2. **打开 `LlmProviderDO.java`**：数数它用了几个 MyBatis-Plus 注解（@TableName/@TableId/@TableField/@TableLogic）
3. **打开 `LlmProviderServiceImpl.java` 的 `list()` 方法**：读懂 LambdaQueryWrapper 的条件拼接逻辑

---

## 小结

| 你现在应该知道 | 一句话记忆 |
|---------------|-----------|
| BaseMapper | 继承它，免费拿 20+ 个 CRUD 方法 |
| 实体注解 | @TableName 表名、@TableId 主键、@TableLogic 逻辑删除 |
| LambdaQueryWrapper | 用 Java 代码拼查询条件，类型安全 |
| 动态条件 | if 判断 + wrapper 链式调用 = 按需拼条件 |
| 分页 | Page 对象 + selectPage()，自动算总数 |

---

## 下一步

下一篇讲 [MyBatis-Plus 在 Lumina 的高级实践](07-mybatis-plus-in-lumina.md)——自动填充机制、多租户拦截器初识、分页拦截器配置。

> 🚀 **现在继续**：[07 — MyBatis-Plus 在 Lumina →](07-mybatis-plus-in-lumina.md)

---

## 自测题

1. **`AgentMapper extends BaseMapper<AgentDO>` 只写了 3 行代码，你怎么就拥有了 CRUD？**
   <details><summary>答案</summary>BaseMapper 里定义了 20+ 个默认方法（insert/deleteById/selectById...），MyBatis-Plus 根据泛型 AgentDO 和表注解自动生成对应 SQL。</details>

2. **`@TableLogic` 标注的字段，调用 `deleteById()` 时实际执行什么 SQL？**
   <details><summary>答案</summary>不是 DELETE，而是 UPDATE ... SET deleted = 1 WHERE id = ?。而且后续查询自动加 WHERE deleted = 0。</details>

3. **LambdaQueryWrapper 为什么用 `AgentDO::getTenantId` 而不是 `"tenant_id"`？**
   <details><summary>答案</summary>方法引用在编译期检查字段名存在，拼错了编译就报错。字符串拼写错运行时才报错。</details>

4. **怎么实现"只在参数非空时才加查询条件"？**
   <details><summary>答案</summary>用 if 判断包裹 wrapper 条件：`if (name != null) { wrapper.like(...); }`，不传就不拼接。</details>

---

📝 **本篇撰写期间修正的代码**：无。
