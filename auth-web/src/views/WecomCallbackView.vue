<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import http from '../api/http'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const error = ref('')

onMounted(async () => {
  const q = new URLSearchParams(window.location.search)
  const code = q.get('code')
  const state = q.get('state')
  if (!code) {
    error.value = `企微登录失败：${q.get('errmsg') || q.get('error') || 'missing code'}`
    return
  }
  const expected = sessionStorage.getItem('wecom.state')
  if (state && expected && state !== expected) {
    error.value = 'state 校验失败，请重新扫码'
    return
  }
  try {
    await http.post('/api/auth/wecom/login', { code })
    sessionStorage.removeItem('wecom.state')
    await auth.fetchMe().catch(() => {})
    router.replace('/')
  } catch (e) {
    error.value = e.code === 40101
      ? '该企微账号未绑定平台用户，请先用账号密码登录或联系管理员'
      : (e.message || '企微登录失败')
  }
})
</script>

<template>
  <div class="card" style="max-width: 480px; margin: 0 auto;">
    <p v-if="error" class="error">{{ error }}</p>
    <p v-else class="muted">正在完成企业微信登录…</p>
    <router-link to="/login" class="muted">返回登录页</router-link>
  </div>
</template>
