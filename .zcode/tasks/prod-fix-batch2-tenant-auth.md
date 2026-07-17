# 生产修复方案 B：租户隔离与认证修复（P0+P1）

## 改动文件清单（互不冲突，不碰方案 A 的文件）

### B1. UserMapper 登录查询修复
文件：`lumina-modules/lumina-business-base/src/main/java/io/lumina/base/infrastructure/mapper/UserMapper.java`
- 当前第 27 行：`@Select("SELECT * FROM lumina_user WHERE tenant_id = #{tenantId} AND username = #{username} AND deleted = 0")`
- 问题：MyBatis 租户插件会再追加 `AND tenant_id = ?`，如果 BaseContext 没设 tenantId，追加的是 0，和传入的 tenantId 不一致
- 修复：给这个方法加 `@InterceptorIgnore(tenantLine = "true")` 注解，跳过租户插件
- 需要 import: `com.baomidou.mybatisplus.annotation.InterceptorIgnore`

### B2. 租户隔离 fail-open 改 fail-closed
文件：`lumina-modules/lumina-business-base/src/main/java/io/lumina/base/handler/TenantLineHandlerImpl.java`
- 当前第 92 行 `ignoreTable` 方法：`return detected == null || !detected.contains(tableName.toLowerCase());`
- 问题：表检测失败时（detected==null）返回 true（忽略=放行），应该返回 false（拦截）
- 修复：detected == null 时返回 false（安全默认），并加注释说明
```java
@Override
public boolean ignoreTable(String tableName) {
    if (ALWAYS_IGNORE.stream().anyMatch(tableName::equalsIgnoreCase)) {
        return true;
    }
    Set<String> detected = tablesWithTenantId;
    if (detected == null) {
        // 表检测未完成时 fail-closed（不忽略任何非 ALWAYS_IGNORE 表）
        return false;
    }
    return !detected.contains(tableName.toLowerCase());
}
```

### B3. 审计日志查询补租户过滤
文件：`lumina-modules/lumina-business-base/src/main/java/io/lumina/base/service/impl/AuditLogServiceImpl.java`
- 问题：查询审计日志时没有 tenant_id 条件（表在 ALWAYS_IGNORE 列表中，租户插件不会自动追加）
- 修复：在查询 wrapper 中手动加 `.eq(AuditLogDO::getTenantId, BaseContext.getTenantId())`
- 如果 BaseContext.getTenantId() 为 null，用 0L
- 所有查询方法都要加（list、getById 等）

### B4. 登出逻辑修复
文件：`lumina-modules/lumina-business-base/src/main/java/io/lumina/base/service/impl/AuthServiceImpl.java`
- 当前第 150-163 行：Redis 黑名单写入和 token 解析在同一个 try-catch 里
- 问题：如果 Redis 写入失败（addTokenToBlacklist 抛异常），catch 吞掉了异常，登出"成功"但 token 仍有效
- 修复：分开 try-catch
```java
// 1. 黑名单写入（失败要报错）
try {
    long remainingTtl = (jwtUtil.getExpiration(token).getTime() - System.currentTimeMillis()) / 1000;
    if (remainingTtl > 0) {
        redisCacheManager.addTokenToBlacklist(token, remainingTtl);
    }
} catch (Exception e) {
    log.error("登出时 Redis 黑名单写入失败: {}", e.getMessage());
    throw new BusinessException(ErrorCode.INTERNAL_ERROR, "登出失败，请重试");
}

// 2. 清除在线记录（失败不影响登出）
try {
    io.lumina.common.core.LoginUser loginUser = jwtUtil.parseTokenToLoginUser(token);
    if (loginUser != null && loginUser.getUserId() != null) {
        onlineUserService.recordLogout(loginUser.getUserId());
    }
} catch (Exception e) {
    log.warn("清除在线记录失败（不影响登出）: {}", e.getMessage());
}
```

### B5. CORS 收敛
文件：搜索找到 CORS 配置类（可能在 framework 或 base 模块）
- 如果 CORS 是 `allowedOriginPatterns("*")` + `allowCredentials(true)` → 改为从配置读取
- 添加配置项 `lumina.cors.allowed-origins` 默认 `http://localhost:5173`（前端开发地址）
- 如果找不到 CORS 配置类（可能是 Spring Security 或 Gateway 层），在 Gateway 配置 CORS
- 在 nacos-config/lumina-gateway.yaml 中加 CORS 配置

## 验证
mvn compile -q
mvn test -pl lumina-modules/lumina-business-base -am -Dtest="AuthServiceImplTest,UserServiceImplTest" -Dsurefire.failIfNoSpecifiedTests=false
