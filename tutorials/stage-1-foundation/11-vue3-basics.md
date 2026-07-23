# 11 — Vue 3 基础

> **前置要求**：会 JavaScript / TypeScript 基本语法
> **预计阅读**：30 分钟
> **难度**：⭐⭐☆☆☆

---

## 这节解决什么问题

传统前端开发（jQuery 时代）你需要手动操作 DOM——`document.getElementById('btn').innerHTML = '点击了'`。这样写很繁琐，数据和界面容易不同步。

Vue 3 说：**"你只管改数据，界面自动更新。"**

这节讲清 Vue 3 的三个核心：**Composition API、响应式数据（ref/reactive/computed）、组件通信**。

---

## Vue 是什么？先建立直觉

### 类比：Excel 表格

Excel 里你在一个单元格写 `=A1+B1`，当 A1 或 B1 变了，结果自动更新。你不用手动重算。

**Vue 就是网页版的 Excel**——你声明"这个显示区域的数据来自这个变量"，变量一变，显示区域自动更新。

```
数据（变量）         界面（模板）
   count = 0    →    "你点了 0 次"
   count = 1    →    "你点了 1 次"（自动变）
   count = 2    →    "你点了 2 次"（自动变）
```

这就是**数据驱动**——你操作数据，Vue 帮你更新界面。

---

## Composition API：Vue 3 的写法

### 一个 Vue 组件的三段式

Vue 组件（`.vue` 文件）分三部分：

```vue
<template>
  <!-- 1. HTML 模板：界面长什么样 -->
</template>

<script setup lang="ts">
// 2. JavaScript 逻辑：数据和行为
</script>

<style scoped>
/* 3. CSS 样式：怎么好看 */
</style>
```

### 最小示例

```vue
<template>
  <button @click="count++">点击了 {{ count }} 次</button>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const count = ref(0)    // ref：包装一个响应式数据
</script>
```

- `ref(0)` 创建一个响应式数据，初始值 0
- 模板里 `{{ count }}` 自动显示值
- `@click="count++"` 点击时改数据，界面自动更新

---

## 响应式数据：ref vs reactive vs computed

### ref：单个值的响应式包装

```typescript
import { ref } from 'vue'

const count = ref(0)        // 数字
const name = ref('张三')     // 字符串
const visible = ref(false)  // 布尔

// 在 <script> 里读写要 .value
count.value++               // 改值
console.log(count.value)    // 读值

// 在 <template> 里不用 .value（Vue 自动解包）
// {{ count }} 而不是 {{ count.value }}
```

> 💡 **记忆诀窍**：`ref` 包装**任何类型**（数字、字符串、布尔、对象），在 JS 里用 `.value`，在模板里不用。

### reactive：对象的响应式包装

```typescript
import { reactive } from 'vue'

const formData = reactive({
  username: '',
  password: '',
  remember: false
})

// 直接操作属性，不用 .value
formData.username = 'admin'    // 改属性，界面自动更新
```

> 💡 **ref vs reactive 的选择**：
> - 基础类型（数字、字符串、布尔）→ 用 `ref`
> - 对象/数组 → 用 `reactive`（也可以用 ref，但 reactive 更自然）

### computed：计算属性

```typescript
import { ref, computed } from 'vue'

const price = ref(100)
const quantity = ref(3)

// computed 自动追踪依赖，依赖变了自动重算
const total = computed(() => price.value * quantity.value)
// total = 300

quantity.value = 5
// total 自动变成 500（不用手动重算）
```

**computed vs 方法的区别**：computed 有**缓存**——依赖没变时不重新计算，多次读取只算一次。

---

## 在 Lumina 里长啥样

### 最简组件：LumStatCard（统计卡片）

```vue
<!-- 文件：lumina-frontend/src/components/common/LumStatCard.vue -->
<template>
  <div class="lum-stat-card" @click="clickable && $emit('click')">
    <div class="lum-stat-card__icon">
      <el-icon :size="22"><component :is="icon" v-if="icon" /></el-icon>
    </div>
    <div class="lum-stat-card__body">
      <span class="lum-stat-card__label">{{ label }}</span>      <!-- ← 使用 props -->
      <span class="lum-stat-card__value">{{ value }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
// 定义组件的"输入"（props）
interface Props {
  label: string                              // 必传
  value: string | number
  icon?: string                              // 可选
  color?: 'primary' | 'success' | 'warning' | 'danger'  // 限定取值
  clickable?: boolean
}

// withDefaults：给可选 props 设默认值
withDefaults(defineProps<Props>(), {
  color: 'primary',
  clickable: false
})

// 定义组件的"输出"（emits）
defineEmits<{ click: [] }>()                 // 点击时触发 click 事件
</script>
```

