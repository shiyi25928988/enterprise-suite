<script setup>
import { computed, onMounted, ref } from 'vue'
import { listSessions, revokeSession, revokeUserSessions } from '../../api/security'

const supported = ref(true)
const rows = ref([])
const loading = ref(false)
const error = ref('')
const notice = ref('')

const users = computed(() => {
  const seen = new Map()
  for (const r of rows.value) {
    const cur = seen.get(r.username)
    if (!cur) seen.set(r.username, { username: r.username, realName: r.realName, count: 1 })
    else cur.count++
  }
  return [...seen.values()]
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await listSessions()
    supported.value = data.supported
    rows.value = data.rows || []
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

async function kick(row) {
  if (!confirm(`确认让 ${row.username} 的该会话立即下线？`)) return
  try {
    const n = await revokeSession(row.sessionId)
    notice.value = n > 0 ? '已强制下线' : '会话已失效'
    load()
  } catch (e) {
    error.value = e.message
  }
}

async function kickUser(username) {
  if (!confirm(`确认让 ${username} 的所有会话立即下线？`)) return
  try {
    const n = await revokeUserSessions(username)
    notice.value = `已强制下线 ${n} 个会话`
    load()
  } catch (e) {
    error.value = e.message
  }
}

function time(value) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'
}

function idle(seconds) {
  if (seconds == null) return '—'
  if (seconds < 60) return `${seconds} 秒`
  if (seconds < 3600) return `${Math.floor(seconds / 60)} 分钟`
  return `${Math.floor(seconds / 3600)} 小时 ${Math.floor((seconds % 3600) / 60)} 分`
}

onMounted(load)
</script>

<template>
  <div class="card">
    <div class="toolbar">
      <h3 style="margin: 0;">在线会话（{{ rows.length }}）</h3>
      <button style="width: auto; margin-left: auto; padding: 8px 18px;" :disabled="loading" @click="load">
        {{ loading ? '刷新中…' : '刷新' }}
      </button>
    </div>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="notice" class="muted">{{ notice }}</p>

    <p v-if="!supported" class="warn-box">
      当前未启用 Redis 会话存储（demo 模式），无法枚举在线会话。请以 <code>redis</code> profile 启动后使用。
    </p>

    <template v-else>
      <p v-if="!rows.length" class="muted">当前没有活跃会话。</p>

      <table v-else>
        <thead>
          <tr>
            <th>用户</th><th>姓名</th><th>IP</th><th>客户端</th>
            <th>登录时间</th><th>最后活动</th><th>空闲</th><th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.sessionId">
            <td>
              {{ row.username }}
              <span v-if="row.current" class="badge">当前</span>
            </td>
            <td>{{ row.realName || '—' }}</td>
            <td class="muted">{{ row.ip || '—' }}</td>
            <td class="muted ua" :title="row.userAgent">{{ row.userAgent || '—' }}</td>
            <td class="muted">{{ time(row.loginAt) }}</td>
            <td class="muted">{{ time(row.lastAccessedAt) }}</td>
            <td class="muted">{{ idle(row.idleSeconds) }}</td>
            <td class="actions">
              <a @click.prevent="kick(row)">下线</a>
              <a @click.prevent="kickUser(row.username)">该用户全部</a>
            </td>
          </tr>
        </tbody>
      </table>

      <p v-if="users.length" class="muted" style="margin-top: 12px;">
        涉及 {{ users.length }} 个账号：{{ users.map((u) => u.username).join('、') }}
      </p>
    </template>
  </div>
</template>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.warn-box {
  background: #fff7ed; border: 1px solid #fdba74; color: #9a3412;
  padding: 10px 12px; border-radius: 6px; font-size: 13px;
}
.ua {
  max-width: 220px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.badge {
  font-size: 11px; background: #e0f2fe; color: #0369a1;
  border-radius: 4px; padding: 1px 6px; margin-left: 4px;
}
</style>
