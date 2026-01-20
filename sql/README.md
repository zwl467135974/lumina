# Lumina 项目 SQL 脚本说明

## 📌 统一管理说明

**本项目采用统一 SQL 管理方案，所有模块的 SQL 脚本都集中在本目录下。**

- ✅ 所有建表脚本统一在 `01_create_tables.sql`
- ✅ 所有初始化数据统一在 `02_init_data.sql`
- ✅ 所有迁移脚本统一在 `03_migration.sql`
- ❌ 模块内的 SQL 文件已删除，请勿使用

## 📁 文件结构

```
sql/
├── 01_create_tables.sql      # 建表脚本（所有核心业务表）
├── 02_init_data.sql          # 初始化数据（租户、角色、权限、管理员用户）
├── 03_migration.sql          # 数据迁移脚本（从旧版本迁移）
└── README.md                 # 本说明文件
```

## 📦 包含的模块

### Base 模块表
- `lumina_user` - 用户表
- `lumina_tenant` - 租户表
- `lumina_role` - 角色表
- `lumina_permission` - 权限表
- `lumina_user_role` - 用户角色关联表
- `lumina_role_permission` - 角色权限关联表

### Agent 模块表
- `lumina_agent` - Agent 表

## 🚀 使用方法

### 首次安装（全新数据库）

**步骤 1：创建数据库**
```bash
mysql -u root -p -e "CREATE DATABASE lumina_dev DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

**步骤 2：执行建表脚本**
```bash
mysql -u root -p lumina_dev < sql/01_create_tables.sql
```

**步骤 3：执行初始化数据脚本**
```bash
mysql -u root -p lumina_dev < sql/02_init_data.sql
```

**完成！** 系统已准备就绪，可以使用以下账号登录：
- 用户名：`admin`
- 密码：`admin123`
- 租户：SYSTEM（系统租户）
- 角色：超级管理员

### 从旧版本升级（已有数据库）

**步骤 1：执行建表脚本**
```bash
mysql -u root -p lumina_dev < sql/01_create_tables.sql
```

**步骤 2：执行数据迁移脚本**
```bash
mysql -u root -p lumina_dev < sql/03_migration.sql
```

**注意**：迁移脚本会自动处理以下内容：
- ✅ 添加 tenant_id 字段到 lumina_user 表
- ✅ 更新现有用户的租户 ID
- ✅ 为现有用户分配默认角色
- ✅ 删除旧的 role 字段
- ✅ 创建必要的索引

## 📊 数据库表结构

### Base 模块表（用户、角色、权限、租户）

| 表名 | 说明 | 关键字段 |
|------|------|---------|
| `lumina_user` | 用户表 | tenant_id, username, password, status |
| `lumina_tenant` | 租户表 | tenant_code, tenant_name, status |
| `lumina_role` | 角色表 | tenant_id, role_code, role_name, status |
| `lumina_permission` | 权限表（树形） | permission_code, permission_name, parent_id, permission_type |
| `lumina_user_role` | 用户-角色关联 | user_id, role_id |
| `lumina_role_permission` | 角色-权限关联 | role_id, permission_id |

### Agent 模块表

| 表名 | 说明 | 关键字段 |
|------|------|---------|
| `lumina_agent` | Agent 表 | agent_name, agent_type, description, status |

## 🔐 默认账号

### 系统管理员

- **用户名**：`admin`
- **密码**：`admin123`
- **租户**：SYSTEM（系统租户，tenant_id=0）
- **角色**：SUPER_ADMIN（超级管理员）
- **权限**：所有权限

## ⚠️ 重要说明

### 安全建议

1. **修改默认密码**
   ```sql
   -- 生成新的 BCrypt 密码（例如：newPassword123）
   UPDATE lumina_user
   SET password = '$2a$12$新的BCrypt哈希'
   WHERE username = 'admin';
   ```

2. **生产环境配置**
   - 修改 JWT 密钥（在 application.yml 中配置）
   - 启用 HTTPS
   - 配置防火墙规则

### 多租户说明

- **系统租户**（tenant_id=0）：用于系统管理员，可以管理所有租户
- **普通租户**（tenant_id>0）：每个租户的数据完全隔离
- **租户内用户名唯一**：同一个租户内用户名不能重复，不同租户可以有相同的用户名

### 角色说明

| 角色编码 | 角色名称 | tenant_id | 说明 |
|---------|---------|-----------|------|
| SUPER_ADMIN | 超级管理员 | 0 | 拥有所有权限 |
| SYSTEM_ADMIN | 系统管理员 | 0 | 系统运维角色 |
| TENANT_ADMIN | 租户管理员 | >0 | 管理本租户用户、角色 |
| TENANT_USER | 普通用户 | >0 | 默认角色 |

## 🧪 测试 SQL

### 查看初始化数据

```sql
-- 查看租户
SELECT * FROM lumina_tenant;

-- 查看角色
SELECT * FROM lumina_role;

-- 查看权限
SELECT * FROM lumina_permission ORDER BY parent_id, sort_order;

-- 查看用户
SELECT user_id, tenant_id, username, real_name, status FROM lumina_user;

-- 查看用户角色关联
SELECT ur.*, u.username, r.role_name
FROM lumina_user_role ur
JOIN lumina_user u ON ur.user_id = u.user_id
JOIN lumina_role r ON ur.role_id = r.role_id;
```

### 验证租户隔离

```sql
-- 验证用户名在租户内唯一
SELECT tenant_id, username, COUNT(*) as count
FROM lumina_user
GROUP BY tenant_id, username
HAVING count > 1;
-- 应该返回空，表示没有重复

-- 查看每个租户的用户数量
SELECT tenant_id, COUNT(*) as user_count
FROM lumina_user
GROUP BY tenant_id;
```

## 📝 版本历史

- **v1.0.0** (2025-01-20)：初始版本
  - 创建 Base 模块表结构
  - 支持多租户
  - 支持角色权限体系
  - 添加默认管理员账号

## 🆘 常见问题

### Q1：执行迁移脚本时报错 "Column already exists"

**A**：这是正常的，脚本已经考虑了这种情况。使用了 `IF NOT EXISTS` 和 `ON DUPLICATE KEY UPDATE` 确保脚本可重复执行。

### Q2：忘记管理员密码怎么办？

**A**：可以重置密码为新的 BCrypt 哈希值
```sql
-- 重置为 admin123
UPDATE lumina_user
SET password = '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5NU9XKaQUeAqn.'
WHERE username = 'admin';
```

### Q3：如何创建新租户？

**A**：通过 API 接口或直接插入数据
```sql
INSERT INTO lumina_tenant (tenant_code, tenant_name, contact_name, status)
VALUES ('NEW_TENANT', '新租户', '张三', 1);
```

### Q4：如何创建新用户？

**A**：通过 Base 服务的 API 接口创建
```
POST /api/v1/base/users
{
  "username": "newuser",
  "password": "password123",
  "realName": "新用户",
  "tenantId": 1
}
```

## 📞 技术支持

如有问题，请查看项目文档或提交 Issue。
