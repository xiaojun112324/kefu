import { createI18n } from 'vue-i18n'

// 自动加载 /src/i18n/locales 下的语言包
const modules = import.meta.glob('./locales/*.ts', { eager: true }) as Record<string, any>
const messages: Record<string, any> = {}

Object.keys(modules).forEach((path) => {
  const locale = path.match(/locales\/(.*)\.ts$/)![1]
  messages[locale] = modules[path].default
})

/** 浏览器/缓存语言推断 */
function resolveLocale() {
  const saved = localStorage.getItem('app-locale')
  if (saved && messages[saved]) return saved
  const nav = navigator.language || 'en'
  // 简单映射
  if (nav.toLowerCase().startsWith('zh-cn')) return messages['zh-CN'] ? 'zh-CN' : 'zh'
  if (nav.toLowerCase().startsWith('zh')) return messages['zh-TW'] ? 'zh-TW' : 'zh-CN'
  const short = nav.split('-')[0]
  return messages[short] ? short : 'en'
}

export const i18n = createI18n({
  legacy: false,
  locale: resolveLocale(),
  fallbackLocale: 'en',
  messages
})

export function setLocale(locale: string) {
  if (messages[locale]) {
    i18n.global.locale.value = locale
    localStorage.setItem('app-locale', locale)
  }
}
