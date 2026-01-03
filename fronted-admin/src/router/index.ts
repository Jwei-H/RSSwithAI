// 路由配置
import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { isAuthenticated } from '@/utils/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    meta: { requiresAuth: true },
    redirect: '/rss-sources',
    children: [
      {
        path: '/rss-sources',
        name: 'RssSources',
        component: () => import('@/views/RssSources.vue'),
        meta: { title: 'RSS源管理' }
      },
      {
        path: '/articles',
        name: 'Articles',
        component: () => import('@/views/Articles.vue'),
        meta: { title: '文章管理' }
      },
      {
        path: '/articles/:id',
        name: 'ArticleDetail',
        component: () => import('@/views/ArticleDetail.vue'),
        meta: { title: '文章详情' }
      },
      {
        path: '/model-configs',
        name: 'ModelConfigs',
        component: () => import('@/views/ModelConfigs.vue'),
        meta: { title: '模型配置' }
      },
      {
        path: '/prompts',
        name: 'Prompts',
        component: () => import('@/views/Prompts.vue'),
        meta: { title: '提示词模板' }
      },
      {
        path: '/prompts/:id',
        name: 'PromptDetail',
        component: () => import('@/views/PromptDetail.vue'),
        meta: { title: '模板详情' }
      },
      {
        path: '/experiments',
        name: 'Experiments',
        component: () => import('@/views/Experiments.vue'),
        meta: { title: '实验管理' }
      },
      {
        path: '/experiments/:id',
        name: 'ExperimentDetail',
        component: () => import('@/views/ExperimentDetail.vue'),
        meta: { title: '实验详情' }
      },
      {
        path: '/settings',
        name: 'Settings',
        component: () => import('@/views/Settings.vue'),
        meta: { title: '系统配置' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const requiresAuth = to.matched.some(record => record.meta.requiresAuth !== false)
  
  if (requiresAuth && !isAuthenticated()) {
    next('/login')
  } else if (to.path === '/login' && isAuthenticated()) {
    next('/')
  } else {
    next()
  }
})

export default router
