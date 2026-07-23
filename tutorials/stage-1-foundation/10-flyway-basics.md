# 10 — Flyway 数据库迁移

> **前置要求**：已完成 [09-Redis 在 Lumina](09-redis-in-lumina.md)
> **预计阅读**：15 分钟
> **难度**：⭐⭐☆☆☆

---

## 这节解决什么问题

Lumina 有 44 张数据库表。这些表是怎么来的？手动在 MySQL 里敲 CREATE TABLE 吗？当然不是——那样没法追踪变更，团队协作会乱套。

Lumina 用 **Flyway** 把数据库表结构的变更变成**版本化的 SQL 脚本**，像管代码一样管数据库。这节讲清 Flyway 的基本用法。

---

## Flyway 是什么？先建立直觉

### 类比：房屋装修的施工日志

装修不是一次性完成的——先铺地板，再刷墙，再装灯……每一步都记录在施工日志里。新来的工人看日志就知道房子是怎么一步步装修成现在这样的。

**Flyway = 数据库的施工日志**。每次表结构变更（建表、加列、改索引、灌种子数据）写一个 SQL 脚本，Flyway 按版本号顺序执行，并记录"哪些已经执行过了"。

### 没有 Flyway 的问题

```
开发 A：手动加了一列 age
开发 B：在 A 之前加了一列 email，但没告诉 A
开发 C：新拉代码，不知道数据库要怎么建
       → 每个开发都在群里问"数据库怎么初始化"
       → 生产环境漏了一列，上线就报错
```

### 有 Flyway 的世界

```
开发 A：写 V43__add_rate_limit.sql
开发 B：写 V44__add_model_pricing.sql
开发 C：新拉代码，启动项目
       → Flyway 自动检查哪些没执行过，自动执行
       → 数据库自动变成最新状态，无需手动操作
```

---

## Flyway 的命名规则

```
db/migration/
├── V1__init_schema.sql           ← V + 版本号 + 双下划线 + 描述
├── V2__init_data.sql
├── V4__add_audit_log_table.sql
├── ...
├── V43__add_agent_rate_limit_and_concurrency.sql
└── V44__add_glm_model_pricing.sql
```

### 命名规则（重要！）

```
V{版本号}__{描述}.sql
```

| 部分 | 规则 | 示例 |
|------|------|------|
| `V` | 固定前缀，表示 Versioned migration | `V` |
| `{版本号}` | 数字，用点或下划线分隔 | `1` / `1.1` / `43` |
| `__` | **两个下划线**（不是 1 个！） | `__` |
| `{描述}` | 人类可读的描述 | `add_audit_log_table` |
| `.sql` | 文件后缀 | `.sql` |

> ⚠️ **最常见的坑**：用了一个下划线 `_` 而不是两个 `__`，Flyway 不识别。

### 执行顺序

Flyway 按版本号大小顺序执行：
```
V1 → V2 → V4 → V10 → V43 → V44
```

> 💡 版本号不连续没关系（V1 后面跳到 V4 也没事），Flyway 按数值排序。

---

## Flyway 怎么追踪执行状态

Flyway 在数据库里自动创建一张元数据表 `flyway_schema_history`，记录每个脚本是否执行过：

```
| version | description          | success | installed_on         |
|---------|----------------------|---------|----------------------|
| 1       | init schema          | true    | 2026-07-01 10:00:00  |
| 2       | init data            | true    | 2026-07-01 10:00:01  |
| 4       | add audit log table  | true    | 2026-07-02 14:30:00  |
| ...     | ...                  | ...     | ...                  |
| 44      | add glm model pricing| true    | 2026-07-22 09:15:00  |
```

**启动时**，Flyway 对比"代码里的脚本"和"表里的记录"，只执行还没跑过的脚本。

---

## Flyway 配置

```yaml
# 文件：lumina-standalone/src/main/resources/application.yml（第 72 行起）
spring:
  flyway:
    enabled: true                         # 启用 Flyway
    baseline-on-migrate: true             # 已有数据的库也能用（不报错）
    baseline-version: 1                   # 基线版本
    locations: classpath:db/migration     # 脚本目录
    encoding: UTF-8                       # 编码
```

> 💡 `baseline-on-migrate: true` 意味着：即使数据库已经存在一些表（不是 Flyway 建的），Flyway 也不会报错，而是把当前状态当作"基线"，后续脚本继续执行。

---

## 在 Lumina 里长啥样：一个典型迁移脚本

