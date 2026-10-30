<script setup>
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()

const displayName = computed(() => auth.user?.realName || auth.user?.username || '')
const roles = computed(() =>
  (auth.user?.authorities || []).map((a) => (typeof a === 'string' ? a : a.authority)),
)

// Console entries are admin-only; the portal stays visible for everyone so a
// non-admin still has a landing page after login.
const menu = computed(() => {
  const items = [
    { to: '/portal', label: '我的应用' },
    { to: '/flow/requests', label: '我的申请' },
    { to: '/flow/todo', label: '审批中心' },
  ]
  if (auth.isAdmin) {
    items.push(
      { to: '/users', label: '用户管理' },
      { to: '/orgs', label: '组织管理' },
      { to: '/roles', label: '角色管理' },
      { to: '/apps', label: '应用接入' },
      { to: '/flow/admin', label: '流程管理' },
      { to: '/sessions', label: '在线会话' },
      { to: '/sync', label: '目录同步' },
      { to: '/audit', label: '操作日志' },
      { to: '/security', label: '安全设置' },
    )
  }
  items.push({ to: '/password', label: '修改密码' })
  return items
})

onMounted(() => {
  if (!auth.user) auth.fetchMe().catch(() => {})
})

async function logout() {
  await auth.logout()
  router.replace('/login')
}
</script>

<template>
  <div class="layout">
    <aside class="sidebar">
      <div class="logo">Enterprise Suite</div>
      <nav>
        <router-link v-for="item in menu" :key="item.to" :to="item.to">{{ item.label }}</router-link>
      </nav>
    </aside>
    <div class="main">
      <header class="header">
        <span class="muted">
          {{ displayName }} · {{ roles.join(', ') || '无角色' }}
        </span>
        <button class="mini" @click="logout">退出登录</button>
      </header>
      <main class="content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.layout { display: flex; min-height: 100vh; }
.sidebar {
  width: 200px; background: #0f172a; color: #cbd5e1;
  display: flex; flex-direction: column;
}
.logo { padding: 20px 18px; font-weight: 600; color: #fff; font-size: 15px; }
nav { display: flex; flex-direction: column; }
nav a {
  padding: 11px 18px; color: #cbd5e1; text-decoration: none; font-size: 14px;
}
nav a.router-link-active { background: #1e293b; color: #fff; border-right: 3px solid var(--primary); }
.main { flex: 1; display: flex; flex-direction: column; }
.header {
  height: 52px; background: #fff; border-bottom: 1px solid var(--border);
  display: flex; align-items: center; justify-content: flex-end; gap: 12px; padding: 0 24px;
}
.content { padding: 24px; flex: 1; }
button.mini {
  width: auto; margin: 0; padding: 6px 14px; font-size: 13px;
}
</style>
