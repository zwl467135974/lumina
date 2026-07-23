# 07 — 多租户隔离

> **前置要求**：已完成 [06-权限 RBAC](06-permission-rbac.md)
> **预计阅读**：25 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

Lumina 是**企业级多租户平台**——一套系统服务多家客户（租户）。**A 公司的数据，B 公司绝对不能看到。**

这节讲清 Lumina 怎么做到"业务代码完全无感"地隔离不同租户的数据。

---

## 多租户是什么？先建立直觉

### 类比：共享办公空间

一栋写字楼服务多家公司：
- **方案 A**：每家公司一层楼（独立数据库）——隔离好但成本高
- **方案 B**：所有公司在同一层，用办公桌上的**公司标签**区分（共享数据库 + tenant_id）——成本低

Lumina 用**方案 B**（共享数据库 + 行级隔离）——所有租户的数据在同一张表，用 `tenant_id` 列区分。

```
lumina_agent 表：
| agent_id | agent_name | tenant_id |
|----------|-----------|-----------|
| 1        | 助手A      | 1         |  ← 租户 1 的
| 2        | 助手B      | 1         |  ← 租户 1 的
| 3        | 助手X      | 2         |  ← 租户 2 的
| 4        | 助手Y      | 2         |  ← 租户 2 的
```

租户 1 的用户查询时，**只看到 tenant_id=1 的行**。租户 2 只看到 tenant_id=2 的行。

---

## 怎么做到"业务无感"

### 你写的代码（完全没提 tenant_id）

```java
LambdaQueryWrapper<AgentDO> wrapper = new LambdaQueryWrapper<>();
wrapper.like(AgentDO::getAgentName, "助手");
List<AgentDO> agents = agentMapper.selectList(wrapper);
```

### 实际执行的 SQL（自动加了租户条件）

```sql
SELECT * FROM lumina_agent
WHERE agent_name LIKE '%助手%'
  AND deleted = 0
  AND tenant_id = 1          ← ← MyBatis-Plus 拦截器自动加的！
```

**你完全没写 `tenant_id = 1`**——拦截器自动加的。这就是"业务无感"。

---

## 三个关键组件

### 1. BaseContext——存当前租户 ID

```java
// BaseContext 用 ThreadLocal 存当前请求的身份信息
public class BaseContext {
    private static final ThreadLocal<LoginContext> CONTEXT = new ThreadLocal<>();

    public static Long getTenantId() {
        return CONTEXT.get().getTenantId();
    }
}
```

JWT 过滤器解析 Token 后，把 tenant_id 注入 HTTP 头 → 租户拦截器从头读到 BaseContext。

### 2. TenantLineHandlerImpl——告诉拦截器当前租户是谁

```java
// 文件：lumina-business-base/.../handler/TenantLineHandlerImpl.java
@Override
public Expression getTenantId() {
    Long tenantId = BaseContext.getTenantId();   // ← 从 ThreadLocal 取
    if (tenantId == null) {
        throw new IllegalStateException("无法获取当前租户 ID");
    }
    return new LongValue(tenantId);    // 返回给 MyBatis-Plus，用于拼 SQL
}

@Override
public String getTenantIdColumn() {
    return "tenant_id";    // 列名
}

@Override
public boolean ignoreTable(String tableName) {
    // 哪些表不需要加 tenant_id（系统表、全局表）
    return ALWAYS_IGNORE.contains(tableName);
}
```

### 3. TenantLineInnerInterceptor——改写 SQL

这是 MyBatis-Plus 的拦截器，在 SQL 执行前**自动改写**：
- `SELECT` → 自动加 `WHERE tenant_id = ?`
- `INSERT` → 自动加 `tenant_id` 列
- `UPDATE` → 自动加 `WHERE tenant_id = ?`
- `DELETE` → 自动加 `WHERE tenant_id = ?`

> 📖 它怎么解析和改写 SQL 的？详见[第三阶 05-MyBatis 拦截器](../stage-3-mastery/05-mybatis-interceptor-internals.md)。

---

## 启动时自动检测哪些表有 tenant_id

```java
// 文件：TenantLineHandlerImpl.java
@PostConstruct
public void detectTenantTables() {
    // 查 information_schema，找出所有有 tenant_id 列的表
    List<String> tables = jdbcTemplate.queryForList(
        "SELECT table_name FROM information_schema.columns " +
        "WHERE column_name = 'tenant_id'", String.class);

    this.tenantTables = new HashSet<>(tables);
    // 检测失败 → fail-closed（拒绝启动，不是放行所有表）
}
```

**效果**：新建表时只要加了 `tenant_id` 列，拦截器**自动识别并隔离**——不用手动配置白名单。