```sql
-- 文件：lumina-modules/lumina-business-base/.../db/migration/V4__add_audit_log_table.sql

-- ========================================
-- Lumina 审计日志表
-- 版本：1.1.0
-- 说明：记录用户/角色/权限/租户/Agent 等关键操作的审计日志，
--       由 @Audit 注解 + AuditAspect 切面自动采集。
-- ========================================

CREATE TABLE IF NOT EXISTS `lumina_audit_log` (
    `audit_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '审计ID',
    `tenant_id` BIGINT DEFAULT NULL COMMENT '租户ID',
    `user_id` BIGINT DEFAULT NULL COMMENT '操作人ID',
    `username` VARCHAR(50) DEFAULT NULL COMMENT '操作人用户名',
    `module` VARCHAR(50) NOT NULL COMMENT '业务模块',
    `action` VARCHAR(50) NOT NULL COMMENT '操作类型',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '描述',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态（0-失败，1-成功）',
    `duration_ms` BIGINT DEFAULT NULL COMMENT '耗时（毫秒）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`audit_id`),
    KEY `idx_tenant_user` (`tenant_id`, `user_id`),       -- 索引
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计日志表';
```

### 一个好的迁移脚本应该有什么

1. **头部注释**：版本号、说明（为什么加这个表）
2. `CREATE TABLE IF NOT EXISTS`：幂等（执行多次不报错）
3. 每列加 `COMMENT`：自解释
4. 合理的索引：查询常用字段加索引
5. 字符集 `utf8mb4`：支持 emoji 和特殊字符

---

## Flyway 脚本不只是建表

有些脚本做三件事——建表 + 灌权限种子 + 给角色授权：

```sql
-- 简化示意（V23 的模式）
-- 1. 建表
CREATE TABLE lumina_llm_provider (...);

-- 2. 灌权限种子
INSERT INTO lumina_permission (permission_code, permission_name) VALUES
    ('model:list', '模型列表'),
    ('model:create', '创建模型');

-- 3. 给 admin 角色授权
INSERT INTO lumina_role_permission (role_id, permission_id) VALUES
    (1, (SELECT permission_id FROM lumina_permission WHERE permission_code = 'model:list'));
```

**一次迁移把表结构和初始数据一起搞定**——启动即用。

---

## ⚠️ Flyway 的铁律

1. **已发布的迁移脚本不可修改！**——V1-V44 已经在生产执行过，改了会导致校验和（checksum）不匹配，启动报错。要改结构就写新的 V45。

2. **版本号不能重复**——两个 V44 会让 Flyway 困惑。

3. **写 SQL 前先 DESCRIBE 实际表**——别凭记忆写列名。Lumina 有专门的技能包 `lumina_flyway` 强制这个约定。

> 📖 Flyway 的完整规范和坑（checksum 不匹配怎么修、out-of-order 迁移）见 `.agents/skills/lumina_flyway/SKILL.md`。

---

## 动手试试

1. **打开迁移目录**：`lumina-modules/lumina-business-base/src/main/resources/db/migration/`
2. **数数有多少个 SQL 文件**（应该是 44 个）
3. **打开 `V1__init_schema.sql`**：看看初始建了哪些核心表（user/role/permission/tenant）
4. **打开 `V44__add_glm_model_pricing.sql`**：看看最新的迁移做了什么

---

## 小结

| 你现在应该知道 | 一句话记忆 |
|---------------|-----------|
| Flyway 是什么 | 数据库的版本化施工日志 |
| 命名规则 | `V版本号__描述.sql`（双下划线！） |
| 怎么追踪 | flyway_schema_history 表记录已执行版本 |
| 执行时机 | 项目启动时自动执行未跑过的脚本 |
| 铁律 | 已发布的脚本不可改，要改写新版本 |

---

## 后端基础全部完成！

恭喜你！后端基础技术栈（Maven + Spring Boot + MyBatis-Plus + Redis + Flyway）全部学完了。

接下来进入**前端基础**（11-17 篇）——Vue 3、Element Plus、Pinia、TypeScript、Axios/SSE。

> 🚀 **现在继续**：[11 — Vue 3 基础 →](11-vue3-basics.md)

---

## 自测题

1. **为什么 Flyway 脚本命名要用两个下划线 `__` 而不是一个？**
   <details><summary>答案</summary>这是 Flyway 的约定。一个下划线会被 Flyway 忽略（不识别为迁移脚本），导致脚本不会被执行。</details>

2. **已发布的 V1 脚本有个错字，你能直接改它吗？**
   <details><summary>答案</summary>不能！改了会导致 checksum 不匹配，Flyway 启动报错。正确做法是写一个新的迁移脚本（如 V45）修正。</details>

3. **新拉了代码，启动项目时 Flyway 会把所有 44 个脚本重新执行一遍吗？**
   <details><summary>答案</summary>不会。Flyway 检查 flyway_schema_history 表，只执行还没记录过的脚本。之前执行过的不会重复执行。</details>

4. **迁移脚本除了建表还能做什么？**
   <details><summary>答案</summary>能做任何 SQL 操作：加列（ALTER TABLE）、灌种子数据（INSERT）、给角色授权、加索引等。一次迁移可以做多件事。</details>

---

📝 **本篇撰写期间修正的代码**：无。
