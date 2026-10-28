<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { startOidcLogin } from '../api/oidc'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

onMounted(() => {
  // refresh CSRF material silently on the login page
  auth.bootstrap().catch(() => {})
})

async function submit() {
  error.value = ''
  loading.value = true
  try {
    const target = route.query.redirect || '/'
    const redirect = await auth.login(username.value, password.value, target)
    router.replace(redirect || target)
  } catch (e) {
    error.value = mapError(e)
  } finally {
    loading.value = false
  }
}

function mapError(e) {
  switch (e.code) {
    case 40101: return '用户名或密码错误'
    case 40102: return '账号已被锁定或禁用，请联系管理员'
    case 40103: return '密码已过期，请联系管理员重置'
    default: return e.message || '登录失败'
  }
}
</script>

<template>
  <div class="card" style="max-width: 380px; margin: 0 auto;">
    <h2 style="margin-top: 0;">登录</h2>
    <form @submit.prevent="submit">
      <label>用户名</label>
      <input v-model="username" autocomplete="username" required />
      <label>密码</label>
      <input v-model="password" type="password" autocomplete="current-password" required />
      <button type="submit" :disabled="loading">
        {{ loading ? '登录中…' : '登 录' }}
      </button>
      <button type="button" class="secondary" @click="startOidcLogin()">
        OIDC 授权码流程（SSO 演示）
      </button>
      <p v-if="error" class="error">{{ error }}</p>
    </form>
    <p class="muted" style="margin-top: 16px;">
      演示账号：admin / admin123（JDBC 模式下来自种子数据）
    </p>
  </div>
</template>
