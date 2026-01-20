# Lumina Business Base 模块测试指南

## 📋 测试前准备

### 1. 环境检查

确保以下服务已启动：
- [x] MySQL 数据服务（localhost:3306）
- [x] Nacos 服务（localhost:8848）
- [ ] Redis 服务（localhost:6379）- 可选，用于限流

### 2. 数据库初始化

**步骤 1：创建数据库**
```bash
mysql -u root -p -e "CREATE DATABASE lumina_dev DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

**步骤 2：执行建表脚本**
```bash
cd G:\Individual\ work\workHub\lumina
mysql -u root -p lumina_dev < sql/01_create_tables.sql
```

预期输出：
```
Table creation completed.
```

**步骤 3：执行初始化数据脚本**
```bash
mysql -u root -p lumina_dev < sql/02_init_data.sql
```

预期输出：
```
============================================
Lumina 数据库初始化完成
============================================
租户数量：2
角色数量：4
权限数量：18
用户数量：1
============================================
默认管理员账号：
用户名：admin
密码：admin123
租户：SYSTEM（系统租户）
角色：SUPER_ADMIN（超级管理员）
============================================
```

**验证数据库初始化**
```sql
-- 验证表是否创建成功
USE lumina_dev;
SHOW TABLES;

-- 验证初始数据
SELECT * FROM lumina_tenant;
SELECT * FROM lumina_role;
SELECT * FROM lumina_permission ORDER BY parent_id, sort_order;
SELECT * FROM lumina_user;
SELECT * FROM lumina_user_role;
```

### 3. 启动 Base 服务

**方式 1：IDE 启动**
1. 打开 IDEA
2. 找到 `LuminaBaseApplication.java`
3. 右键 → Run 'LuminaBaseApplication'

**方式 2：命令行启动**
```bash
cd lumina-modules/lumina-business-base
mvn spring-boot:run
```

**验证服务启动**
查看控制台输出，应该看到：
```
Started LuminaBaseApplication in X.XXX seconds
Tomcat started on port(s): 8082 (http)
```

**健康检查**
```bash
curl http://localhost:8082/actuator/health
```

预期返回：
```json
{"status":"UP"}
```

### 4. 启动 Gateway 服务

```bash
cd lumina-gateway
mvn spring-boot:run
```

**验证 Gateway 启动**
```bash
curl http://localhost:8080/actuator/health
```

---

## 🧪 测试场景

### 场景 1：超级管理员登录

**测试目标**：验证超级管理员（系统租户）可以正常登录

**测试步骤**：

1. **通过 Gateway 登录**
```bash
curl -X POST http://localhost:8080/api/v1/base/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "admin123",
    "tenantId": 0
  }'
```

2. **直接访问 Base 服务登录**
```bash
curl -X POST http://localhost:8082/api/v1/base/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "admin123",
    "tenantId": 0
  }'
```

**预期返回**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "userId": 1,
    "username": "admin",
    "tenantId": 0,
    "realName": "系统管理员",
    "roles": ["SUPER_ADMIN"],
    "permissions": [
      "system",
      "system:tenant",
      "system:tenant:list",
      "system:tenant:create",
      ...
    ],
    "expiration": 1737331200000
  }
}
```

**验证点**：
- [x] 返回 200 状态码
- [x] token 字段非空
- [x] tenantId = 0（系统租户）
- [x] roles 包含 "SUPER_ADMIN"
- [x] permissions 包含所有系统权限（约 18 个）

**常见错误**：
- `401 Unauthorized`：密码错误或用户不存在
- `500 Internal Server Error`：数据库连接失败或 Mapper 方法缺失

---

### 场景 2：JWT Token 验证

**测试目标**：验证 JWT Token 生成和解析正确

**测试步骤**：

1. **复制上一步返回的 token**

2. **解析 Token 内容**
```bash
# 使用 jwt.io 或 jwt-cli 解码
echo "eyJhbGciOiJIUzI1NiJ9..." | jwt decode
```

3. **使用 Token 获取用户信息**
```bash
curl -X GET http://localhost:8080/api/v1/base/auth/user-info \
  -H "Authorization: Bearer <你的token>"
```

