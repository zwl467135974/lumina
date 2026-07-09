<template>
  <div class="login-page">
    <div class="login-bg-layer"></div>

    <div class="login-card">
      <!-- Brand Logo -->
      <div class="login-brand">
        <div class="login-logo">
          <svg viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
            <polygon
              points="32,4 58,19 58,49 32,64 6,49 6,19"
              fill="none"
              stroke="var(--lumina-primary)"
              stroke-width="2"
            />
            <polygon
              points="32,14 50,24.5 50,45.5 32,56 14,45.5 14,24.5"
              fill="none"
              stroke="var(--lumina-primary-light)"
              stroke-width="1.5"
              opacity="0.5"
            />
            <circle cx="32" cy="35" r="5" fill="var(--lumina-primary)" />
          </svg>
        </div>
        <h1 class="login-title">Lumina</h1>
        <p class="login-subtitle">{{ t('login.subtitle') }}</p>
      </div>

      <!-- Form -->
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        class="login-form"
        @submit.prevent="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="formData.username"
            :placeholder="t('login.usernamePlaceholder')"
            size="large"
            :prefix-icon="User"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="formData.password"
            type="password"
            :placeholder="t('login.passwordPlaceholder')"
            size="large"
            :prefix-icon="Lock"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            class="login-btn"
            @click="handleLogin"
          >
            {{ t('login.submit') }}
          </el-button>
        </el-form-item>
      </el-form>

      <p class="login-footer-text">{{ t('login.footerHint') }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores'

const { t } = useI18n()
const router = useRouter()
const userStore = useUserStore()

const formRef = ref(null)
const loading = ref(false)

const formData = reactive({
  username: '',
  password: ''
})

const formRules = reactive({
  username: [
    { required: true, message: () => t('login.usernameRequired'), trigger: 'blur' }
  ],
  password: [
    { required: true, message: () => t('login.passwordRequired'), trigger: 'blur' }
  ]
})

async function handleLogin() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await userStore.login(formData.username, formData.password)
    ElMessage.success(t('login.success'))
    router.push('/')
  } catch (err) {
    ElMessage.error(err?.message || t('login.error'))
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
/* ============================================
   Login Page — Studio Indigo 企业级
   亮色默认 + 暗色自动跟随
   ============================================ */

.login-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--lumina-bg-page);
  overflow: hidden;
  font-family: var(--lumina-font-body);
  padding: var(--lumina-spacing-lg);
}

/* ---------- 背景装饰：极淡点阵 ---------- */
.login-bg-layer {
  position: absolute;
  inset: 0;
  background-image: radial-gradient(
    circle,
    rgba(var(--lumina-primary-rgb), 0.04) 1px,
    transparent 1px
  );
  background-size: 32px 32px;
  z-index: 0;
  pointer-events: none;
}

/* ---------- 登录卡片 ---------- */
.login-card {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 400px;
  padding: var(--lumina-spacing-2xl) var(--lumina-spacing-xl) var(--lumina-spacing-xl);
  background: var(--lumina-bg-card);
  border: 1px solid var(--lumina-border);
  border-radius: var(--lumina-radius-xl);
  box-shadow: var(--lumina-shadow-lg);
  animation: loginFadeIn 0.5s var(--lumina-ease-out) both;
}

@keyframes loginFadeIn {
  from {
    opacity: 0;
    transform: translateY(16px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ---------- 品牌区域 ---------- */
.login-brand {
  text-align: center;
  margin-bottom: var(--lumina-spacing-xl);
}

.login-logo {
  display: flex;
  justify-content: center;
  margin-bottom: var(--lumina-spacing-md);
}

.login-logo svg {
  width: 64px;
  height: 64px;
}

.login-title {
  font-family: var(--lumina-font-display);
  font-size: var(--lumina-font-size-3xl);
  font-weight: var(--lumina-font-weight-bold);
  letter-spacing: -0.02em;
  margin: 0 0 var(--lumina-spacing-xs) 0;
  color: var(--lumina-text-primary);
}

.login-subtitle {
  margin: 0;
  font-size: var(--lumina-font-size-base);
  color: var(--lumina-text-secondary);
  line-height: var(--lumina-line-height-base);
}

/* ---------- 表单 ---------- */
.login-form {
  display: flex;
  flex-direction: column;
}

.login-form :deep(.el-form-item) {
  margin-bottom: var(--lumina-spacing-md);
}

/* ---------- 提交按钮 ---------- */
.login-btn {
  width: 100%;
  margin-top: var(--lumina-spacing-sm);
  height: 46px;
  font-family: var(--lumina-font-display);
  font-size: var(--lumina-font-size-md);
  font-weight: var(--lumina-font-weight-semibold);
  border-radius: var(--lumina-radius-md);
}

/* ---------- 底部提示 ---------- */
.login-footer-text {
  text-align: center;
  margin: var(--lumina-spacing-lg) 0 0 0;
  font-size: var(--lumina-font-size-xs);
  color: var(--lumina-text-muted);
}

/* ---------- 响应式 ---------- */
@media (max-width: 480px) {
  .login-card {
    max-width: 100%;
    padding: var(--lumina-spacing-xl) var(--lumina-spacing-lg);
    border-radius: var(--lumina-radius-lg);
  }
}
</style>
