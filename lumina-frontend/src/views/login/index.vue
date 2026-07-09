<!--
  Lumina AI Agent Platform �� Login Page
  Design: "Luminous" Dark Theme
  Engineer: ����ҳ����ʦ (Subagent)
-->

<template>
  <div class="login-page">
    <!-- Floating light orbs (CSS pseudo-elements are used, this div holds the grid texture) -->
    <div class="login-bg-layer"></div>

    <!-- Login Card -->
    <div class="login-card">
      <!-- Brand Logo -->
      <div class="login-brand">
        <div class="login-logo">
          <div class="login-logo-hex">
            <div class="login-logo-glow"></div>
            <svg viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
              <polygon
                points="32,4 58,19 58,49 32,64 6,49 6,19"
                fill="none"
                stroke="url(#logoGrad)"
                stroke-width="1.5"
              />
              <polygon
                points="32,14 50,24.5 50,45.5 32,56 14,45.5 14,24.5"
                fill="none"
                stroke="url(#logoGradInner)"
                stroke-width="1"
                opacity="0.6"
              />
              <circle cx="32" cy="35" r="5" fill="url(#logoGlowCenter)" />
              <defs>
                <linearGradient id="logoGrad" x1="0" y1="0" x2="64" y2="64">
                  <stop offset="0%" stop-color="#818cf8" />
                  <stop offset="100%" stop-color="#6366f1" />
                </linearGradient>
                <linearGradient id="logoGradInner" x1="64" y1="0" x2="0" y2="64">
                  <stop offset="0%" stop-color="#6366f1" />
                  <stop offset="100%" stop-color="#818cf8" />
                </linearGradient>
                <radialGradient id="logoGlowCenter" cx="0.5" cy="0.5" r="0.5">
                  <stop offset="0%" stop-color="#6366f1" stop-opacity="0.9" />
                  <stop offset="50%" stop-color="#818cf8" stop-opacity="0.4" />
                  <stop offset="100%" stop-color="#4f46e5" stop-opacity="0" />
                </radialGradient>
              </defs>
            </svg>
          </div>
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
        <el-form-item prop="username" class="login-form-item">
          <el-input
            v-model="formData.username"
            :placeholder="t('login.usernamePlaceholder')"
            size="large"
            :prefix-icon="User"
            class="login-input"
          />
        </el-form-item>
        <el-form-item prop="password" class="login-form-item">
          <el-input
            v-model="formData.password"
            type="password"
            :placeholder="t('login.passwordPlaceholder')"
            size="large"
            :prefix-icon="Lock"
            class="login-input"
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-form-item class="login-form-item">
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

      <!-- Footer hint -->
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
   Login Page — 企业级亮色
   ============================================ */
.login-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--lumina-bg-base);
  overflow: hidden;
  font-family: var(--lumina-font-body);
}

/* ���� Grid texture layer ���� */
.login-bg-layer {
  position: absolute;
  inset: 0;
  background-image:
    /* Subtle dot grid */
    radial-gradient(circle, rgba(var(--lumina-primary-rgb), 0.06) 1px, transparent 1px);
  background-size: 32px 32px;
  z-index: 0;
  pointer-events: none;
}

/* ���� Deep space radial layers on the page itself ���� */
.login-page::before,
.login-page::after {
  content: '';
  position: absolute;
  border-radius: 50%;
  filter: blur(100px);
  z-index: 0;
  pointer-events: none;
}

/* Purple glow orb �� top-left */
.login-page::before {
  width: 600px;
  height: 600px;
  background: radial-gradient(circle, rgba(var(--lumina-primary-rgb), 0.18) 0%, transparent 70%);
  top: -15%;
  left: -5%;
  animation: orbDriftPurple 18s ease-in-out infinite;
}

/* Amber glow orb �� bottom-right */
.login-page::after {
  width: 500px;
  height: 500px;
  background: radial-gradient(circle, rgba(var(--lumina-warning-rgb), 0.12) 0%, transparent 70%);
  bottom: -10%;
  right: -5%;
  animation: orbDriftAmber 22s ease-in-out infinite;
}

@keyframes orbDriftPurple {
  0%, 100% {
    transform: translate(0, 0) scale(1);
  }
  25% {
    transform: translate(60px, 40px) scale(1.08);
  }
  50% {
    transform: translate(-20px, 80px) scale(0.95);
  }
  75% {
    transform: translate(40px, 20px) scale(1.05);
  }
}

@keyframes orbDriftAmber {
  0%, 100% {
    transform: translate(0, 0) scale(1);
  }
  25% {
    transform: translate(-50px, -30px) scale(1.1);
  }
  50% {
    transform: translate(30px, -70px) scale(0.92);
  }
  75% {
    transform: translate(-40px, -10px) scale(1.04);
  }
}

/* Extra micro orb �� third layer */
.login-page .login-bg-layer::before {
  content: '';
  position: absolute;
  width: 300px;
  height: 300px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(167, 139, 250, 0.1) 0%, transparent 70%);
  filter: blur(60px);
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  animation: orbDriftMiddle 25s ease-in-out infinite;
  pointer-events: none;
}

@keyframes orbDriftMiddle {
  0%, 100% {
    transform: translate(-50%, -50%) scale(1);
  }
  33% {
    transform: translate(-40%, -55%) scale(1.15);
  }
  66% {
    transform: translate(-60%, -45%) scale(0.9);
  }
}

