# 06 — 权限体系：@RequirePermission + RBAC

> **前置要求**：已完成 [05-校验与审计](05-validation-and-audit.md)
> **预计阅读**：25 分钟
> **难度**：⭐⭐⭐☆☆

---

## 这节解决什么问题

企业系统里不是所有人都能做所有事——普通用户不能删别人的 Agent，只有管理员能管理角色。**怎么控制谁能做什么？**

Lumina 用 **RBAC**（Role-Based Access Control，基于角色的访问控制）+ `@RequirePermission` 注解解决。这节讲清这套权限体系。

---

## RBAC 是什么？先建立直觉

### 类比：公司的门禁卡

公司有不同门禁级别：
- **普通员工卡**：能进大门、自己部门
- **部门经理卡**：能进大门、自己部门、会议室
- **行政卡**：能进所有门

**卡 = 角色，门 = 权限**。你不直接给某个人开某个门的权限，而是给他一张"角色卡"，这张卡有对应门的权限。

### RBAC 五表

Lumina 用五张表实现 RBAC：

```
用户(user) ──分配──► 角色(role) ──关联──► 权限(permission)
     │                  │
     └──用户角色表──┘   └──角色权限表──┘
                            │
                            ▼
                        菜单(menu)
```

| 表 | 作用 | 类比 |
|----|------|------|
| `user` | 用户 | 员工 |
| `role` | 角色 | 门禁卡类型 |
| `permission` | 权限 | 门 |
| `user_role` | 用户-角色关联 | 给员工发卡 |
| `role_permission` | 角色-权限关联 | 给卡开门权限 |

### 为什么不直接"用户-权限"？

如果 100 个用户有相同权限，改一次要改 100 行。用角色做中间层——改角色的权限，100 个用户自动生效。

---

## @RequirePermission：声明式权限控制

### 怎么用

```java
// 文件：AgentController.java:54-57
@RestController
@RequirePermission("agent:list")        // ← 类级别：整个类都需要 agent:list 权限
@RequestMapping("/api/v1/agents")
public class AgentController {

    @RequirePermission("model:create")  // ← 方法级别：覆盖类级，需要 model:create
    @PostMapping
    public R<ModelPricingDO> create(...) { }
}
```

**效果**：没有 `agent:list` 权限的用户，访问任何 Agent 接口都被拦截返回 403。

### 权限编码规范

```
模块:操作
```

| 权限码 | 含义 |
|--------|------|
| `agent:list` | 查看 Agent 列表 |
| `agent:create` | 创建 Agent（如果有这个细粒度） |
| `budget:list` | 查看预算 |
| `cost:view` | 查看成本 |
| `model:create` | 创建模型价格 |

---

## 拦截器怎么校验

```java
// 文件：lumina-business-base/.../interceptor/PermissionCheckInterceptor.java
@Override
public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    // 1. 只处理 Controller 方法
    if (!(handler instanceof HandlerMethod)) return true;

    HandlerMethod method = (HandlerMethod) handler;

    // 2. 读取方法/类上的 @RequirePermission 注解
    RequirePermission rp = method.getMethodAnnotation(RequirePermission.class);
    // （同时兼容 common 和 base 两个包的注解）

    // 3. 没有权限注解 → 放行
    if (rp == null) return true;

    // 4. 检查权限
    checkPermission(rp);
    return true;
}

private void checkPermission(RequirePermission rp) {
    // 从 BaseContext 拿当前用户的权限列表（JWT 过滤器注入的）
    String[] requiredPerms = rp.value();
    Set<String> userPerms = BaseContext.getPermissions();

    // 超管直接放行
    if (BaseContext.getRoles().contains("SUPER_ADMIN")) return;

    // AND 逻辑：需要拥有全部权限
    // OR 逻辑（默认）：拥有任一权限即可
    if (rp.requireAll()) {
        for (String perm : requiredPerms) {
            if (!userPerms.contains(perm)) {
                throw new BusinessException(ErrorCode.PERMISSION_DENIED);
            }
        }
    } else {
        boolean hasAny = Arrays.stream(requiredPerms).anyMatch(userPerms::contains);
        if (!hasAny) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
    }
}
```

### AND vs OR 逻辑

