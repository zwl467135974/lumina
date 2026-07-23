# 12 — Vue 3 在 Lumina 的实践

> **前置要求**：已完成 [11-Vue 3 基础](11-vue3-basics.md)
> **预计阅读**：15 分钟
> **难度**：⭐⭐☆☆☆

---

## 这节解决什么问题

上一节你学了 ref/reactive/computed。这节看 Lumina 怎么把这些组织成可复用的逻辑——**组合式函数（composables）**，以及项目怎么组织组件。

---

## Composables：逻辑复用

### 问题

假设你有 5 个列表页（Agent 列表、用户列表、角色列表……），每个都要管理 `loading`、`tableData`、`pagination`，还要处理翻页、搜索。如果每个页面都写一遍：

```typescript
// Agent 列表页
const loading = ref(false)
const tableData = ref([])
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const loadData = async () => { ... }
const handlePageChange = (page) => { ... }
// ... 30 行重复逻辑

// 用户列表页
const loading = ref(false)        // ← 又写一遍
const tableData = ref([])
// ... 完全相同的 30 行
```

### 解决方案：useTable 组合式函数

Lumina 把列表页的通用逻辑抽成了 `useTable`：

```typescript
// 文件：lumina-frontend/src/composables/useTable.ts
export function useTable<T>(fetchFn: (params: any) => Promise<{ data: PageResult<T> }>) {
  const loading = ref(false)
  const tableData = ref<T[]>([])
  const pagination = reactive({
    pageNum: 1,
    pageSize: 10,
    total: 0
  })

  const loadData = async () => {
    loading.value = true
    try {
      const res = await fetchFn({
        pageNum: pagination.pageNum,
        pageSize: pagination.pageSize
      })
      tableData.value = res.data.list || res.data.records || []
      pagination.total = res.data.total || 0
    } finally {
      loading.value = false
    }
  }

  const search = () => {
    pagination.pageNum = 1
    loadData()
  }

  const handlePageChange = (page: number) => {
    pagination.pageNum = page
    loadData()
  }

  return { loading, tableData, pagination, loadData, search, handlePageChange }
}
```

### 怎么用？一行搞定

```typescript
// 文件：lumina-frontend/src/views/agent/index.vue
import { useTable } from '@/composables/useTable'
import { listAgents } from '@/api/modules/agent'

// 一行拿到列表页全部状态和方法
const { loading, tableData, pagination, loadData } = useTable<AgentVO>(
  (params) => listAgents(params)
)

// onMounted 时加载数据
onMounted(() => loadData())
```

**从 30 行重复代码 → 1 行调用**。这就是组合式函数的威力。

---

## 组件组织

Lumina 前端的组件分三层：

```
src/
├── components/          ← 公共组件（跨页面复用）
│   ├── common/          ←   通用 UI（LumTablePanel/LumStatCard/LumSearchInput）
│   └── agent/           ←   Agent 专用（AgentChat/AgentDebugPanel）
│
├── views/               ← 页面组件（每个路由一个）
│   ├── login/           ←   登录页
│   ├── dashboard/       ←   首页
│   ├── agent/           ←   Agent 管理
│   │   ├── index.vue    ←     列表页
│   │   ├── form.vue     ←     编辑表单
│   │   └── detail.vue   ←     详情页
│   └── system/          ←   系统管理
│
└── layouts/             ← 布局组件（侧边栏+顶栏+内容区）
```

**规律**：
- 通用 UI 封装成 `LumXxx` 组件放在 `components/common/`
- 页面特有的组件放对应 `views/xxx/` 目录

---

## 小结

| 概念 | 一句话记忆 |
|------|-----------|
| Composables | 把可复用逻辑抽成 `useXxx()` 函数，组件里解构使用 |
| useTable | 列表页通用逻辑（loading/数据/分页/搜索），1 行调用 |
| 组件分层 | common（通用）→ views（页面）→ layouts（布局） |

---

## 下一步

下一篇 [Element Plus 速成](13-element-plus-basics.md)——表格、表单、弹窗怎么用。

> 🚀 [13 — Element Plus →](13-element-plus-basics.md)

---

📝 **本篇撰写期间修正的代码**：无。
