<template>
  <div class="mcp-monitor">
    <div class="page-header">
      <h2>MCP 管理</h2>
      <el-button @click="loadAll" :loading="loading">刷新</el-button>
    </div>

    <!-- MCP 未启用提示 -->
    <el-alert
      v-if="!loading && status && !status.enabled"
      title="MCP 未启用"
      type="info"
      :closable="false"
      show-icon
    >
      MCP 协议当前未启用。请在 Nacos 配置中设置 <code>lumina.mcp.enabled=true</code> 并配置 <code>lumina.mcp.servers</code>。
    </el-alert>

    <template v-if="status && status.enabled">
      <!-- MCP Server 列表 -->
      <el-card shadow="never" class="section-card">
        <template #header>
          <span>MCP Server（{{ status.servers.length }}）</span>
        </template>
        <el-table :data="status.servers" v-loading="loading" stripe size="small">
          <el-table-column prop="name" label="名称" min-width="140" />
          <el-table-column prop="transport" label="传输方式" width="120">
            <template #default="{ row }">
              <el-tag :type="row.transport === 'stdio' ? 'primary' : 'success'" size="small">
                {{ row.transport }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="连接状态" width="120">
            <template #default="{ row }">
              <el-tag :type="row.connected ? 'success' : 'danger'" size="small">
                {{ row.connected ? '已连接' : '未连接' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="toolCount" label="工具数" width="80" sortable />
          <el-table-column label="命令/URL" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.transport === 'stdio' ? row.command : row.url }}
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!loading && status.servers.length === 0" description="未配置 MCP Server" :image-size="60" />
      </el-card>

      <!-- MCP 工具列表 -->
      <el-card shadow="never" class="section-card">
        <template #header>
          <span>MCP 工具（{{ tools.length }}）</span>
        </template>
        <el-table :data="tools" stripe size="small">
          <el-table-column prop="name" label="工具名称" min-width="200" show-overflow-tooltip />
          <el-table-column prop="serverName" label="来源 Server" width="140" />
          <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        </el-table>
        <el-empty v-if="tools.length === 0" description="暂无已注册的 MCP 工具" :image-size="60" />
      </el-card>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getMcpServers, getMcpTools, type McpStatusVO, type McpToolVO } from '@/api/modules/mcp'

const loading = ref(false)
const status = ref<McpStatusVO | null>(null)
const tools = ref<McpToolVO[]>([])

async function loadAll() {
  loading.value = true
  try {
    const [statusRes, toolsRes] = await Promise.all([getMcpServers(), getMcpTools()])
    status.value = statusRes.data
    tools.value = toolsRes.data || []
  } catch (e: any) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadAll)
</script>

<style scoped>
.mcp-monitor {
  padding: 16px;
}
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.page-header h2 {
  margin: 0;
  font-size: 18px;
}
.section-card {
  margin-bottom: 16px;
}
</style>
