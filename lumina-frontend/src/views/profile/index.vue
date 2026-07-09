<template>
  <div class="profile-page">
    <PageHeader :title="t('profile.title')" />

    <el-tabs v-model="activeTab" class="profile-tabs">
      <!-- 个人资料 -->
      <el-tab-pane :label="t('profile.info')" name="info">
        <el-card shadow="never">
          <el-form :model="profileForm" label-width="100px" style="max-width: 500px">
            <el-form-item :label="t('system.user.username')">
              <el-input :model-value="userStore.userInfo?.username" disabled />
            </el-form-item>
            <el-form-item :label="t('system.user.nickname')">
              <el-input v-model="profileForm.nickname" :placeholder="t('system.user.nicknamePlaceholder')" />
            </el-form-item>
            <el-form-item :label="t('system.user.email')">
              <el-input v-model="profileForm.email" :placeholder="t('system.user.emailPlaceholder')" />
            </el-form-item>
            <el-form-item :label="t('system.user.phone')">
              <el-input v-model="profileForm.phone" :placeholder="t('system.user.phonePlaceholder')" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleSaveProfile" :loading="savingProfile">
                {{ t('common.save') }}
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>

      <!-- 修改密码 -->
      <el-tab-pane :label="t('profile.password')" name="password">
        <el-card shadow="never">
          <el-form
            :model="passwordForm"
            :rules="passwordRules"
            ref="passwordFormRef"
            label-width="100px"
            style="max-width: 500px"
          >
            <el-form-item :label="t('profile.oldPassword')" prop="oldPassword">
              <el-input v-model="passwordForm.oldPassword" type="password" show-password />
            </el-form-item>
            <el-form-item :label="t('profile.newPassword')" prop="newPassword">
              <el-input v-model="passwordForm.newPassword" type="password" show-password />
            </el-form-item>
            <el-form-item :label="t('profile.confirmPassword')" prop="confirmPassword">
              <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleChangePassword" :loading="changingPassword">
                {{ t('profile.changePassword') }}
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>

      <!-- 偏好设置 -->
      <el-tab-pane :label="t('profile.preferences')" name="preferences">
        <el-card shadow="never">
          <el-form label-width="120px" style="max-width: 500px">
            <el-form-item :label="t('profile.theme')">
              <el-radio-group v-model="appStore.theme" @change="onThemeChange">
                <el-radio-button value="light">{{ t('header.lightMode') }}</el-radio-button>
                <el-radio-button value="dark">{{ t('header.darkMode') }}</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item :label="t('profile.language')">
              <el-radio-group v-model="currentLocale" @change="onLocaleChange">
                <el-radio-button value="zh-CN">中文</el-radio-button>
                <el-radio-button value="en">English</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useUserStore, useAppStore } from '@/stores'
import { updateUser } from '@/api/modules/system-user'
import { PageHeader } from '@/components/common'

const { t, locale } = useI18n()
const userStore = useUserStore()
const appStore = useAppStore()

const activeTab = ref('info')
const savingProfile = ref(false)
const changingPassword = ref(false)
const passwordFormRef = ref<FormInstance>()

const currentLocale = computed({
  get: () => locale.value,
  set: (val) => { locale.value = val }
})

const profileForm = reactive({
  nickname: '',
  email: '',
  phone: ''
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const passwordRules: FormRules = {
  oldPassword: [{ required: true, message: t('profile.oldPasswordRequired'), trigger: 'blur' }],
  newPassword: [
    { required: true, message: t('profile.newPasswordRequired'), trigger: 'blur' },
    { min: 6, max: 20, message: t('profile.passwordLength'), trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: t('profile.confirmPasswordRequired'), trigger: 'blur' },
    {
      validator: (_rule: any, value: string, callback: any) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error(t('profile.passwordMismatch')))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

const loadProfile = () => {
  const info = userStore.userInfo as any
  if (info) {
    profileForm.nickname = info.nickname || ''
    profileForm.email = info.email || ''
    profileForm.phone = info.phone || ''
  }
}

const handleSaveProfile = async () => {
  const info = userStore.userInfo as any
  if (!info?.userId) return
  savingProfile.value = true
  try {
    await updateUser(info.userId, {
      nickname: profileForm.nickname,
      email: profileForm.email,
      phone: profileForm.phone
    } as any)
    ElMessage.success(t('common.updateSuccess'))
    info.nickname = profileForm.nickname
    info.email = profileForm.email
    info.phone = profileForm.phone
  } catch {
    // handled by interceptor
  } finally {
    savingProfile.value = false
  }
}

const handleChangePassword = async () => {
  if (!passwordFormRef.value) return
  await passwordFormRef.value.validate(async (valid) => {
    if (!valid) return
    changingPassword.value = true
    try {
      ElMessage.info(t('profile.passwordFeatureHint'))
      passwordForm.oldPassword = ''
      passwordForm.newPassword = ''
      passwordForm.confirmPassword = ''
    } finally {
      changingPassword.value = false
    }
  })
}

const onThemeChange = (val: string | number | boolean | undefined) => {
  appStore.setTheme(val as 'light' | 'dark')
}

const onLocaleChange = (val: string | number | boolean | undefined) => {
  locale.value = val as string
  localStorage.setItem('lumina-lang', val as string)
}

onMounted(() => loadProfile())
</script>

<style scoped>
.profile-tabs {
  max-width: 700px;
}
</style>