**预期返回**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "userId": 1,
    "username": "admin",
    "tenantId": 0,
    "roles": ["SUPER_ADMIN"],
    "permissions": ["system", "system:tenant", ...]
  }
}
```

**验证点**：
- [x] Token 解码后包含 tenantId、roles、permissions
- [x] roles 是数组类型
- [x] permissions 是数组类型

---

### 场景 3：Gateway Header 传递测试

**测试目标**：验证 Gateway 正确传递用户信息到下游服务

**测试步骤**：

1. **修改 AuthServiceImpl 添加调试日志**

在 `AuthServiceImpl.java` 的 `login` 方法中添加：
```java
// 打印请求头（用于测试）
log.info("X-User-Id: {}", request.getHeader("X-User-Id"));
log.info("X-Username: {}", request.getHeader("X-Username"));
log.info("X-Tenant-Id: {}", request.getHeader("X-Tenant-Id"));
log.info("X-Roles: {}", request.getHeader("X-Roles"));
log.info("X-Permissions: {}", request.getHeader("X-Permissions"));
```

2. **通过 Gateway 发送请求**

3. **查看 Base 服务日志**

**预期日志**：
```
X-User-Id: 1
X-Username: admin
X-Tenant-Id: 0
X-Roles: SUPER_ADMIN
X-Permissions: system,system:tenant,system:tenant:list,...
```

**验证点**：
- [x] 所有 Header 都正确传递
- [x] X-Tenant-Id = 0
- [x] X-Roles 包含 SUPER_ADMIN
- [x] X-Permissions 包含所有权限（逗号分隔）

---

### 场景 4：租户隔离测试

**测试目标**：验证不同租户的数据隔离

**前置条件**：创建租户 A 的用户

**步骤 1：创建租户**
```sql
INSERT INTO lumina_tenant (tenant_code, tenant_name, status)
VALUES ('TENANT_A', '租户A', 1);
```

**步骤 2：为租户 A 创建角色**
```sql
INSERT INTO lumina_role (tenant_id, role_code, role_name, sort_order)
VALUES (1, 'TENANT_A_ADMIN', '租户A管理员', 1);

-- 分配用户管理权限
INSERT INTO lumina_role_permission (role_id, permission_id)
SELECT 5, permission_id FROM lumina_permission
WHERE permission_code LIKE 'system:user%';
```

**步骤 3：创建租户 A 的用户**
```sql
INSERT INTO lumina_user (tenant_id, username, password, real_name, status)
VALUES (1, 'tenant_admin',
'$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5NU9XKaQUeAqn.',
'租户A管理员', 1);

-- 分配角色
INSERT INTO lumina_user_role (user_id, role_id)
VALUES (2, 5);
```

**步骤 4：测试租户 A 用户登录**
```bash
curl -X POST http://localhost:8080/api/v1/base/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "tenant_admin",
    "password": "admin123",
    "tenantId": 1
  }'
```

**预期返回**：
```json
{
  "data": {
    "userId": 2,
    "username": "tenant_admin",
    "tenantId": 1,
    "roles": ["TENANT_A_ADMIN"],
    "permissions": ["system:user", "system:user:list", ...]
  }
}
```

**验证点**：
- [x] tenantId = 1（租户 A）
- [x] roles 只包含租户 A 的角色
- [x] permissions 只包含用户管理相关权限
- [x] 不包含租户管理权限（system:tenant:*）

**步骤 5：验证租户隔离**
```sql
-- 验证用户名在租户内唯一
SELECT tenant_id, username, COUNT(*) as count
FROM lumina_user
WHERE deleted = 0
GROUP BY tenant_id, username
HAVING count > 1;
-- 应该返回空，表示没有重复

