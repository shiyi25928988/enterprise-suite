import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes = [
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { bare: true } },
  { path: '/callback', name: 'callback', component: () => import('../views/CallbackView.vue'), meta: { bare: true } },
  { path: '/wecom-callback', name: 'wecom-callback', component: () => import('../views/WecomCallbackView.vue'), meta: { bare: true } },
  // Console: /portal is the employee entry (any authenticated user), the rest is
  // admin-only — also enforced server-side via @PreAuthorize on /api/v1/**.
  {
    path: '/',
    component: () => import('../layouts/AdminLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: 'portal', name: 'portal', component: () => import('../views/PortalView.vue') },
      { path: 'users', name: 'users', component: () => import('../views/admin/UsersView.vue'), meta: { requiresAdmin: true } },
      { path: 'orgs', name: 'orgs', component: () => import('../views/admin/OrgView.vue'), meta: { requiresAdmin: true } },
      { path: 'roles', name: 'roles', component: () => import('../views/admin/RolesView.vue'), meta: { requiresAdmin: true } },
      { path: 'apps', name: 'apps', component: () => import('../views/admin/AppsView.vue'), meta: { requiresAdmin: true } },
      { path: 'security', name: 'security', component: () => import('../views/admin/SecurityView.vue'), meta: { requiresAdmin: true } },
      { path: 'sync', name: 'sync', component: () => import('../views/admin/SyncView.vue'), meta: { requiresAdmin: true } },
    ],
  },
  { path: '/403', name: 'forbidden', component: () => import('../views/ForbiddenView.vue'), meta: { bare: true } },
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
  // landing page depends on the role: admins get the console, others the portal
  if (to.path === '/') {
    return auth.isAdmin ? { name: 'users' } : { name: 'portal' }
  }
  if (to.meta.requiresAdmin && auth.user && !auth.isAdmin) {
    return { name: 'forbidden' }
  }
  return true
})

export default router
