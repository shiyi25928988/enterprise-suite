<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { inbox, markRead, markAllRead, announce } from '../api/message'

const auth = useAuthStore()
const router = useRouter()

const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = 20
const unreadOnly = ref(false)
const error = ref('')
const notice = ref('')

// admin announcement form
const annTitle = ref('')
const annBody = ref('')
const annLink = ref('')
const annRole = ref('')
const posting = ref(false)

async function load() {
  error.value = ''
  try {
    const data = await inbox(unreadOnly.value, page.value, size)
    rows.value = data.rows
    total.value = data.total
  } catch (e) {
    error.value = e.message
  }
}

async function toggleUnreadOnly() {
  unreadOnly.value = !unreadOnly.value
  page.value = 1
  await load()
}

function setPage(p) {
  page.value = p
  load()
}

const bump = () => window.dispatchEvent(new CustomEvent('messages:changed'))

async function readOne(m) {
  if (m.read) {
    if (m.link) router.push(m.link)
    return
  }
  try {
    await markRead(m.id)
    m.read = true
    bump()
    if (m.link) router.push(m.link)
  } catch (e) {
    error.value = e.message
  }
}

async function readAll() {
  error.value = ''
  try {
    await markAllRead()
    bump()
    await load()
  } catch (e) {
    error.value = e.message
  }
}

async function postAnnounce() {
  error.value = ''
  notice.value = ''
  if (!annTitle.value.trim()) {
    error.value = '公告标题不能为空'
    return
  }
  posting.value = true
  try {
    const r = await announce({
      title: annTitle.value.trim(),
      body: annBody.value || null,
      link: annLink.value || null,
      roleCode: annRole.value || null,
    })
    notice.value = `已发送给 ${r.delivered} 名用户`
    annTitle.value = ''
    annBody.value = ''
    annLink.value = ''
    page.value = 1
    unreadOnly.value = false
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    posting.value = false
  }
}

const totalPages = () => Math.max(1, Math.ceil(total.value / size))

function fmt(t) {
  return (t || '').replace('T', ' ').slice(0, 16)
}

onMounted(load)
</script>

<template>
  <div class="card">
    <div class="head">
      <h2>消息中心</h2>
      <div>
        <button class="secondary mini" @click="toggleUnreadOnly">
          {{ unreadOnly ? '查看全部' : '只看未读' }}
        </button>
        <button class="secondary mini" @click="readAll">全部已读</button>
      </div>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="notice" class="muted">{{ notice }}</p>

    <table>
      <thead>
        <tr>
          <th style="width: 60px">状态</th>
          <th>标题 / 正文</th>
          <th style="width: 90px">类型</th>
          <th style="width: 130px">时间</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="m in rows" :key="m.id" :class="{ unread: !m.read }" @click="readOne(m)">
          <td>{{ m.read ? '已读' : '未读' }}</td>
          <td>
            <div class="title">{{ m.title }}</div>
            <div v-if="m.body" class="muted">{{ m.body }}</div>
          </td>
          <td>{{ m.type === 'announcement' ? '公告' : '通知' }}</td>
          <td class="muted">{{ fmt(m.createdAt) }}</td>
        </tr>
        <tr v-if="rows.length === 0">
          <td colspan="4" class="muted" style="text-align: center">暂无消息</td>
        </tr>
      </tbody>
    </table>

    <div class="pager" v-if="totalPages() > 1">
      <button class="secondary mini" :disabled="page <= 1" @click="setPage(page - 1)">上一页</button>
      <span class="muted">{{ page }} / {{ totalPages() }}</span>
      <button class="secondary mini" :disabled="page >= totalPages()" @click="setPage(page + 1)">下一页</button>
    </div>

    <template v-if="auth.isAdmin">
      <h3>发布公告</h3>
      <label>标题</label>
      <input v-model="annTitle" maxlength="200" placeholder="公告标题" />
      <label>正文</label>
      <input v-model="annBody" maxlength="1000" placeholder="正文（可选）" />
      <label>跳转链接</label>
      <input v-model="annLink" placeholder="/flow/todo（可选）" />
      <label>接收范围</label>
      <select v-model="annRole">
        <option value="">全员</option>
        <option value="ADMIN">仅 ADMIN 角色</option>
        <option value="EMPLOYEE">EMPLOYEE 角色</option>
        <option value="WAREHOUSE">WAREHOUSE 角色</option>
      </select>
      <button class="mini" :disabled="posting" @click="postAnnounce">发送</button>
    </template>
  </div>
</template>

<style scoped>
.head { display: flex; justify-content: space-between; align-items: center; }
h2 { margin: 0; font-size: 16px; }
.head button { width: auto; margin: 0 0 0 8px; padding: 6px 14px; font-size: 13px; }
table { width: 100%; border-collapse: collapse; margin-top: 16px; }
th, td { text-align: left; padding: 10px 8px; border-bottom: 1px solid var(--border); font-size: 14px; }
th { color: var(--muted); font-weight: 500; font-size: 13px; }
tbody tr { cursor: pointer; }
tbody tr:hover { background: #f8fafc; }
tbody tr.unread .title { font-weight: 600; }
.title { font-size: 14px; }
.pager { display: flex; gap: 12px; align-items: center; justify-content: center; margin-top: 16px; }
.pager button { width: auto; margin: 0; padding: 6px 14px; font-size: 13px; }
h3 { margin-top: 28px; font-size: 14px; }
select { width: 100%; padding: 8px 10px; border: 1px solid var(--border); border-radius: 6px; font-size: 14px; }
button.mini { width: auto; }
</style>
