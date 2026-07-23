# 10 — 实战：从零写一个前端页面

> **前置要求**：已完成 [09-实战：后端模块开发](09-build-a-feature-backend.md)
> **预计阅读**：35 分钟
> **难度**：⭐⭐⭐⭐☆

---

## 这节解决什么问题

上一篇你写了公告管理的后端。这篇给它配一个前端页面——列表、搜索、新建、编辑、删除，一个不少。

学完你能复制这个流程，给任何后端接口配前端页面。

---

## 我们要做什么

配合 09 篇的公告管理后端，实现：
- 公告列表页（表格 + 分页 + 搜索）
- 新建/编辑弹窗（表单 + 校验）
- 删除确认
- 权限控制（按钮显隐）

涉及 5 个文件的创建/修改。

---

## Step 1：类型定义

```
lumina-frontend/src/types/announcement.ts
```

```typescript
// 和后端 VO 对齐
export interface AnnouncementVO {
  id: number
  title: string
  content: string
  status: number      // 0=草稿 1=发布
  createBy: number
  createTime: string
}

// 和后端 DTO 对齐
export interface AnnouncementDTO {
  title: string
  content?: string
  status?: number
}

// 查询参数
export interface AnnouncementQuery {
  title?: string
  status?: number
  pageNum: number
  pageSize: number
}
```

> 💡 前后端类型要对齐——这就是 TypeScript 的价值。后端改字段，前端编译就报错。

---

## Step 2：API 封装

```
lumina-frontend/src/api/modules/announcement.ts
```

```typescript
import { http } from '@/api/request'
import type { R, PageResult } from '@/types/api'
import type { AnnouncementVO, AnnouncementDTO, AnnouncementQuery } from '@/types/announcement'

// 列表查询
export function listAnnouncements(params: AnnouncementQuery) {
  return http.get<R<PageResult<AnnouncementVO>>>('/api/v1/base/announcements', { params })
}

// 详情
export function getAnnouncement(id: number) {
  return http.get<R<AnnouncementVO>>(`/api/v1/base/announcements/${id}`)
}

// 创建
export function createAnnouncement(data: AnnouncementDTO) {
  return http.post<R<AnnouncementVO>>('/api/v1/base/announcements', data)
}

// 更新
export function updateAnnouncement(id: number, data: AnnouncementDTO) {
  return http.put<R<AnnouncementVO>>(`/api/v1/base/announcements/${id}`, data)
}

// 删除
export function deleteAnnouncement(id: number) {
  return http.delete<R<void>>(`/api/v1/base/announcements/${id}`)
}
```

---

## Step 3：路由注册

```
lumina-frontend/src/router/modules/index.ts （添加一段）
```

```typescript
export const announcementRoutes = {
  path: '/announcement',
  component: Layout,
  redirect: '/announcement/list',
  meta: { title: '公告管理', icon: 'Bell', requiresAuth: true },
  children: [
    {
      path: 'list',
      name: 'AnnouncementList',
      component: () => import('@/views/announcement/index.vue'),    // 懒加载
      meta: {
        title: '公告列表',
        requiresAuth: true,
        permissions: ['announcement:list'],    // ← 需要权限
        keepAlive: true
      }
    }
  ]
}
```

别忘了在路由汇总里注册：
```typescript
// router/index.ts
export const routes = [
  // ... 其他路由
  agentRoutes,
  announcementRoutes,    // ← 加这行
]
```

---

## Step 4：页面组件

```
lumina-frontend/src/views/announcement/index.vue
```

```vue
<template>
  <div class="announcement-page">
    <!-- 搜索栏 -->
    <el-card>
      <el-form inline>
        <el-form-item label="标题">
          <el-input v-model="searchTitle" placeholder="搜索标题" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchStatus" placeholder="全部" clearable>
            <el-option label="草稿" :value="0" />
            <el-option label="已发布" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card style="margin-top: 16px">
      <template #header>
        <div style="display: flex; justify-content: space-between">
          <span>公告列表</span>
          <el-button v-permission="'announcement:list'" type="primary" @click="handleCreate">
            新建公告
          </el-button>
        </div>
      </template>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="title" label="标题" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '已发布' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        @current-change="handlePageChange"
        style="margin-top: 16px; justify-content: flex-end"
      />
    </el-card>

    <!-- 新建/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑公告' : '新建公告'" width="600px">
      <el-form ref="formRef" :model="formData" :rules="rules" label-width="80px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="formData.title" placeholder="请输入标题" />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input v-model="formData.content" type="textarea" :rows="6" placeholder="请输入内容" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="formData.status">
            <el-radio :value="0">草稿</el-radio>
            <el-radio :value="1">发布</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listAnnouncements,
  createAnnouncement,
  updateAnnouncement,
  deleteAnnouncement
} from '@/api/modules/announcement'
import type { AnnouncementVO } from '@/types/announcement'

// === 列表状态 ===
const loading = ref(false)
const tableData = ref<AnnouncementVO[]>([])
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const searchTitle = ref('')
const searchStatus = ref<number | undefined>(undefined)

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const res = await listAnnouncements({
      title: searchTitle.value || undefined,
      status: searchStatus.value,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    })
    tableData.value = res.data.list
    pagination.total = res.data.total
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadData()
}

const handleReset = () => {
  searchTitle.value = ''
  searchStatus.value = undefined
  handleSearch()
}

const handlePageChange = (page: number) => {
  pagination.pageNum = page
  loadData()
}

// === 弹窗状态 ===
const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref()
const formData = reactive({
  title: '',
  content: '',
  status: 1
})
const rules = {
  title: [{ required: true, message: '标题不能为空', trigger: 'blur' }]
}

const handleCreate = () => {
  editingId.value = null
  formData.title = ''
  formData.content = ''
  formData.status = 1
  dialogVisible.value = true
}

const handleEdit = (row: AnnouncementVO) => {
  editingId.value = row.id
  formData.title = row.title
  formData.content = row.content
  formData.status = row.status
  dialogVisible.value = true
}

const handleSubmit = async () => {
  await formRef.value.validate()    // 前端校验
  submitting.value = true
  try {
    if (editingId.value) {
      await updateAnnouncement(editingId.value, { ...formData })
      ElMessage.success('更新成功')
    } else {
      await createAnnouncement({ ...formData })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadData()    // 刷新列表
  } finally {
    submitting.value = false
  }
}

const handleDelete = (row: AnnouncementVO) => {
  ElMessageBox.confirm(`确定删除「${row.title}」吗？`, '提示', {
    type: 'warning'
  }).then(async () => {
    await deleteAnnouncement(row.id)
    ElMessage.success('删除成功')
    loadData()
  }).catch(() => {})    // 取消不做任何事
}

// 页面加载时拉数据
onMounted(() => loadData())
</script>
```

