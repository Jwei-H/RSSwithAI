import { createApp } from 'vue'
import './style.css'
import 'highlight.js/styles/github.css'
import App from './App.vue'
import router from './router'
import { initSession } from './stores/session'

initSession()

createApp(App).use(router).mount('#app')
