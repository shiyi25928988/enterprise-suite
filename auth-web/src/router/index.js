import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes = [
  { path: '/', name: 'home', component: () => import('../views/HomeView.vue'), meta: { requiresAuth: true } },
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue') },
  { path: '/callback', name: 'callback', component: () => import('../views/CallbackView.vue') },
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.user) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
