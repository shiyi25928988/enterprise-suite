<script setup>
// M5c badge (工牌) login — the printed QR points here with ?code=<badgeCode>.
// Works on any terminal's browser: scan → auto-login. Manual entry fallback
// covers cameras that cannot open URLs.
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import http from '../api/http'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const error = ref('')
const busy = ref(false)
const badgeCode = ref('')

async function login(code) {
  if (!code) return
  busy.value = true
  error.value = ''
  try {
    await http.post('/api/auth/badge/login', { badgeCode: code })
    await auth.fetchMe().catch(() => {})
    router.replace('/')
  } catch (e) {
    error.value = e.code === 40101 ? '工牌码无效或已轮换' : (e.message || '工牌登录失败')
  } finally {
    busy.value = false
  }
}

onMounted(() => {
  const q = new URLSearchParams(window.location.search)
  if (q.get('code')) login(q.get('code').trim())
})
</script>

<template>
  <div class="card" style="max-width: 480px; margin: 0 auto;">
    <h2 style="margin-top: 0;">工牌登录</h2>
    <p v-if="!error" class="muted">正在识别工牌…</p>
    <p v-if="error" class="error">{{ error }}</p>
    <form @submit.prevent="login(badgeCode.trim())">
      <label>工牌码（也可手动输入）</label>
      <input v-model="badgeCode" autocomplete="off" spellcheck="false" />
      <button type="submit" :disabled="busy || !badgeCode.trim()">
        {{ busy ? '登录中…' : '登 录' }}
      </button>
    </form>
    <router-link to="/login" class="muted">返回登录页</router-link>
  </div>
</template>
