/**
 * i18n 配置
 */
import { createI18n } from 'vue-i18n'
import zhCN from './zh-CN'
import en from './en'

const i18n = createI18n({
  legacy: false,
  locale: localStorage.getItem('lumina-lang') || 'zh-CN',
  fallbackLocale: 'zh-CN',
  messages: {
    'zh-CN': zhCN,
    'en': en
  }
})

export default i18n