> ⚠️ 这是 Lumina v3.1 的改进。之前是硬编码 `IGNORE_TABLES` 白名单，新建表容易忘记加导致安全漏洞。

---

## fail-closed 设计（安全关键）

```java
public boolean ignoreTable(String tableName) {
    // 如果表在"已知有 tenant_id"的列表里 → 不忽略（要隔离）
    if (tenantTables.contains(tableName)) {
        return false;
    }
    // 如果在显式忽略列表（系统表）→ 忽略
    if (ALWAYS_IGNORE.contains(tableName)) {
        return true;
    }
    // 未知表 → fail-closed：默认不忽略（当有 tenant_id 处理）
    return false;    // ← 宁可错杀不可放过
}
```

**fail-closed** = 遇到不确定的情况时**拒绝**而不是放行。如果一张新表不确定有没有 tenant_id，默认当作需要隔离——宁可查询失败（报错）也不能泄露数据。

---

## 顺序陷阱：多租户必须在分页之前

```java
// 文件：MybatisPlusTenantConfig.java
@Bean
public MybatisPlusInterceptor mybatisPlusInterceptor(...) {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

    // ⚠️ 多租户拦截器必须先加！
    interceptor.addInnerInterceptor(tenantInterceptor);

    // 分页拦截器后加
    interceptor.addInnerInterceptor(paginationInterceptor);

    return interceptor;
}
```

**为什么？** 分页拦截器会执行 `SELECT COUNT(*)` 重写，如果此时租户条件还没加进去，COUNT 的 WHERE 和实际查询不一致 → 参数索引错乱报错。Lumina 踩过这个坑，注释特意记录了。

---

## 跨租户访问的安全防线

```
HTTP 请求进来
  ↓
JWT 过滤器：解析 Token → 注入 X-Tenant-Id（来自 Token，不可伪造）
  ↓
租户拦截器：从头读 tenant_id → 写入 BaseContext
  ↓
MyBatis 拦截器：从 BaseContext 取 tenant_id → 改写 SQL
  ↓
数据库：只返回当前租户的数据
```

**安全关键**：tenant_id 来自 **JWT Token**（用户登录时签发的），不是前端传的参数。用户**不能自己改 tenant_id**——Token 里的 tenant_id 是签名保护的。

---

## 动手试试

1. **打开 `TenantLineHandlerImpl.java`**：看 `getTenantId()` 怎么从 BaseContext 取
2. **打开 `MybatisPlusTenantConfig.java`**：确认租户拦截器在分页拦截器之前
3. **在数据库里手动插入一条 tenant_id=999 的数据**：用 admin（tenant_id=0）登录，确认查不到那条数据

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| 共享数据库行级隔离 | 所有租户同一张表，用 tenant_id 列区分 |
| 业务无感 | MyBatis-Plus 拦截器自动加 WHERE tenant_id = ? |
| 自动检测 | @PostConstruct 查 information_schema 发现 tenant_id 列 |
| fail-closed | 未知表默认隔离（宁可报错不泄露） |
| 拦截器顺序 | 多租户 → 分页，顺序不能反 |
| 安全来源 | tenant_id 来自 JWT，不可伪造 |

---

## 下一步

下一篇 [JWT 认证](08-jwt-auth.md)——Token 怎么生成、校验、传递。

> 🚀 [08 — JWT 认证 →](08-jwt-auth.md)

---

## 自测题

1. **用户能通过修改请求参数来查看其他租户的数据吗？为什么？**
   <details><summary>答案</summary>不能。tenant_id 来自 JWT Token（签名保护），不是请求参数。用户改不了 Token 里的 tenant_id。</details>

2. **新建了一张表忘了加 tenant_id 列，会怎样？**
   <details><summary>答案</summary>自动检测不会把它加入隔离列表，查询时不加 tenant_id 条件。如果是业务表，会造成数据泄露。所以建表时该加 tenant_id 的一定要加。</details>

3. **为什么"多租户拦截器必须在分页拦截器之前"？**
   <details><summary>答案</summary>分页拦截器执行 COUNT(*) 重写时需要正确的 WHERE 条件。如果租户条件没加进去，COUNT 和实际数据的 WHERE 不一致，导致参数索引错乱。</details>

4. **fail-closed 是什么意思？为什么比 fail-open 安全？**
   <details><summary>答案</summary>fail-closed = 遇到不确定时拒绝（不放行）。比 fail-open 安全，因为宁可报错也不能泄露数据。代价是可能误拦合法请求。</details>

---

📝 **本篇撰写期间修正的代码**：无。
