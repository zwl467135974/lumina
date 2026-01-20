# Lumina 项目剩余任务清单

**生成时间**: 2025-01-21  
**当前进度**: 95% 完成  
**核心功能**: ✅ 100% 完成

---

## 📋 任务分类

根据实际代码分析，剩余任务分为以下几类：

### ✅ 已完成（P0/P1）
- ✅ AgentScope 工具动态注册
- ✅ MyBatis 租户拦截器 SQL 解析
- ✅ 租户创建时默认角色
- ✅ 记忆管理器 Redis 持久化

---

## 🔴 高优先级任务（P2 - 建议完成）

### 1. 前端管理页面开发

**当前状态**：
- ✅ 基础框架（Vue 3 + TypeScript）
- ✅ 布局组件（DefaultLayout）
- ✅ Agent 列表和详情页
- ✅ 登录页面
- ❌ **用户管理页面**（只有占位符）
- ❌ **角色管理页面**（完全缺失）
- ❌ **权限管理页面**（完全缺失，需要树形结构）
- ❌ **租户管理页面**（完全缺失）
- ❌ **Agent 创建/编辑页面**（完全缺失）

**代码位置**：
- `lumina-frontend/src/views/system/user.vue` - 只有占位符
- `lumina-frontend/src/views/system/` - 缺少其他页面文件

**需要实现**：
1. **用户管理页面**（3-4小时）
   - 用户列表（分页、搜索、筛选）
   - 用户创建/编辑表单
   - 用户角色分配
   - 重置密码功能
   - 用户状态管理（启用/禁用）

2. **角色管理页面**（3-4小时）
   - 角色列表（分页、搜索）
   - 角色创建/编辑表单
   - 角色权限分配（树形选择器）
   - 角色状态管理

3. **权限管理页面**（3-4小时）
   - 权限树展示（Element Plus Tree）
   - 权限创建/编辑表单
   - 权限层级管理
   - 权限代码和路径管理

4. **租户管理页面**（2-3小时）
   - 租户列表（分页、搜索）
   - 租户创建/编辑表单
   - 租户状态管理

5. **Agent 创建/编辑页面**（2-3小时）
   - Agent 配置表单
   - LLM 配置（模型选择、参数设置）
   - 工具选择
   - 提示词编辑

**预计工作量**: 13-18小时

---

### 2. 前端权限管理

**当前状态**：
- ❌ 权限 Store 缺失（`stores/modules/permission.ts`）
- ❌ 权限指令 `v-permission` 未实现
- ❌ 动态路由加载（基于权限）
- ❌ 菜单权限控制

**需要实现**：
1. **创建权限 Store**（1小时）
   ```typescript
   // stores/modules/permission.ts
   - 权限列表管理
   - 权限检查方法
   - 基于权限的路由过滤
   ```

2. **权限指令**（1小时）
   ```vue
   <el-button v-permission="'user:create'">创建</el-button>
   ```

3. **动态路由**（1小时）
   - 根据用户权限过滤路由
   - 动态注册路由

4. **菜单权限控制**（0.5小时）
   - Sidebar 组件中根据权限显示/隐藏菜单项

**代码位置**：
- `lumina-frontend/src/stores/modules/` - 缺少 `permission.ts`
- `lumina-frontend/src/directives/` - 需要创建 `permission.ts`
- `lumina-frontend/src/router/guards.ts` - 需要添加权限检查

**预计工作量**: 3-4小时

---

### 3. 前端状态持久化

**当前状态**：
- ❌ `pinia-plugin-persistedstate` 未安装
- ❌ 用户状态未持久化
- ❌ 应用状态未持久化

**需要实现**：
1. 安装依赖：
   ```bash
   npm install pinia-plugin-persistedstate
   ```

2. 配置持久化策略：
   ```typescript
   // stores/index.ts
   - 配置持久化插件
   - 设置持久化策略（localStorage/sessionStorage）
   ```

3. 标记需要持久化的 Store：
   - `user.ts` - 用户信息
   - `app.ts` - 应用状态（侧边栏折叠等）

**预计工作量**: 1-2小时

---

## 🟡 中优先级任务（P3 - 可选）

### 4. 单元测试

**当前状态**：
- ❌ **没有任何测试代码**
- ❌ 测试依赖未配置

**需要实现**：
1. **添加测试依赖**（pom.xml）
   ```xml
   - JUnit 5
   - Mockito
   - Spring Boot Test
   - AssertJ
   ```

