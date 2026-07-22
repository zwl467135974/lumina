<template>
  <div class="mcp-monitor">
    <div class="page-header">
      <h2>{{ t('monitor.mcpManageTitle') }}</h2>
      <div class="header-actions">
        <el-button type="primary" @click="showRegisterDialog" v-if="status?.enabled">
          {{ t('common.create') }}
        </el-button>
        <el-button @click="loadAll" :loading="loading">{{ t('monitor.refresh') }}</el-button>
      </div>
    </div>

    <!-- MCP 未启用提示 -->
    <el-alert
      v-if="!loading && status && !status.enabled"
      :title="t('monitor.mcpTitle')"
      type="info"
      :closable="false"
      show-icon
    >
      {{ t('monitor.mcpNotEnabledDesc') }}
    </el-alert>

    <template v-if="status && status.enabled">
      <!-- MCP Server 列表 -->
      <el-card shadow="never" class="section-card">
        <template #header>
          <span>MCP Server（{{ status.servers.length }}）</span>
        </template>
        <el-table :data="status.servers" v-loading="loading" stripe size="small">
          <el-table-column prop="name" :label="t('monitor.mcpName')" min-width="140" />
          <el-table-column prop="transport" :label="t('monitor.transport')" width="120">
            <template #default="{ row }">
              <el-tag :type="row.transport === 'stdio' ? 'primary' : 'success'" size="small">
                {{ row.transport }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('monitor.connStatus')" width="120">
            <template #default="{ row }">
              <el-tag :type="row.connected ? 'success' : 'danger'" size="small">
                {{ row.connected ? t('monitor.connConnected') : t('monitor.connDisconnected') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="toolCount" :label="t('monitor.toolCount')" width="80" sortable />
          <el-table-column :label="t('monitor.cmdUrl')" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.transport === 'stdio' ? row.command : row.url }}
            </template>
          </el-table-column>
          <el-table-column :label="t('common.actions')" width="200" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="handleReconnect(row.name)" :loading="row._reconnecting">
                {{ t('monitor.reconnect') }}
              </el-button>
              <el-button link type="success" size="small" @click="handleHealthCheck(row.name)">
                {{ t('monitor.healthCheck') }}
              </el-button>
              <el-button link type="danger" size="small" @click="handleUnregister(row.name)">
                {{ t('common.delete') }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!loading && status.servers.length === 0" :description="t('monitor.mcpNotConfigured')" :image-size="60" />
      </el-card>

      <!-- MCP 工具列表 -->
      <el-card shadow="never" class="section-card">
        <template #header>
          <span>{{ t('monitor.mcpToolsTitle', { n: tools.length }) }}</span>
        </template>
        <el-table :data="tools" stripe size="small">
          <el-table-column prop="name" :label="t('monitor.toolName')" min-width="200" show-overflow-tooltip />
          <el-table-column prop="serverName" :label="t('monitor.sourceServer')" width="140" />
          <el-table-column prop="description" :label="t('common.description')" min-width="200" show-overflow-tooltip />
        </el-table>
        <el-empty v-if="tools.length === 0" :description="t('monitor.noMcpTools')" :image-size="60" />
      </el-card>
    </template>

    <!-- 注册 MCP Server 对话框 -->
    <el-dialog v-model="registerDialogVisible" :title="t('monitor.registerServer')" width="600px">
      <el-form :model="registerForm" label-width="100px">
        <el-form-item label="Name" required>
          <el-input v-model="registerForm.name" placeholder="my-mcp-server" />
        </el-form-item>
        <el-form-item label="Transport" required>
          <el-select v-model="registerForm.transport" style="width: 100%">
            <el-option label="stdio" value="stdio" />
            <el-option label="http (SSE)" value="http" />
            <el-option label="streamable-http" value="streamable-http" />
          </el-select>
        </el-form-item>
        <template v-if="registerForm.transport === 'stdio'">
          <el-form-item label="Command" required>
            <el-input v-model="registerForm.command" placeholder="python3" />
          </el-form-item>
          <el-form-item label="Args">
            <el-input v-model="registerForm.argsStr" placeholder='["mcp_server.py", "--root", "/tmp"]' />
            <small class="form-hint">JSON 数组格式</small>
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="URL" required>
            <el-input v-model="registerForm.url" placeholder="http://localhost:3000/sse" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="registerDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleRegister" :loading="registering">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getMcpServers, getMcpTools,
  registerMcpServer, unregisterMcpServer, reconnectMcpServer, checkMcpServerHealth,
  type McpStatusVO, type McpToolVO, type McpServerRegisterDTO,
} from '@/api/modules/mcp'

const { t } = useI18n()

const loading = ref(false)
const status = ref<McpStatusVO | null>(null)
const tools = ref<McpToolVO[]>([])

// 注册对话框
const registerDialogVisible = ref(false)
const registering = ref(false)
const registerForm = reactive({
  name: '',
  transport: 'stdio' as 'stdio' | 'http' | 'streamable-http',
  command: '',
  argsStr: '',
  url: '',
})

async function loadAll() {
  loading.value = true
  try {
    const [statusRes, toolsRes] = await Promise.all([getMcpServers(), getMcpTools()])
    status.value = statusRes.data
    tools.value = toolsRes.data || []
  } catch (e: any) {
    ElMessage.error(e.message || t('monitor.loadFailed'))
  } finally {
    loading.value = false
  }
}

function showRegisterDialog() {
  registerForm.name = ''
  registerForm.transport = 'stdio'
  registerForm.command = ''
  registerForm.argsStr = ''
  registerForm.url = ''
  registerDialogVisible.value = true
}

async function handleRegister() {
  if (!registerForm.name.trim()) {
    ElMessage.warning('Name is required')
    return
  }
  registering.value = true
  try {
    const data: McpServerRegisterDTO = {
      name: registerForm.name,
      transport: registerForm.transport,
    }
    if (registerForm.transport === 'stdio') {
      data.command = registerForm.command
      if (registerForm.argsStr.trim()) {
        data.args = JSON.parse(registerForm.argsStr)
      }
    } else {
      data.url = registerForm.url
    }
    await registerMcpServer(data)
    ElMessage.success(t('common.success'))
    registerDialogVisible.value = false
    await loadAll()
  } catch (e: any) {
    ElMessage.error(e.message || 'Register failed')
  } finally {
    registering.value = false
  }
}

async function handleReconnect(name: string) {
  try {
    await reconnectMcpServer(name)
    ElMessage.success(t('monitor.reconnect') + ' OK')
    await loadAll()
  } catch (e: any) {
    ElMessage.error(e.message || 'Reconnect failed')
  }
}

async function handleHealthCheck(name: string) {
  try {
    const res = await checkMcpServerHealth(name)
    ElMessage.success(res.data ? 'Healthy' : 'Unhealthy')
  } catch (e: any) {
    ElMessage.error(e.message || 'Health check failed')
  }
}

async function handleUnregister(name: string) {
  try {
    await ElMessageBox.confirm(`Delete MCP server "${name}"?`, t('common.confirm'), { type: 'warning' })
    await unregisterMcpServer(name)
    ElMessage.success(t('common.success'))
    await loadAll()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || 'Delete failed')
    }
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
.header-actions {
  display: flex;
  gap: 8px;
}
.section-card {
  margin-bottom: 16px;
}
.form-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
