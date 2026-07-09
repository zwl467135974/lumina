/**
 * 应用入口
 */
import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import pinia from './stores'
import { useAppStore } from './stores/modules/app'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import i18n from '@/locales'
import '@/assets/styles/index.scss'
import setupDirectives from '@/directives'

const app = createApp(App)

// 注册 Element Plus 图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(pinia)

// 初始化主题（从 localStorage 恢复）
useAppStore().initTheme()

app.use(router)
app.use(i18n)
app.use(ElementPlus)

// 注册自定义指令
setupDirectives(app)

app.mount('#app')