这个 100 行的组件集齐了 Vue 3 的核心写法：
- `<script setup lang="ts">` —— 组合式 API
- `defineProps<Props>()` —— 类型化的 props
- `withDefaults` —— 设默认值
- `defineEmits` —— 声明事件
- `{{ label }}` —— 模板里用 props

### 实战组件：登录页（ref + reactive + computed 三件套）

```typescript
// 文件：lumina-frontend/src/views/login/index.vue（简化）
import { ref, reactive, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAppStore } from '@/stores/modules/app'

const { t, locale } = useI18n()         // i18n
const appStore = useAppStore()          // Pinia store

// ref：简单值
const formRef = ref()                   // DOM 引用
const loading = ref(false)              // 加载状态

// reactive：表单对象
const formData = reactive({
  username: '',
  password: ''
})

// computed：派生数据
const langOptions = computed(() => [
  { code: 'zh-CN', label: '中文' },
  { code: 'en', label: 'English' }
])
```

---

## 组件通信

### 父传子：Props

```vue
<!-- 父组件 -->
<template>
  <LumStatCard label="Agent 总数" :value="42" color="primary" />
</template>

<!-- 子组件 LumStatCard 接收 -->
<script setup lang="ts">
interface Props {
  label: string
  value: string | number
  color?: string
}
defineProps<Props>()
</script>
```

### 子传父：Emits

```vue
<!-- 子组件 -->
<template>
  <button @click="$emit('click')">点击</button>
</template>
<script setup lang="ts">
defineEmits<{ click: [] }>()
</script>

<!-- 父组件 -->
<template>
  <LumStatCard @click="handleCardClick" />
</template>
```

### 双向绑定：v-model

```vue
<!-- 父组件 -->
<template>
  <LumSearchInput v-model="keyword" />
  <!-- keyword 变化自动传给子组件，子组件输入也自动更新 keyword -->
</template>
```

---

## 生命周期

组件从创建到销毁的过程，Vue 提供钩子让你在特定时机做事：

```typescript
import { onMounted, onUnmounted } from 'vue'

onMounted(() => {
  // 组件挂载到页面后执行（类似 DOMContentLoaded）
  // 常用于：发请求加载数据
  loadData()
})

onUnmounted(() => {
  // 组件销毁前执行
  // 常用于：清理定时器、取消请求
  clearInterval(timer)
})
```

> 💡 Lumina 里最常用的是 `onMounted`——页面加载后发 API 请求。

---

## 动手试试

1. **打开 `LumStatCard.vue`**：找到 `defineProps`、`defineEmits`、`withDefaults`
2. **打开 `login/index.vue`**：找到 `ref`、`reactive`、`computed` 各一处
3. **在 IDEA 里全局搜 `onMounted`**：看看哪些页面在加载时发请求

---

## 小结

| 概念 | 一句话记忆 | 关键代码 |
|------|-----------|----------|
| 数据驱动 | 改数据，界面自动更新 | `{{ count }}` |
| ref | 包装单个值，JS 里用 `.value` | `ref(0)` |
| reactive | 包装对象，直接操作属性 | `reactive({...})` |
| computed | 计算属性，有缓存 | `computed(() => ...)` |
| Props | 父传子 | `defineProps<Props>()` |
| Emits | 子传父 | `defineEmits<{...}>()` |
| 生命周期 | 特定时机执行 | `onMounted(() => ...)` |

---

## 下一步

下一篇 [Vue 3 在 Lumina 的实践](12-vue3-in-lumina.md)——Lumina 怎么组织组件、组合式函数（composables）怎么复用逻辑。

> 🚀 **现在继续**：[12 — Vue 3 在 Lumina →](12-vue3-in-lumina.md)

---

## 自测题

1. **ref 和 reactive 有什么区别？什么时候用哪个？**
   <details><summary>答案</summary>ref 包装任何类型，JS 里用 .value 读写；reactive 包装对象，直接操作属性。基础类型用 ref，对象/数组用 reactive。</details>

2. **computed 和普通函数有什么区别？**
   <details><summary>答案</summary>computed 有缓存——依赖没变时多次读取只算一次。普通函数每次调用都重算。</details>

3. **在 `<script setup>` 里 ref 要 `.value`，为什么在 `<template>` 里不用？**
   <details><summary>答案</summary>Vue 模板编译器自动对 ref 做"解包"（unwrap），所以模板里直接写 `{{ count }}` 即可。在 script 里编译器不管，要手动 .value。</details>

4. **父组件怎么接收子组件触发的事件？**
   <details><summary>答案</summary>子组件 `defineEmits` 声明 + `$emit('事件名')` 触发；父组件 `@事件名="处理函数"` 监听。</details>

---

📝 **本篇撰写期间修正的代码**：无。`LumStatCard.vue` 代码规范良好，是 Vue 3 Composition API 的优秀教学示例。
