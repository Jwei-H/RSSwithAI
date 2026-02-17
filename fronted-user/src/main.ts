import { createApp } from 'vue'
import './style.css'
import 'highlight.js/styles/github.css'
import App from './App.vue'
import router from './router'
import { initSession } from './stores/session'
import { useThemeStore } from './stores/theme'
import { registerPwa } from './pwa'

initSession()
useThemeStore().initTheme()
registerPwa()

createApp(App).use(router).mount('#app')
