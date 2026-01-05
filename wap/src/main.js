import { createApp } from 'vue'
import App from './App.vue'
import './style.css'   // 一定要有
import { i18n } from './i18n'

createApp(App)
  .use(i18n)
  .mount('#app')