-- 查看每个租户的用户数量
SELECT tenant_id, COUNT(*) as user_count
FROM lumina_user
WHERE deleted = 0
GROUP BY tenant_id;
```

---

### 场景 5：旧路径兼容性测试

**测试目标**：验证旧路径 `/api/v1/auth/**` 可以正常工作

**测试步骤**：

1. **使用旧路径登录**
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "admin123"
  }'
```

2. **验证请求被重写**
查看 Gateway 日志，应该看到：
```
Path rewritten from /api/v1/auth/login to /api/v1/base/auth/login
```

**预期返回**：
与场景 1 相同的登录响应

**验证点**：
- [x] 旧路径返回 200 状态码
- [x] 成功返回 token
- [x] Gateway 日志显示路径重写

---

### 场景 6：角色状态验证测试

**测试目标**：验证禁用角色的用户无法登录

**测试步骤**：

1. **禁用 admin 用户的角色**
```sql
UPDATE lumina_role SET status = 0 WHERE role_code = 'SUPER_ADMIN';
```

2. **尝试登录**
```bash
curl -X POST http://localhost:8080/api/v1/base/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "admin123",
    "tenantId": 0
  }'
```

**预期返回**：
```json
{
  "code": 500,
  "message": "用户没有有效的角色"
}
```

3. **恢复角色状态**
```sql
UPDATE lumina_role SET status = 1 WHERE role_code = 'SUPER_ADMIN';
```

**验证点**：
- [x] 禁用角色后无法登录
- [x] 错误提示"用户没有有效的角色"
- [x] 恢复后可以正常登录

---

### 场景 7：密码错误测试

**测试步骤**：

```bash
curl -X POST http://localhost:8080/api/v1/base/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "wrong_password",
    "tenantId": 0
  }'
```

**预期返回**：
```json
{
  "code": 500,
  "message": "用户名或密码错误"
}
```

---

## 📊 性能测试

### 压力测试

使用 Apache Bench (ab) 进行简单的压力测试：

```bash
# 安装 ab 工具（如果未安装）
# Windows: 下载 Apache HTTP Server
# Linux: sudo apt-get install apache2-utils

# 测试登录接口（并发 100，总请求 1000）
ab -n 1000 -c 100 -p login.json -T application/json \
  http://localhost:8080/api/v1/base/auth/login
```

**login.json 文件内容**：
```json
{
  "username": "admin",
  "password": "admin123",
  "tenantId": 0
}
```

**预期结果**：
- 成功率 > 99%
- 平均响应时间 < 200ms
- 无 500 错误

---

## 🔍 故障排查

### 问题 1：服务启动失败

**症状**：
```
Failed to configure a DataSource: 'url' attribute is not specified
```

**解决方案**：
1. 检查 `application.yml` 中的数据库配置
2. 确认 MySQL 服务已启动
3. 验证数据库连接信息（用户名、密码、数据库名）

### 问题 2：Mapper 方法找不到

**症状**：
```
org.apache.ibatis.binding.BindingException: Invalid bound statement
```

**解决方案**：
1. 检查 `@Mapper` 注解是否添加
2. 检查方法签名是否正确
3. 确认 MyBatis-Plus 扫描路径配置

### 问题 3：JWT Token 解析失败

**症状**：
```
无效的 Token
```

**解决方案**：
1. 检查 SECRET_KEY 是否一致
2. 验证 Token 是否过期
3. 确认 Claims 类型转换正确（String[] vs Collection）

### 问题 4：Gateway 401 错误

**症状**：通过 Gateway 访问接口时返回 401

**解决方案**：
1. 检查 Authorization header 格式（必须为 "Bearer xxx"）
2. 验证 Token 是否有效
3. 确认请求路径不在白名单中

---

## ✅ 测试检查清单

### 基础功能
- [ ] 数据库表创建成功
- [ ] 初始数据插入成功
- [ ] Base 服务启动成功
- [ ] Gateway 服务启动成功

### 登录功能
- [ ] 超级管理员登录成功
- [ ] 租户管理员登录成功
- [ ] 密码错误返回正确错误信息
- [ ] 用户不存在返回正确错误信息
- [ ] 角色禁用后无法登录

### JWT 功能
- [ ] Token 生成成功
- [ ] Token 解析成功
- [ ] Token 包含 tenantId
- [ ] Token 包含 roles 数组
- [ ] Token 包含 permissions 数组

### Gateway 集成
- [ ] Gateway 正确路由到 Base 服务
- [ ] JWT 认证过滤器工作正常
- [ ] 用户信息通过 Header 传递
- [ ] 旧路径兼容性正常

### 租户隔离
- [ ] 用户数据按租户隔离
- [ ] 角色数据按租户隔离
- [ ] 权限正确分配
- [ ] 跨租户访问被阻止

---

## 📝 测试报告模板

```markdown
## Lumina Business Base 测试报告

**测试日期**：2025-01-20
**测试人员**：[姓名]
**环境**：Windows / MySQL 8.0 / Nacos 2.x

### 测试结果汇总

| 测试场景 | 测试结果 | 备注 |
|---------|---------|------|
| 环境准备 | ✅ 通过 | - |
| 超级管理员登录 | ✅ 通过 | - |
| JWT Token 验证 | ✅ 通过 | - |
| Gateway Header 传递 | ✅ 通过 | - |
| 租户隔离测试 | ✅ 通过 | - |
| 旧路径兼容性 | ✅ 通过 | - |
| 角色状态验证 | ✅ 通过 | - |

### 发现的问题

1. [问题描述]
   - 严重程度：高/中/低
   - 复现步骤：...
   - 预期结果：...
   - 实际结果：...

### 建议

1. [改进建议]
...
```

---

## 🎯 下一步

测试通过后，可以进行以下操作：

1. **开发用户管理接口**（UserController）
2. **开发角色管理接口**（RoleController）
3. **开发权限管理接口**（PermissionController）
4. **开发租户管理接口**（TenantController）
5. **实现 BaseContext 工具类**（ThreadLocal 存储用户上下文）
6. **实现权限注解**（@RequirePermission）
7. **添加 Redis 缓存**（缓存用户权限信息）

---

**祝测试顺利！如有问题，请查看日志或联系开发团队。**