2. **Service 层测试**（5-6小时）
   - UserServiceTest
   - RoleServiceTest
   - PermissionServiceTest
   - TenantServiceTest
   - AuthServiceTest

3. **Controller 层测试**（3-4小时）
   - 使用 MockMvc
   - API 端点测试
   - 参数验证测试
   - 权限检查测试

4. **工具类测试**（2小时）
   - StringUtil、CollectionUtil
   - DateUtil
   - JwtUtil

**目标覆盖率**: 70%+

**预计工作量**: 10-12小时

---

### 5. API 文档（SpringDoc/Swagger）

**当前状态**：
- ✅ SpringDoc 依赖已添加（pom.xml）
- ❌ **配置文件缺失**
- ❌ Controller 注解未添加

**需要实现**：
1. **创建 SpringDoc 配置**（1小时）
   ```java
   // config/SpringDocConfig.java
   - OpenAPI Bean 配置
   - JWT 认证支持
   - API 分组
   ```

2. **添加 API 注解**（1-2小时）
   - `@Operation` - 接口描述
   - `@Parameter` - 参数描述
   - `@ApiResponse` - 响应描述
   - Controller 级别分组

**代码位置**：
- `lumina-framework/src/main/java/io/lumina/framework/config/` - 需要创建 `SpringDocConfig.java`
- 所有 Controller - 需要添加注解

**预计工作量**: 2-3小时

---

### 6. Gateway 动态路由配置

**当前状态**：
- ✅ 静态 YAML 配置（application.yml）
- ✅ Nacos Discovery 已配置
- ❌ **动态路由加载未实现**
- ❌ 路由刷新机制未实现

**需要实现**：
1. **从 Nacos 读取路由配置**（2小时）
   - 创建路由配置实体
   - 实现 Nacos 配置监听

2. **动态刷新路由**（1.5小时）
   - 监听配置变更
   - 更新 Gateway 路由

3. **路由限流配置**（0.5小时，可选）
   - Sentinel 集成
   - 限流规则配置

**代码位置**：
- `lumina-gateway/src/main/java/io/lumina/gateway/config/` - 需要创建动态路由配置类

**预计工作量**: 3-4小时

---

### 7. ConfigLoader Nacos 集成

**当前状态**：
- ✅ 支持 classpath 资源
- ❌ **Nacos 配置中心支持未实现**

**需要实现**：
1. **Nacos 配置读取**（1.5小时）
   - 实现 Nacos 配置客户端
   - 优先级：Nacos > classpath

2. **配置热更新**（1小时）
   - 监听 Nacos 配置变更
   - 刷新 Agent 配置

3. **配置验证**（0.5小时）
   - 配置格式验证
   - 必需字段检查

**代码位置**：
- `lumina-agent-core/src/main/java/io/lumina/agent/loader/ConfigLoader.java`

**预计工作量**: 2-3小时

---

### 8. Redis 缓存增强

**当前状态**：
- ✅ Redis 配置完成
- ✅ MemoryManager Redis 持久化完成
- ❌ **用户权限缓存未实现**
- ❌ **角色权限缓存未实现**
- ❌ **Token 黑名单未实现**

**需要实现**：
1. **用户权限缓存**（1小时）
   ```java
   - 缓存用户权限列表
   - 缓存 Key: user:permissions:{userId}
   - TTL: 30分钟
   ```

2. **角色权限缓存**（1小时）
   ```java
   - 缓存角色权限列表
   - 缓存 Key: role:permissions:{roleId}
   - TTL: 1小时
   ```

3. **Token 黑名单**（0.5小时）
   ```java
   - 登出时加入黑名单
   - 验证时检查黑名单
   - TTL: 与 Token 过期时间一致
   ```

**代码位置**：
- `lumina-modules/lumina-business-base/src/main/java/io/lumina/base/service/` - 需要添加缓存层

**预计工作量**: 2-3小时

---

## 🟢 低优先级任务（P4 - 优化）

### 9. EnhancedToolManager JSON Schema 支持

**当前状态**：
- ⚠️ 参数解析使用简单实现
- ❌ JSON Schema 解析未实现

**代码位置**：
```java
// EnhancedToolManager.java:237-240
private Object[] parseParameters(String params, Method method) {
    // TODO: 根据 JSON Schema 解析参数
    return new Object[0];
}
```

**预计工作量**: 1-2小时

---

### 10. Docker 部署配置

