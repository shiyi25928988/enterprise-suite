import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes = [
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue') },
  { path: '/callback', name: 'callback', component: () => import('../views/CallbackView.vue') },
  {
    path: '/',
    component: () => import('../layouts/AdminLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', redirect: '/users' },
      { path: 'users', name: 'users', component: () => import('../views/admin/UsersView.vue') },
      { path: 'orgs', name: 'orgs', component: () => import('../views/admin/OrgView.vue') },
      { path: 'roles', name: 'roles', component: () => import('../views/admin/RolesView.vue') },
    ],
  },
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