```java
@RequirePermission(value = {"agent:list", "agent:create"}, requireAll = true)
// requireAll=true → 需要同时拥有两个权限（AND）

@RequirePermission(value = {"cost:view", "budget:list"}, requireAll = false)
// requireAll=false（默认）→ 拥有任一即可（OR）
```

---

## 前端的权限控制

### 按钮级权限：v-permission 指令

```vue
<!-- 文件：lumina-frontend/src/views/system/user.vue -->
<el-button v-permission="'user:create'" @click="handleCreate">新建</el-button>
<el-button v-permission="'user:update'" @click="handleEdit">编辑</el-button>
<el-button v-permission="'user:delete'" @click="handleDelete">删除</el-button>
```

```typescript
// 文件：lumina-frontend/src/directives/permission.ts
const permission = {
  mounted(el: HTMLElement, binding) {
    const { hasPermission } = usePermissionStore()
    if (!hasPermission(binding.value)) {
      el.parentNode.removeChild(el)    // 没权限直接删掉按钮
    }
  }
}
```

**效果**：没 `user:create` 权限的用户，界面上根本看不到"新建"按钮。

### 路由级权限

```typescript
// 文件：lumina-frontend/src/router/modules/index.ts
{
  path: 'list',
  meta: {
    permissions: ['agent:list']    // ← 需要这个权限才能访问
  }
}
```

路由守卫检查 `meta.permissions`，没权限跳 403 页。

---

## 权限的"双重保障"

```
前端：v-permission 隐藏按钮（用户体验）
        ↓ 用户即使绕过前端直接调 API
后端：@RequirePermission 拦截器拦截（安全保障）
        ↓
  双重保障：前端隐藏 + 后端拦截
```

> ⚠️ **关键**：前端隐藏只是"体验优化"——不能依赖它做安全。用户可以绕过前端直接发请求。**后端的 @RequirePermission 才是真正的安全防线**。

---

## 权限从哪来

```
用户登录
  ↓
JWT 过滤器解析 Token
  ↓
从数据库/缓存查用户的角色和权限
  ↓
注入到 HTTP 头：X-Permissions=agent:list,budget:list,...
  ↓
PermissionCheckInterceptor 读头 → 写入 BaseContext
  ↓
@RequirePermission 从 BaseContext 校验
```

权限数据存在 `lumina_permission` 表，通过 `lumina_role_permission` 关联到角色，通过 `lumina_user_role` 关联到用户。Flyway V17 迁移脚本初始化了所有模块的权限种子数据。

---

## 动手试试

1. **打开 `AgentController.java`**：找到类级的 `@RequirePermission("agent:list")`
2. **打开 `PermissionCheckInterceptor.java`**：看 `checkPermission` 方法的 AND/OR 逻辑
3. **打开前端 `user.vue`**：找到 `v-permission` 指令的用法
4. **用非超管账号登录**：看看菜单和按钮是否受限

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| RBAC 五表 | user → user_role → role → role_permission → permission |
| @RequirePermission | 类/方法标注所需权限，拦截器自动校验 |
| 超管绕过 | SUPER_ADMIN 角色直接放行所有权限检查 |
| v-permission | 前端隐藏按钮（体验），后端拦截（安全） |
| 双重保障 | 前端隐藏 + 后端拦截，缺一不可 |

---

## 下一步

下一篇 [多租户隔离](07-multi-tenancy.md)——SQL 自动加 tenant_id 是怎么做到的。

> 🚀 [07 — 多租户隔离 →](07-multi-tenancy.md)

---

## 自测题

1. **RBAC 为什么要"用户→角色→权限"三层而不是直接"用户→权限"？**
   <details><summary>答案</summary>批量管理。100 个用户权限相同时，只改角色权限一次，100 个用户自动生效。如果直接关联，要改 100 次。</details>

2. **前端 v-permission 隐藏了按钮，用户还能调用那个接口吗？**
   <details><summary>答案</summary>能。前端隐藏只是体验优化，用户可以绕过前端直接发请求。后端的 @RequirePermission 才是安全防线。</details>

3. **`@RequirePermission(value={"a","b"}, requireAll=true)` 和 `requireAll=false` 有什么区别？**
   <details><summary>答案</summary>requireAll=true 需要同时拥有 a 和 b（AND），requireAll=false 拥有 a 或 b 任一即可（OR）。</details>

---

📝 **本篇撰写期间修正的代码**：无。