**当前状态**：
- ❌ **Dockerfile 不存在**
- ❌ **docker-compose.yml 不存在**

**需要实现**：
1. **Dockerfile**（各模块）
   - lumina-gateway/Dockerfile
   - lumina-business-base/Dockerfile
   - lumina-agent-service/Dockerfile
   - lumina-frontend/Dockerfile

2. **docker-compose.yml**
   - 服务编排
   - 网络配置
   - 数据卷配置
   - 环境变量配置

**预计工作量**: 3-4小时

---

## 📊 剩余工作量统计

| 优先级 | 任务 | 预计工作量 | 累计工作量 |
|--------|------|-----------|-----------|
| **P2（高）** | 前端管理页面 | 13-18小时 | 13-18小时 |
| **P2（高）** | 前端权限管理 | 3-4小时 | 16-22小时 |
| **P2（高）** | 前端状态持久化 | 1-2小时 | 17-24小时 |
| **P3（中）** | 单元测试 | 10-12小时 | 27-36小时 |
| **P3（中）** | API 文档 | 2-3小时 | 29-39小时 |
| **P3（中）** | Gateway 动态路由 | 3-4小时 | 32-43小时 |
| **P3（中）** | Nacos 配置集成 | 2-3小时 | 34-46小时 |
| **P3（中）** | Redis 缓存增强 | 2-3小时 | 36-49小时 |
| **P4（低）** | JSON Schema 支持 | 1-2小时 | 37-51小时 |
| **P4（低）** | Docker 部署 | 3-4小时 | 40-55小时 |

**按每天工作 6 小时计算**：
- **P2 任务**：约 3-4 天（前端开发）
- **P3 任务**：约 3-4 天（测试和优化）
- **P4 任务**：约 1 天（部署配置）
- **总计**：约 7-9 天

---

## 🎯 建议的实施顺序

### 阶段1：前端功能完善（推荐优先）
**时间**：3-4天（17-24小时）

1. 前端管理页面开发（13-18小时）
2. 前端权限管理（3-4小时）
3. 前端状态持久化（1-2小时）

**产出**：
- ✅ 完整的管理界面
- ✅ 用户可直接使用的系统

---

### 阶段2：质量保障（建议）
**时间**：2-3天（12-15小时）

1. 单元测试（10-12小时）
2. API 文档（2-3小时）

**产出**：
- ✅ 代码质量保障
- ✅ API 文档完善

---

### 阶段3：运维增强（可选）
**时间**：1-2天（7-10小时）

1. Gateway 动态路由（3-4小时）
2. Nacos 配置集成（2-3小时）
3. Redis 缓存增强（2-3小时）

**产出**：
- ✅ 运维灵活性提升
- ✅ 性能优化

---

### 阶段4：部署配置（可选）
**时间**：1天（4-6小时）

1. Docker 部署配置（3-4小时）
2. JSON Schema 支持（1-2小时）

**产出**：
- ✅ 容器化部署
- ✅ 工具参数解析增强

---

## 💡 立即可开始的任务

### 选项1：前端功能完善（强烈推荐）
**优先级**：⭐⭐⭐⭐⭐

1. 用户管理页面（3-4小时）
2. 角色管理页面（3-4小时）
3. 权限管理页面（3-4小时）
4. 前端权限管理（3-4小时）

**优点**：
- 快速看到完整功能
- 用户可以直接使用系统
- 提升用户体验

**预计时间**：2-3天

---

### 选项2：质量保障（推荐）
**优先级**：⭐⭐⭐⭐

1. 单元测试（10-12小时）
2. API 文档（2-3小时）

**优点**：
- 提升代码质量
- 减少 bug
- 便于维护

**预计时间**：2-3天

---

### 选项3：运维优化（可选）
**优先级**：⭐⭐⭐

1. Gateway 动态路由（3-4小时）
2. Redis 缓存增强（2-3小时）

**优点**：
- 提升运维灵活性
- 性能优化

**预计时间**：1天

---

## 📝 注意事项

1. **前端页面开发**是最重要和最紧急的任务，建议优先完成
2. **单元测试**可以逐步完善，不要求一次性达到 70% 覆盖率
3. **API 文档**可以边开发边补充注解，不需要一次性完成
4. **Docker 部署**可以等前端功能完善后再做

---

**当前进度**: 95% 完成  
**核心功能**: ✅ 100% 完成  
**剩余工作**: 主要是前端开发和测试完善

