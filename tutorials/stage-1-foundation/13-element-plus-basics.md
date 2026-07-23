# 13 — Element Plus 速成

> **前置要求**：已完成 [12-Vue 3 在 Lumina](12-vue3-in-lumina.md)
> **预计阅读**：20 分钟
> **难度**：⭐⭐☆☆☆

---

## 这节解决什么问题

Vue 本身只提供核心框架，UI 组件（按钮、表格、表单、弹窗）要自己写或用第三方库。**Element Plus** 是 Vue 3 最流行的企业级 UI 组件库——你不用从零写 CSS，直接用它的组件搭界面。

这节讲 Lumina 最常用的 5 个 Element Plus 组件。

---

## Element Plus 是什么？先建立直觉

### 类比：宜家家具

你自己打家具（写 HTML+CSS）——能做但费时。宜家（Element Plus）提供成品家具——你按说明书组装就行。

**Element Plus 提供 80+ 个现成组件**：按钮、表格、表单、弹窗、下拉、日期选择器……你拼装它们搭建完整界面。

### 基本用法

```vue
<template>
  <!-- el- 开头的就是 Element Plus 组件 -->
  <el-button type="primary" @click="handleClick">确定</el-button>
  <el-button type="danger">删除</el-button>
</template>
```

---

## 五大常用组件

### 1. el-table（表格）— 列表页核心

```vue
<!-- Lumina Agent 列表页简化 -->
<template>
  <el-table :data="tableData" v-loading="loading">
    <el-table-column prop="agentName" label="名称" />           <!-- 普通列 -->
    <el-table-column prop="agentType" label="类型" />
    <el-table-column prop="status" label="状态">
      <template #default="{ row }">
        <el-tag :type="row.status === 1 ? 'success' : 'info'">  <!-- 自定义渲染 -->
          {{ row.status === 1 ? '启用' : '禁用' }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="操作">
      <template #default="{ row }">
        <el-button size="small" @click="handleEdit(row)">编辑</el-button>
        <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
      </template>
    </el-table-column>
  </el-table>

  <!-- 分页 -->
  <el-pagination
    v-model:current-page="pagination.pageNum"
    v-model:page-size="pagination.pageSize"
    :total="pagination.total"
    @current-change="handlePageChange"
  />
</template>
```

### 2. el-form（表单）— 增删改核心

```vue
<template>
  <el-form ref="formRef" :model="formData" :rules="rules" label-width="80px">
    <el-form-item label="名称" prop="name">
      <el-input v-model="formData.name" placeholder="请输入名称" />
    </el-form-item>
    <el-form-item label="类型" prop="type">
      <el-select v-model="formData.type">
        <el-option label="ReAct" value="ReAct" />
        <el-option label="Plan-Execute" value="PlanAndExecute" />
      </el-select>
    </el-form-item>
  </el-form>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'

const formRef = ref()
const formData = reactive({
  name: '',
  type: ''
})

// 校验规则
const rules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

// 提交前校验
const handleSubmit = async () => {
  await formRef.value.validate()   // 校验不通过会抛异常
  // 校验通过，提交
}
</script>
```

### 3. el-dialog（弹窗）

```vue
<template>
  <el-dialog v-model="dialogVisible" title="编辑 Agent" width="500px">
    <el-form :model="formData">
      <!-- 表单内容 -->
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
const dialogVisible = ref(false)    // 控制显示/隐藏
</script>
```

### 4. el-button（按钮）

```vue
<el-button>默认</el-button>
<el-button type="primary">主要</el-button>      <!-- 蓝色 -->
<el-button type="success">成功</el-button>      <!-- 绿色 -->
<el-button type="danger">危险</el-button>       <!-- 红色 -->
<el-button :loading="saving">保存中</el-button> <!-- 加载状态 -->
<el-button :icon="Search">搜索</el-button>      <!-- 带图标 -->
```

### 5. ElMessage（消息提示）

```typescript
import { ElMessage } from 'element-plus'

// 成功提示（绿色，自动消失）
ElMessage.success('保存成功')

// 错误提示（红色）
ElMessage.error('网络错误')

// 警告提示（黄色）
ElMessage.warning('请先登录')
```

> 💡 `ElMessage` 不是组件，是函数——直接调用就弹出提示。Lumina 的 API 拦截器在请求失败时自动调用它。

---

## 在 Lumina 里怎么注册

```typescript
// 文件：lumina-frontend/src/main.ts
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'          // 别忘了引入样式

const app = createApp(App)
app.use(ElementPlus)                           // 全局注册所有组件
```

注册后，所有 `.vue` 文件都能直接用 `el-xxx` 组件，不用单独 import。

---

## 动手试试

1. **打开 `views/agent/index.vue`**：找到 `el-table`、`el-table-column`、`el-pagination`
2. **打开 `views/system/user.vue`**：找到 `el-dialog` + `el-form` 组合
3. **搜索 `ElMessage`**：看看哪些地方在弹消息提示

---

## 小结

| 组件 | 用途 | 关键属性 |
|------|------|----------|
| el-table | 数据表格 | `:data`、`el-table-column`、`#default` 插槽 |
| el-form | 表单 | `:model`、`:rules`、`validate()` |
| el-dialog | 弹窗 | `v-model` 控制显隐 |
| el-button | 按钮 | `type` 颜色、`:loading` 加载态 |
| ElMessage | 消息提示 | `success/error/warning` |

---

## 下一步

下一篇 [Pinia + Vue Router](14-pinia-router-basics.md)——状态管理和路由。

> 🚀 [14 — Pinia + Router →](14-pinia-router-basics.md)

---

📝 **本篇撰写期间修正的代码**：无。
