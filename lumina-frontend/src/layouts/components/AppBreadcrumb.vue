<template>
  <div class="app-breadcrumb">
    <el-breadcrumb separator="/">
      <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item
        v-for="item in breadcrumbList"
        :key="item.path"
        :to="{ path: item.path }"
      >
        {{ item.meta?.title }}
      </el-breadcrumb-item>
    </el-breadcrumb>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const breadcrumbList = computed(() => {
  const matched = route.matched.filter((item) => item.meta?.title)
  return matched.slice(1)
})
</script>

<style scoped lang="scss">
.app-breadcrumb {
  padding: 16px 20px;
  background-color: #fff;
  border-bottom: 1px solid #e8e8e8;
}
</style>
