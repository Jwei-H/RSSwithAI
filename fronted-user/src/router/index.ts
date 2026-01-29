import { createRouter, createWebHistory, type RouteLocationNormalized } from 'vue-router'
import { isAuthenticated } from '../stores/session'

const routes = [
  {
    path: '/',
    redirect: '/subscriptions'
  },
  {
    path: '/login',
    component: () => import('../views/LoginView.vue'),
    meta: { public: true }
  },
  {
    path: '/register',
    component: () => import('../views/RegisterView.vue'),
    meta: { public: true }
  },
  {
    path: '/subscriptions',
    component: () => import('../views/SubscriptionsView.vue')
  },
  {
    path: '/discover',
    component: () => import('../views/DiscoverView.vue')
  },
  {
    path: '/favorites',
    component: () => import('../views/FavoritesView.vue')
  },
  {
    path: '/profile',
    component: () => import('../views/ProfileView.vue')
  },
  {
    path: '/history',
    component: () => import('../views/HistoryView.vue')
  },
  {
    path: '/:pathMatch(.*)*',
    component: () => import('../views/NotFound.vue'),
    meta: { public: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to: RouteLocationNormalized) => {
  if (to.meta.public) return true
  if (isAuthenticated()) return true
  return { path: '/login', query: { redirect: to.fullPath } }
})

export default router
