# Lumina Flyway 迁移规范

## 核心原则

**写新迁移前必须先检查已有迁移的表结构、列名、数据约定。**

## 版本号规则

- 单调递增：V1, V2, ..., V25, V26...
- 不跳号、不复用、不修改已执行的迁移
- 文件命名：`V{版本号}__{简洁描述}.sql`（双下划线）
- 当前版本：V44（每次写新迁移前先 `ls db/migration/ | sort | tail -3` 确认最新版本号）

## 写迁移前的强制检查（不可跳过）

### 0. 查实际表结构（最高优先级，必须执行）

**写任何 INSERT/ALTER 前，必须先连数据库查 DESCRIBE，确认列名真实存在。**

```bash
# 查目标表的列名（替换表名）
"/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe" -uroot -p123456 -h127.0.0.1 lumina_dev \
  -e "DESCRIBE lumina_permission;"
```

或如果没有数据库连接，用 grep 从建表迁移中提取：

```bash
# 找到建表语句
grep -A 30 "CREATE TABLE.*lumina_permission" db/migration/V*.sql
```

**绝对禁止凭记忆/猜测写列名。** 历史上多次因此导致 CI 全量失败（V44 事件）。

### 1. 检查已有列名约定

### 2. 常见列名约定（必须遵守）

| 表名 | 常见易错列名 | 正确列名 |
|------|-------------|---------|
| `lumina_permission` | ❌ `name` `code` `id` `type` | ✅ `permission_name` `permission_code` `permission_id` `permission_type` |
| `lumina_permission` | ❌ `tenant_id`（此表无租户列） | ✅ 无 tenant_id |
| `lumina_permission` | 父权限码 ❌ `model` | ✅ `system:model`（带 system: 前缀） |
| `lumina_role` | ❌ `id` `name` | ✅ `role_id` `role_code` `role_name` |
| `lumina_role_permission` | ❌ `id` | ✅ `id`(自增) `role_id` `permission_id` |
| `lumina_user` | ❌ `id` `real_name` | ✅ `user_id` `nickname` |
| `lumina_model_pricing` | — | ✅ `provider` `model_name` `input_price` `output_price` `currency` `is_active` |
| 通用 | — | ✅ `create_time` / `update_time` / `deleted` / `tenant_id` |

### 3. 检查权限种子格式

权限种子 INSERT 必须用正确的列名和 `INSERT IGNORE` 防重复：

```sql
-- ✅ 正确格式（参考 V17/V25）
INSERT IGNORE INTO `lumina_permission` 
    (`parent_id`, `permission_code`, `permission_name`, `permission_type`, `path`, `icon`, `sort_order`)
VALUES (...);

-- 给 admin 角色分配权限
INSERT IGNORE INTO `lumina_role_permission` (`role_id`, `permission_id`)
SELECT 1, `permission_id` FROM `lumina_permission`
WHERE `permission_code` IN (...);
```

## 迁移内容规范

### CREATE TABLE
- `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`
- 必须有 `create_time` / `update_time` / `deleted`
- 索引命名：`idx_{columns}` 或 `uk_{columns}`（唯一索引）

### ALTER TABLE
- 每条 ALTER 一行，注释说明改了什么

### INSERT 种子数据
- 用 `INSERT IGNORE` 防重复执行报错
- 外键引用用子查询：`SET @parentId = LAST_INSERT_ID()` 或 `SELECT permission_id FROM ...`

## 持久化变更说明书（v3.14 批次 4.2 起，每个迁移必须附带）

Flyway 社区版没有 down migration——**回滚语义必须在写迁移时就设计好并写成文字**，
而不是失败后现场想。每个迁移文件头部注释块必须包含三行说明书：

```sql
-- 持久化变更说明书
-- 变更: <新增表 lumina_xxx | 新增列 xxx.yyy | 种子: N 条权限/字典/...>
-- 回滚: DROP TABLE lumina_xxx; | ALTER TABLE xxx DROP COLUMN yyy; | DELETE FROM xxx WHERE ...;
-- 影响: < ALWAYS_IGNORE 是否需加表 | 存量行默认值语义 | 是否锁表（大表 ALTER 需评估）>
```

规则：

1. **不可逆变更显式声明**：`DROP COLUMN`/`DROP TABLE`/改列类型的迁移，说明书必须写
   "不可逆——数据需先备份到 xxx"并给出备份语句
2. **种子数据的回滚以条件为准**：`DELETE ... WHERE permission_code IN (...)` 而非按自增 ID
3. **多表关联变更**：一个迁移动多张表时逐表列变更与回滚
4. **提交信息联动**：含迁移的 commit 描述中复述说明书摘要（评审者第一眼看到持久化影响）
5. **存量行语义**：新增 NOT NULL 列必须给 DEFAULT，说明书注明存量行的该列语义
   （如 instance_id NULL = 旧实例任务，见 ADR-002）

## 迁移失败的处理

如果迁移执行失败（Flyway 留下 `success = 0` 记录）：

```sql
-- 1. 删除失败记录
DELETE FROM flyway_schema_history WHERE version = '{版本号}';

-- 2. 清理可能创建了一半的表/数据
DROP TABLE IF EXISTS {半完成的表};
DELETE FROM {表} WHERE {条件};

-- 3. 修复 SQL 后重启服务
```

## 禁止事项

- ❌ 修改已执行的迁移文件（Flyway 会校验 checksum）
- ❌ 跳过版本号（V23 后直接写 V25）
- ❌ 不检查列名就写 INSERT（最常见的失败原因）
- ❌ DDL 不加 `IF NOT EXISTS` / `IF EXISTS` 保护
