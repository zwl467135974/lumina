<template>
  <div class="share-page">
    <PageHeader :title="t('share.title')" :description="t('share.description')" />

    <el-row :gutter="16">
      <!-- 左：模板列表 -->
      <el-col :xs="24" :md="16">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>{{ t('share.templates') }}</span>
              <el-upload
                :auto-upload="true"
                :show-file-list="false"
                :http-request="handleImportBundle"
                accept=".zip"
                class="header-upload"
              >
                <el-button type="primary" size="small">{{ t('share.importBundle') }}</el-button>
              </el-upload>
            </div>
          </template>

          <el-table :data="templates" v-loading="loading" stripe size="small">
            <el-table-column prop="name" :label="t('share.templateName')" min-width="140">
              <template #default="{ row }">
                <span class="tpl-name">{{ row.name }}</span>
                <el-tag v-if="row.version > 1" size="small" type="info" style="margin-left: 6px">v{{ row.version }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="agentType" label="Type" width="90" />
            <el-table-column prop="description" :label="t('common.description')" min-width="160" show-overflow-tooltip />
            <el-table-column :label="t('share.skillsCol')" min-width="150">
              <template #default="{ row }">
                <el-tag v-for="s in row.skillNames.slice(0, 3)" :key="s" size="small" class="skill-chip">{{ s }}</el-tag>
                <span v-if="row.skillNames.length > 3" class="more">+{{ row.skillNames.length - 3 }}</span>
                <span v-if="row.skillNames.length === 0" class="more">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" :label="t('task.createTime')" width="170" />
            <el-table-column :label="t('common.actions')" width="160" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openInstantiate(row)">{{ t('share.instantiate') }}</el-button>
                <el-button link type="danger" @click="handleDelete(row)">{{ t('common.delete') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!loading && templates.length === 0" :description="t('share.empty')" :image-size="60" />
        </el-card>
      </el-col>

      <!-- 右：技能 URL 导入 -->
      <el-col :xs="24" :md="8">
        <el-card shadow="never" class="url-card">
          <template #header>
            <span>{{ t('share.urlImport') }}</span>
          </template>
          <p class="url-tip">{{ t('share.urlTip') }}</p>
          <el-input v-model="skillUrl" :placeholder="t('share.urlPlaceholder')" clearable />
          <el-button type="primary" class="url-btn" :loading="importing" @click="handleImportUrl">
            {{ t('share.importUrl') }}
          </el-button>
          <div v-if="urlResult" class="url-result">
            <div class="url-result-line ok">
              {{ t('share.importedCount', { n: urlResult.imported.length }) }}
              <el-tag v-for="i in urlResult.imported.slice(0, 5)" :key="i.id" size="small" type="success" class="skill-chip">
                {{ i.name }}
              </el-tag>
            </div>
            <div v-for="r in urlResult.rejected.slice(0, 5)" :key="r.name" class="url-result-line bad">
              {{ r.name }}: {{ r.reason }}
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 实例化对话框 -->
    <el-dialog v-model="instantiateVisible" :title="t('share.instantiate')" width="460px">
      <el-form label-width="90px">
        <el-form-item :label="t('share.templateName')">
          <span>{{ instantiateTarget?.name }}</span>
        </el-form-item>
        <el-form-item :label="t('share.agentName')">
          <el-input v-model="instantiateName" :placeholder="instantiateTarget?.name" maxlength="90" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="instantiateVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="instantiating" @click="handleInstantiate">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'ShareCenter' })
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { PageHeader } from '@/components/common'
import {
  listTemplates,
  importBundle,
  instantiateTemplate,
  deleteTemplate,
  importSkillFromUrl,
  type AgentTemplateVO
} from '@/api/modules/share'
import type { SkillImportResult } from '@/api/modules/skill'

const { t } = useI18n()
const router = useRouter()

const templates = ref<AgentTemplateVO[]>([])
const loading = ref(false)

const skillUrl = ref('')
const importing = ref(false)
const urlResult = ref<SkillImportResult | null>(null)

const instantiateVisible = ref(false)
const instantiating = ref(false)
const instantiateTarget = ref<AgentTemplateVO | null>(null)
const instantiateName = ref('')

async function loadTemplates() {
  loading.value = true
  try {
    const res = await listTemplates()
    templates.value = res.data || []
  } finally {
    loading.value = false
  }
}

async function handleImportBundle(option: { file: File }) {
  try {
    const res = await importBundle(option.file)
    ElMessage.success(t('share.bundleImported', { name: res.data?.name || '' }))
    loadTemplates()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message ?? t('common.saveFailed'))
  }
}

function openInstantiate(row: AgentTemplateVO) {
  instantiateTarget.value = row
  instantiateName.value = ''
  instantiateVisible.value = true
}

async function handleInstantiate() {
  if (!instantiateTarget.value) return
  instantiating.value = true
  try {
    const res = await instantiateTemplate(instantiateTarget.value.id, instantiateName.value || undefined)
    ElMessage.success(t('share.instantiated', { name: res.data?.agentName || '' }))
    instantiateVisible.value = false
    router.push(`/agent/detail/${res.data?.agentId}`)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message ?? t('common.saveFailed'))
  } finally {
    instantiating.value = false
  }
}

async function handleDelete(row: AgentTemplateVO) {
  await ElMessageBox.confirm(t('share.deleteConfirm', { name: row.name }), t('common.confirm'), { type: 'warning' })
  await deleteTemplate(row.id)
  ElMessage.success(t('common.deleteSuccess'))
  loadTemplates()
}

async function handleImportUrl() {
  if (!skillUrl.value.trim()) {
    ElMessage.warning(t('share.urlPlaceholder'))
    return
  }
  importing.value = true
  urlResult.value = null
  try {
    const res = await importSkillFromUrl(skillUrl.value.trim())
    urlResult.value = res.data || null
    ElMessage.success(t('share.urlDone'))
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message ?? t('common.saveFailed'))
  } finally {
    importing.value = false
  }
}

onMounted(loadTemplates)
</script>

<style scoped lang="scss">
.share-page {
  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .tpl-name {
    font-family: var(--lumina-font-mono, monospace);
  }

  .skill-chip {
    margin-right: 4px;
  }

  .more {
    color: var(--lumina-text-secondary);
    font-size: 12px;
  }

  .url-card {
    .url-tip {
      color: var(--lumina-text-secondary);
      font-size: 12px;
      margin: 0 0 10px;
      line-height: 1.6;
    }

    .url-btn {
      width: 100%;
      margin-top: 10px;
    }

    .url-result {
      margin-top: 12px;

      .url-result-line {
        font-size: 12px;
        line-height: 1.8;

        &.ok {
          color: var(--lumina-text-primary);
        }

        &.bad {
          color: var(--lumina-danger, #f56c6c);
        }
      }
    }
  }
}
</style>