---

## Step 5：菜单注册

后端菜单从数据库动态下发。你需要在 Flyway 脚本里加菜单种子（V45 脚本补充）：

```sql
-- 追加到 V45__add_announcement.sql
INSERT INTO `lumina_menu` (`menu_name`, `path`, `component`, `parent_id`, `sort`, `icon`)
VALUES ('公告管理', '/announcement', 'announcement/index', 0, 50, 'Bell');
```

> 💡 Lumina 的菜单是**后端下发**的——前端不硬编码菜单，登录后从 API `/api/v1/base/menus` 获取。详见[第一阶 14-Pinia+Router](../stage-1-foundation/14-pinia-router-basics.md)。

---

## 对照检查清单

- [x] **类型定义**：types/ 里定义 VO/DTO/Query
- [x] **API 封装**：api/modules/ 里封装 5 个接口
- [x] **路由注册**：router/modules/ 加路由 + meta.permissions
- [x] **页面组件**：Composition API + Element Plus + el-table + el-dialog
- [x] **权限控制**：v-permission 按钮指令
- [x] **前端校验**：el-form rules + formRef.validate()
- [x] **菜单注册**：Flyway 脚本加菜单种子

---

## 动手试试

1. **创建上述 5 个文件/修改**
2. **编译前端**：`cd lumina-frontend && pnpm dev`
3. **访问** http://localhost:3000/announcement/list
4. **操作**：新建 → 搜索 → 编辑 → 删除，验证全链路

---

## 小结

| 步骤 | 做什么 | 文件 |
|------|--------|------|
| 1. 类型 | 定义 VO/DTO | types/announcement.ts |
| 2. API | 封装接口调用 | api/modules/announcement.ts |
| 3. 路由 | 注册路由+权限 | router/modules/index.ts |
| 4. 页面 | 表格+弹窗+搜索 | views/announcement/index.vue |
| 5. 菜单 | 数据库加菜单种子 | Flyway 脚本 |

**和后端一样，这 5 步是固定的**——每个新前端页面都是这个流程。

---

## 🎉 第二阶核心完成

你已经学会了从零开发一个**完整的前后端功能**！加上前面 8 篇理念，你现在能：
- 理解每个技术为什么这么设计
- 独立创建后端模块（Flyway→DO→Mapper→Service→Controller）
- 独立创建前端页面（类型→API→路由→组件→菜单）

---

## 下一步

剩下的 5 篇（11-15）是工程化主题：Nacos/Gateway、配置管理、测试、Git 规范、技术选型。如果你想直接进 AI 专项也完全可以——你已经具备开发能力了。

> 🚀 [11 — Nacos + Gateway →](11-nacos-gateway.md)

---

## 自测题

1. **为什么前端类型定义要和后端 VO/DTO 对齐？**
   <details><summary>答案</summary>类型对齐保证前后端数据结构一致。后端改字段，前端 TS 编译报错能立即发现。如果用裸 JS 或 any，运行时才发现问题。</details>

2. **`v-permission="'announcement:list'"` 的作用是什么？**
   <details><summary>答案</summary>按钮级权限控制。没这个权限的用户，界面上看不到这个按钮。但注意这只是体验优化——安全防线在后端 @RequirePermission。</details>

3. **为什么菜单不写在前端，而是后端数据库下发？**
   <details><summary>答案</summary>动态权限驱动——不同角色看到的菜单不同。后端根据用户权限返回菜单列表，前端只渲染后端给的内容。改权限不用改前端代码。</details>

---

📝 **本篇撰写期间修正的代码**：无。本篇是教学示例代码（新建文件），未实际创建到项目中。