/* ============================================
   Card �� Glass Morphism
   ============================================ */
.login-card {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 420px;
  padding: 48px 40px 40px;
  background: rgba(30, 41, 59, 0.85);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(var(--lumina-primary-rgb), 0.2);
  border-radius: var(--lumina-radius-xl);
  box-shadow:
    var(--lumina-shadow-lg),
    inset 0 1px 0 rgba(255, 255, 255, 0.04),
    0 0 40px rgba(var(--lumina-primary-rgb), 0.08);
  animation: fadeInUp 0.7s var(--lumina-transition-base) both;
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(30px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ============================================
   Brand Section
   ============================================ */
.login-brand {
  text-align: center;
  margin-bottom: 36px;
}

/* Hex Logo Container */
.login-logo {
  display: flex;
  justify-content: center;
  margin-bottom: 20px;
}

.login-logo-hex {
  position: relative;
  width: 72px;
  height: 72px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-logo-hex svg {
  position: relative;
  z-index: 1;
  width: 100%;
  height: 100%;
  filter: drop-shadow(0 0 12px rgba(var(--lumina-primary-rgb), 0.4));
}

.login-logo-glow {
  position: absolute;
  inset: -12px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(var(--lumina-primary-rgb), 0.25) 0%, transparent 70%);
  filter: blur(16px);
  animation: logoPulse 3s ease-in-out infinite;
}

@keyframes logoPulse {
  0%, 100% {
    opacity: 0.5;
    transform: scale(1);
  }
  50% {
    opacity: 1;
    transform: scale(1.15);
  }
}

/* Title �� gradient text */
.login-title {
  font-family: var(--lumina-font-display);
  font-size: 28px;
  font-weight: 700;
  letter-spacing: -0.5px;
  margin: 0 0 8px 0;
  background: linear-gradient(135deg, #818cf8 0%, #6366f1 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

/* Subtitle */
.login-subtitle {
  margin: 0;
  font-size: 14px;
  color: var(--lumina-text-muted);
  line-height: 1.5;
}

/* ============================================
   Form
   ============================================ */
.login-form {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.login-form-item {
  margin-bottom: 0;
}

/* Override Element Plus input styles for dark theme */
.login-input :deep(.el-input__wrapper) {
  background: rgba(15, 23, 42, 0.7);
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: var(--lumina-radius-md);
  box-shadow: none;
  transition: border-color var(--lumina-transition-base),
              box-shadow var(--lumina-transition-base);
}

.login-input :deep(.el-input__wrapper:hover) {
  border-color: rgba(var(--lumina-primary-rgb), 0.4);
}

.login-input :deep(.el-input__wrapper.is-focus) {
  border-color: var(--lumina-primary);
  box-shadow: 0 0 0 3px rgba(var(--lumina-primary-rgb), 0.15);
}

.login-input :deep(.el-input__inner) {
  color: var(--lumina-text-primary);
  caret-color: var(--lumina-primary);
}

.login-input :deep(.el-input__inner::placeholder) {
  color: var(--lumina-text-muted);
}

.login-input :deep(.el-input__prefix) {
  color: var(--lumina-text-muted);
  transition: color var(--lumina-transition-base);
}

.login-input :deep(.el-input__wrapper.is-focus .el-input__prefix) {
  color: var(--lumina-primary-light);
}

/* ���� Submit Button ���� */
.login-btn {
  width: 100%;
  margin-top: 12px;
}

.login-btn :deep(span) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.login-btn :deep(.el-button) {
  width: 100%;
  height: 46px;
  font-family: var(--lumina-font-display);
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.3px;
  border: none;
  border-radius: var(--lumina-radius-md);
  background: linear-gradient(135deg, #4f46e5 0%, #3730a3 100%);
  box-shadow:
    0 4px 16px rgba(var(--lumina-primary-rgb), 0.35),
    0 0 0 0 rgba(var(--lumina-primary-rgb), 0);
  transition:
    transform var(--lumina-transition-base),
    box-shadow var(--lumina-transition-base),
    background var(--lumina-transition-base);
  cursor: pointer;
}

.login-btn :deep(.el-button:hover) {
  transform: translateY(-1px) scale(1.02);
  background: linear-gradient(135deg, #8b5cf6 0%, #4338ca 100%);
  box-shadow:
    0 6px 24px rgba(var(--lumina-primary-rgb), 0.45),
    0 0 0 3px rgba(var(--lumina-primary-rgb), 0.15);
}

.login-btn :deep(.el-button:active) {
  transform: translateY(0) scale(0.98);
}

.login-btn :deep(.el-button.is-loading) {
  background: linear-gradient(135deg, #4338ca 0%, #3730a3 100%);
}

.login-btn :deep(.el-button.is-loading::before) {
  background: rgba(15, 23, 42, 0.4);
}

/* ============================================
   Footer hint
   ============================================ */
.login-footer-text {
  text-align: center;
  margin: 24px 0 0 0;
  font-size: 12px;
  color: var(--lumina-text-muted);
  opacity: 0.6;
}

/* ============================================
   Form validation error styling
   ============================================ */
.login-form :deep(.el-form-item__error) {
  color: var(--lumina-danger-light);
  font-size: 12px;
  padding-top: 4px;
}
</style>
