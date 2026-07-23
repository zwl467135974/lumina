# 05 — MyBatis 拦截器原理（多租户 SQL 改写底层）

> **前置要求**：已完成 [04-事务传播](04-transaction-propagation.md)
> **预计阅读**：25 分钟
> **难度**：⭐⭐⭐⭐⭐

---

## 面试题引入

> **"面试官：MyBatis-Plus 的多租户是怎么自动给 SQL 加 WHERE tenant_id = ? 的？拦截器的原理是什么？"**

---

## 表层回答（60 分）

MyBatis-Plus 注册了 `TenantLineInnerInterceptor`，在 SQL 执行前用 JSqlParser 解析 SQL，自动在 WHERE 条件里追加 `tenant_id = ?`。

---

## 深层原理（90 分）

### MyBatis 拦截器责任链

```
你的代码: agentMapper.selectList(wrapper)
  ↓
MybatisPlusInterceptor（拦截器链）
  ├── TenantLineInnerInterceptor  ← 多租户改写
  └── PaginationInnerInterceptor  ← 分页改写
  ↓
解析 SQL → 改写 SQL → 执行改写后的 SQL
  ↓
MySQL
```

### SQL 改写过程（JSqlParser）

```
原始 SQL（你写的 Wrapper 生成的）:
  SELECT * FROM lumina_agent WHERE deleted = 0 AND agent_name LIKE '%助手%'

JSqlParser 解析成 AST（抽象语法树）:
  SelectStatement
    ├── columns: *
    ├── table: lumina_agent
    └── where: deleted = 0 AND agent_name LIKE '%助手%'

拦截器遍历 AST：
  发现表 lumina_agent 在"有 tenant_id 列"的列表里
  → 在 where 里追加: AND tenant_id = 1

改写后 SQL:
  SELECT * FROM lumina_agent WHERE deleted = 0 AND agent_name LIKE '%助手%' AND tenant_id = 1

执行改写后的 SQL
```

### INSERT 怎么改写

```
原始 SQL:
  INSERT INTO lumina_agent (agent_name, agent_type) VALUES (?, ?)

改写后:
  INSERT INTO lumina_agent (agent_name, agent_type, tenant_id) VALUES (?, ?, 1)
                                   ↑ 自动加列名                 ↑ 自动加值
```

---

## TenantLineHandler 的三个核心方法

```java
// 文件：TenantLineHandlerImpl.java
@Override
public Expression getTenantId() {
    Long tenantId = BaseContext.getTenantId();    // ← 从 ThreadLocal 取当前租户
    if (tenantId == null) {
        throw new IllegalStateException("无法获取租户 ID");    // fail-closed
    }
    return new LongValue(tenantId);
}

@Override
public String getTenantIdColumn() {
    return "tenant_id";    // 列名
}

@Override
public boolean ignoreTable(String tableName) {
    // 哪些表不加 tenant_id
    if (ALWAYS_IGNORE.contains(tableName)) return true;     // 系统表
    if (tenantTables.contains(tableName)) return false;     // 确认有 tenant_id 的表
    return false;    // fail-closed：未知表默认隔离
}
```

---

## 启动时自动检测哪些表有 tenant_id

```java
// 文件：TenantLineHandlerImpl.java:54-70
@PostConstruct
public void detectTenantTables() {
    // 查 information_schema
    List<String> tables = jdbcTemplate.queryForList(
        "SELECT table_name FROM information_schema.columns " +
        "WHERE column_name = 'tenant_id' AND table_schema = DATABASE()",
        String.class
    );
    this.tenantTables = new HashSet<>(tables);
    log.info("检测到 {} 张含 tenant_id 的表", tables.size());
}
```

**效果**：新建表只要加了 `tenant_id` 列，自动被检测、自动被隔离——不用手动维护白名单。

---

## 拦截器顺序为什么重要

```java
// 文件：MybatisPlusTenantConfig.java:28-29
// 注释：多租户 → 分页，顺序不能反
interceptor.addInnerInterceptor(tenantInterceptor);    // 先加多租户
interceptor.addInnerInterceptor(paginationInterceptor); // 后加分页
```

**为什么**：分页拦截器执行 `SELECT COUNT(*)` 重写。如果此时 tenant_id 还没加进去，COUNT 的 WHERE 和实际查询不一致 → 参数索引错乱（`Parameter index out of range`）。

---

## 常见追问

### Q：JSqlParser 是什么？

**A**：一个 Java 的 SQL 解析库。把 SQL 字符串解析成 AST（抽象语法树），可以遍历修改 AST 再重新生成 SQL。MyBatis-Plus 用它来改写 SQL。

### Q：ignoreTable 返回 false 但表没有 tenant_id 列会怎样？

**A**：SQL 执行报错（`Unknown column 'tenant_id'`）。这就是 fail-closed——宁可报错也不能放行。

### Q：原生 SQL（@Select 写的）也会被改写吗？

**A**：会。只要表名在 tenantTables 里，不管 SQL 是 Wrapper 生成的还是 @Select 手写的，拦截器都会改写。

---

## 小结

| 知识点 | 一句话 |
|--------|--------|
| 原理 | JSqlParser 解析 SQL → 遍历 AST → 追加 tenant_id 条件 |
| SELECT 改写 | WHERE 追加 `AND tenant_id = ?` |
| INSERT 改写 | 列名和 VALUES 都追加 tenant_id |
| 自动检测 | @PostConstruct 查 information_schema |
| 顺序 | 多租户 → 分页，反了会报错 |
| fail-closed | 未知表默认隔离（宁报错不放行） |

---

📝 **本篇撰写期间修正的代码**：无。
