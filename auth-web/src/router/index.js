import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes = [
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue') },
  { path: '/callback', name: 'callback', component: () => import('../views/CallbackView.vue') },
  { path: '/wecom-callback', name: 'wecom-callback', component: () => import('../views/WecomCallbackView.vue') },
  // The admin console itself is admin-only (also enforced server-side via
  // @PreAuthorize("hasRole('ADMIN')") on every /api/v1/** endpoint).
  {
    path: '/',
    component: () => import('../layouts/AdminLayout.vue'),
    meta: { requiresAuth: true, requiresAdmin: true },
    children: [
      { path: '', redirect: '/users' },
      { path: 'users', name: 'users', component: () => import('../views/admin/UsersView.vue') },
      { path: 'orgs', name: 'orgs', component: () => import('../views/admin/OrgView.vue') },
      { path: 'roles', name: 'roles', component: () => import('../views/admin/RolesView.vue') },
      { path: 'security', name: 'security', component: () => import('../views/admin/SecurityView.vue') },
    ],
  },
  { path: '/403', name: 'forbidden', component: () => import('../views/ForbiddenView.vue') },
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  // silent session recovery on hard refresh (JSESSIONID still valid)
  if (!auth.user && !auth.loaded) {
    await auth.bootstrap().catch(() => {})
  }
  if (to.meta.requiresAuth && !auth.user) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresAdmin && auth.user && !auth.isAdmin) {
    return { name: 'forbidden' }
  }
  return true
})

export default router
