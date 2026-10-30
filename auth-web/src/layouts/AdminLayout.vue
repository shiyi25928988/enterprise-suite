<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import * as messages from '../api/message'

const auth = useAuthStore()
const router = useRouter()

const displayName = computed(() => auth.user?.realName || auth.user?.username || '')
const roles = computed(() =>
  (auth.user?.authorities || []).map((a) => (typeof a === 'string' ? a : a.authority)),
)

// M11 message center: unread badge + recent-messages dropdown, polled every
// 30s. Cheap by design — one indexed count per tick.
const unread = ref(0)
const panelOpen = ref(false)
const recent = ref([])
let timer = null

async function refreshUnread() {
  try {
    unread.value = await messages.unreadCount()
  } catch {
    /* logged-out or transient failure — badge just keeps its old value */
  }
}

// MessagesView dispatches this after marking read, so the badge keeps up
// instantly instead of waiting for the next poll tick.
function onMessagesChanged() {
  refreshUnread()
}

async function togglePanel() {
  panelOpen.value = !panelOpen.value
  if (panelOpen.value) {
    try {
      const data = await messages.inbox(false, 1, 8)
      recent.value = data.rows
    } catch {
      recent.value = []
    }
  }
}

async function openMessage(m) {
  panelOpen.value = false
  if (!m.read) {
    try {
      await messages.markRead(m.id)
      refreshUnread()
    } catch {
      /* ignore — navigation matters more */
    }
  }
  if (m.link) router.push(m.link)
}

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
      { to: '/flow/analytics', label: '流程分析' },
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
  refreshUnread()
  timer = setInterval(refreshUnread, 30000)
  window.addEventListener('messages:changed', onMessagesChanged)
})

onUnmounted(() => {
  clearInterval(timer)
  window.removeEventListener('messages:changed', onMessagesChanged)
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
        <div class="bell-wrap">
          <button class="bell" title="消息中心" @click="togglePanel">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
              <path d="M13.73 21a2 2 0 0 1-3.46 0" />
            </svg>
            <span v-if="unread > 0" class="badge">{{ unread > 99 ? '99+' : unread }}</span>
          </button>
          <div v-if="panelOpen" class="panel">
            <div class="panel-head">
              <span>最新消息</span>
              <router-link to="/messages" @click="panelOpen = false">查看全部</router-link>
            </div>
            <div v-if="recent.length === 0" class="panel-empty">暂无消息</div>
            <div v-for="m in recent" :key="m.id" class="panel-item" :class="{ unread: !m.read }"
                 @click="openMessage(m)">
              <div class="panel-title">{{ m.title }}</div>
              <div class="panel-meta">{{ m.type === 'announcement' ? '公告' : '通知' }}
                · {{ (m.createdAt || '').replace('T', ' ').slice(0, 16) }}</div>
            </div>
          </div>
        </div>
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
.bell-wrap { position: relative; }
.bell {
  position: relative; width: 34px; height: 34px; margin: 0; padding: 6px;
  border: 1px solid var(--border); border-radius: 8px; background: #fff;
  cursor: pointer; color: #475569;
}
.bell:hover { background: #f1f5f9; }
.badge {
  position: absolute; top: -6px; right: -6px; min-width: 16px; height: 16px;
  padding: 0 4px; border-radius: 8px; background: #ef4444; color: #fff;
  font-size: 11px; line-height: 16px; text-align: center;
}
.panel {
  position: absolute; right: 0; top: 42px; width: 320px; z-index: 50;
  background: #fff; border: 1px solid var(--border); border-radius: 10px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.12);
}
.panel-head {
  display: flex; justify-content: space-between; align-items: center;
  padding: 10px 14px; font-size: 13px; font-weight: 600;
  border-bottom: 1px solid var(--border);
}
.panel-head a { font-size: 12px; font-weight: 400; }
.panel-empty { padding: 18px 14px; font-size: 13px; color: #94a3b8; text-align: center; }
.panel-item { padding: 10px 14px; border-bottom: 1px solid var(--border); cursor: pointer; }
.panel-item:last-child { border-bottom: none; }
.panel-item:hover { background: #f8fafc; }
.panel-item.unread .panel-title { font-weight: 600; }
.panel-title { font-size: 13px; color: #1e293b; }
.panel-meta { font-size: 12px; color: #94a3b8; margin-top: 3px; }
</style>
