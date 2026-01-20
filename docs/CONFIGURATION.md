# Lumina 配置说明文档

## 📋 目录

1. [JWT 配置](#jwt-配置)
2. [白名单配置](#白名单配置)
3. [租户隔离配置](#租户隔离配置)
4. [数据库配置](#数据库配置)
5. [服务端口配置](#服务端口配置)
6. [Nacos 配置](#nacos-配置)

---

## JWT 配置

### 配置项说明

JWT（JSON Web Token）用于用户认证和授权，支持密钥和过期时间配置。

### 配置位置

- **Gateway**: `lumina-gateway/src/main/resources/application.yml`
- **Base 服务**: `lumina-modules/lumina-business-base/src/main/resources/application.yml`

### 配置示例

```yaml
lumina:
  jwt:
    # JWT 密钥（生产环境必须修改为复杂的随机字符串）
    secret-key: lumina-secret-key-for-jwt-token-generation-must-be-long-enough
    # Token 过期时间（毫秒，默认 7 天 = 604800000 毫秒）
    expiration: 604800000
```

### 配置项详解

| 配置项 | 类型 | 默认值 | 说明 |
|-------|------|--------|------|
| `lumina.jwt.secret-key` | String | lumina-secret-key-for-jwt-token-generation-must-be-long-enough | JWT 签名密钥，生产环境必须修改 |
| `lumina.jwt.expiration` | Long | 604800000 | Token 过期时间（毫秒），默认 7 天 |

### 安全建议

1. **生产环境密钥生成**：
   ```bash
   # 使用 OpenSSL 生成 256 位随机密钥
   openssl rand -base64 32

   # 或使用 Python
   python -c "import secrets; print(secrets.token_urlsafe(32))"
   ```

2. **密钥长度要求**：
   - 最小长度：32 字节（256 位）
   - 推荐长度：64 字节（512 位）
   - 编码：Base64 或 URL-safe Base64

3. **过期时间建议**：
   - 开发环境：7 天（604800000 毫秒）
   - 生产环境：1-2 小时（3600000-7200000 毫秒）
   - 可结合 Refresh Token 机制

### JWT Token 结构

生成的 JWT Token 包含以下信息：

```json
{
  "sub": "admin",           // 主题（用户名）
  "userId": 1,              // 用户 ID
  "username": "admin",      // 用户名
  "tenantId": 0,            // 租户 ID
  "roles": ["SUPER_ADMIN"], // 角色列表
  "permissions": [...],     // 权限列表
  "iat": 1705795200,        // 签发时间
  "exp": 1706400000         // 过期时间
}
```

---

## 白名单配置

### 配置说明

白名单用于配置不需要 JWT 认证的路径，如登录接口、健康检查等。

### 配置位置

**Gateway**: `lumina-gateway/src/main/resources/application.yml`

### 配置示例

```yaml
lumina:
  whitelist:
    paths:
      - /api/v1/auth/login          # 旧登录接口
      - /api/v1/base/auth/login     # 新登录接口
      - /actuator/health            # 健康检查
      - /actuator/info              # 信息端点
      - /actuator/gateway           # Gateway 端点
```

### 白名单匹配规则

白名单支持以下匹配方式：

1. **精确匹配**：
   ```yaml
   - /api/v1/base/auth/login  # 只匹配这个确切路径
   ```

2. **前缀匹配**：
   ```yaml
   - /actuator                 # 匹配 /actuator/health、/actuator/info 等
   ```

3. **通配符匹配**（未来支持）：
   ```yaml
   - /api/v1/public/**         # 匹配 /api/v1/public/ 下所有路径
   ```

### 常见白名单路径

| 路径 | 说明 |
|------|------|
| `/api/v1/auth/login` | 登录接口（旧路径） |
| `/api/v1/base/auth/login` | 登录接口（新路径） |
| `/actuator/health` | 健康检查 |
| `/actuator/info` | 应用信息 |
| `/actuator/gateway` | Gateway 信息 |
| `/api/v1/public/**` | 公开 API（如果有） |

---

## 租户隔离配置

### 配置说明

Lumina 实现了基于租户的数据隔离，确保不同租户的数据完全隔离。

### 核心组件

1. **BaseContext**: 线程上下文工具类，存储当前请求的租户信息
2. **TenantIsolationInterceptor**: 拦截器，从 Gateway Header 提取租户信息
3. **TenantLineInterceptor**: MyBatis 拦截器（待完善），自动添加租户过滤条件

### Gateway 配置

Gateway 自动将租户信息通过 HTTP Header 传递给下游服务：

```yaml
# Gateway 会自动添加以下 Header
X-User-Id: 1
X-Username: admin
X-Tenant-Id: 0
X-Roles: SUPER_ADMIN,SYSTEM_ADMIN
X-Permissions: system,system:tenant,...
```

### Base 服务配置

**注册拦截器**（`WebMvcConfig.java`）：

```java
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    @Autowired
    private TenantIsolationInterceptor tenantIsolationInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantIsolationInterceptor)
                .addPathPatterns("/api/**");
    }
}
```

### 使用 BaseContext

在业务代码中获取当前租户信息：

```java
// 获取租户 ID
Long tenantId = BaseContext.getTenantId();

// 获取用户 ID
Long userId = BaseContext.getUserId();

// 获取用户名
String username = BaseContext.getUsername();

// 判断是否是超级管理员
boolean isSuperAdmin = BaseContext.isSuperAdmin();

// 判断是否是租户管理员
boolean isTenantAdmin = BaseContext.isTenantAdmin();

// 判断是否有指定权限
boolean hasPermission = BaseContext.hasPermission("system:user:create");

// 判断是否有指定角色
boolean hasRole = BaseContext.hasRole("TENANT_ADMIN");
```

### 租户隔离规则

1. **超级管理员**（tenant_id = 0）：
   - 可以查看和管理所有租户的数据
   - 可以创建和管理租户
   - 拥有所有权限

2. **租户管理员**（tenant_id > 0）：
   - 只能查看和管理本租户的数据
   - 不能管理租户
   - 只能管理本租户的用户和角色

3. **普通用户**（tenant_id > 0）：
   - 只能查看本租户的数据
   - 基本权限

### 数据库表租户隔离

需要租户隔离的表：
- `lumina_user` - 用户表
- `lumina_role` - 角色表
- `lumina_user_role` - 用户角色关联表
- `lumina_role_permission` - 角色权限关联表

不需要租户隔离的表：
- `lumina_tenant` - 租户表（全局表）
- `lumina_permission` - 权限表（系统级）

---

## 数据库配置

### 配置位置

**Base 服务**: `lumina-modules/lumina-business-base/src/main/resources/application.yml`

### 配置示例

```yaml
spring:
  datasource:
    type: com.alibaba.druid.pool.DruidDataSource
    druid:
      driver-class-name: com.mysql.cj.jdbc.Driver
      url: jdbc:mysql://localhost:3306/lumina_dev?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
      username: root
      password: root123
      initial-size: 5
      min-idle: 5
      max-active: 20
```

### 配置项详解

| 配置项 | 说明 | 默认值 |
|-------|------|--------|
| `spring.datasource.druid.driver-class-name` | JDBC 驱动类 | com.mysql.cj.jdbc.Driver |
| `spring.datasource.druid.url` | 数据库 URL | - |
| `spring.datasource.druid.username` | 数据库用户名 | - |
| `spring.datasource.druid.password` | 数据库密码 | - |
| `spring.datasource.druid.initial-size` | 初始连接数 | 5 |
| `spring.datasource.druid.min-idle` | 最小空闲连接数 | 5 |
| `spring.datasource.druid.max-active` | 最大活跃连接数 | 20 |

### MyBatis-Plus 配置

```yaml
mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
  type-aliases-package: io.lumina.base.infrastructure.entity
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
  global-config:
    db-config:
      id-type: auto                    # 主键自增
      logic-delete-field: deleted      # 逻辑删除字段
      logic-delete-value: 1            # 删除值
      logic-not-delete-value: 0        # 未删除值
```

---

## 服务端口配置

### 配置说明

不同服务使用不同的端口，避免冲突。

### 配置示例

| 服务 | 端口 | 配置文件 |
|------|------|----------|
| Gateway | 8080 | `lumina-gateway/src/main/resources/application.yml` |
| Base 服务 | 8082 | `lumina-modules/lumina-business-base/src/main/resources/application.yml` |
| Agent 服务 | 8083 | `lumina-modules/lumina-business-agent/src/main/resources/application.yml` |

### 配置格式

```yaml
server:
  port: 8080  # 服务监听端口
```

---

## Nacos 配置

### 配置说明

Nacos 用于服务注册与发现、配置管理。

### 配置位置

所有服务的 `application.yml`

### 配置示例

```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
        namespace: dev
        group: LUMINA_GROUP

      config:
        server-addr: localhost:8848
        namespace: dev
        group: LUMINA_GROUP
        file-extension: yaml
```

### 配置项详解

| 配置项 | 说明 | 示例值 |
|-------|------|--------|
| `spring.cloud.nacos.discovery.server-addr` | Nacos 服务器地址 | localhost:8848 |
| `spring.cloud.nacos.discovery.namespace` | 命名空间 ID | dev |
| `spring.cloud.nacos.discovery.group` | 服务分组 | LUMINA_GROUP |
| `spring.cloud.nacos.config.server-addr` | Nacos 配置中心地址 | localhost:8848 |
| `spring.cloud.nacos.config.namespace` | 配置命名空间 | dev |
| `spring.cloud.nacos.config.group` | 配置分组 | LUMINA_GROUP |
| `spring.cloud.nacos.config.file-extension` | 配置文件扩展名 | yaml |

---

## 完整配置示例

### Gateway 配置（`lumina-gateway/src/main/resources/application.yml`）

```yaml
server:
  port: 8080

spring:
  application:
    name: lumina-gateway

  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
        namespace: dev
        group: LUMINA_GROUP

      config:
        server-addr: localhost:8848
        namespace: dev
        group: LUMINA_GROUP
        file-extension: yaml

    gateway:
      routes:
        # Base 服务路由
        - id: lumina-base-auth-route
          uri: lb://lumina-business-base
          predicates:
            - Path=/api/v1/base/auth/**
          filters:
            - StripPrefix=0

        # ... 其他路由配置 ...

# Lumina 配置
lumina:
  jwt:
    secret-key: lumina-secret-key-for-jwt-token-generation-must-be-long-enough
    expiration: 604800000
  whitelist:
    paths:
      - /api/v1/auth/login
      - /api/v1/base/auth/login
      - /actuator/health
      - /actuator/info

# 日志配置
logging:
  level:
    org.springframework.cloud.gateway: INFO
    io.lumina: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
```

### Base 服务配置（`lumina-modules/lumina-business-base/src/main/resources/application.yml`）

```yaml
server:
  port: 8082

spring:
  application:
    name: lumina-business-base

  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
        namespace: dev
        group: LUMINA_GROUP

  datasource:
    type: com.alibaba.druid.pool.DruidDataSource
    druid:
      driver-class-name: com.mysql.cj.jdbc.Driver
      url: jdbc:mysql://localhost:3306/lumina_dev?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
      username: root
      password: root123
      initial-size: 5
      min-idle: 5
      max-active: 20

mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
  type-aliases-package: io.lumina.base.infrastructure.entity
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
  global-config:
    db-config:
      id-type: auto
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0

# Lumina 配置
lumina:
  jwt:
    secret-key: lumina-secret-key-for-jwt-token-generation-must-be-long-enough
    expiration: 604800000

logging:
  level:
    io.lumina: DEBUG
    org.springframework: INFO
```

---

## 生产环境配置建议

### 1. JWT 配置

```yaml
lumina:
  jwt:
    # 生产环境：使用生成的随机密钥
    secret-key: ${JWT_SECRET:your-production-secret-key-min-256-bits}
    # 生产环境：缩短过期时间，建议 1-2 小时
    expiration: ${JWT_EXPIRATION:3600000}  # 1 小时
```

### 2. 数据库配置

```yaml
spring:
  datasource:
    druid:
      # 生产环境：使用环境变量
      url: jdbc:mysql://${DB_HOST:prod-db}:3306/${DB_NAME:lumina}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
      username: ${DB_USER:lumina_app}
      password: ${DB_PASSWORD}
      # 生产环境：增加连接池大小
      initial-size: 10
      min-idle: 10
      max-active: 100
```

### 3. 白名单配置

```yaml
lumina:
  whitelist:
    paths:
      - /api/v1/base/auth/login
      - /actuator/health
      # 生产环境：移除 /actuator/info 和 /actuator/gateway
```

### 4. 日志配置

```yaml
logging:
  level:
    io.lumina: INFO  # 生产环境：使用 INFO 级别
    org.springframework: WARN
  file:
    name: /var/log/lumina/gateway.log  # 生产环境：输出到文件
```

---

## 配置验证

### 1. 验证 JWT 配置

```bash
# 登录获取 Token
curl -X POST http://localhost:8080/api/v1/base/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "admin123", "tenantId": 0}'

# 使用 Token 访问受保护的接口
curl http://localhost:8080/api/v1/base/users \
  -H "Authorization: Bearer <token>"
```

### 2. 验证白名单配置

```bash
# 白名单接口不需要 Token
curl http://localhost:8080/actuator/health

# 非白名单接口需要 Token
curl http://localhost:8080/api/v1/base/users
# 应返回 401 Unauthorized
```

### 3. 验证租户隔离

```bash
# 登录租户 A 的用户
curl -X POST http://localhost:8080/api/v1/base/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "tenant_admin", "password": "admin123", "tenantId": 1}'

# 尝试访问其他租户的数据（应该被拦截）
# TODO: 添加具体的测试用例
```

---

## 常见问题

### Q1：JWT Token 过期怎么办？

**A**：客户端可以实现自动刷新机制：
1. 在 Token 过期前 5 分钟请求刷新
2. 或者在收到 401 响应时重新登录

### Q2：如何动态修改白名单？

**A**：当前白名单配置在 `application.yml` 中，需要重启服务。可以后续改进为从 Nacos 配置中心读取，实现动态刷新。

### Q3：租户隔离如何保证？

**A**：通过多层防护：
1. **Gateway 层**：传递租户信息
2. **拦截器层**：设置 BaseContext
3. **业务层**：使用 BaseContext.getTenantId()
4. **数据层**：在 Mapper 查询时添加 tenant_id 条件

### Q4：生产环境如何保护 JWT 密钥？

**A**：
1. 使用环境变量存储密钥
2. 不将密钥提交到版本控制系统
3. 定期轮换密钥
4. 使用密钥管理服务（如 AWS KMS、Azure Key Vault）

---

## 相关文档

- [SQL 脚本说明](../sql/README.md)
- [测试指南](../TESTING.md)
- [项目 README](../README.md)

---

**最后更新**: 2025-01-20
