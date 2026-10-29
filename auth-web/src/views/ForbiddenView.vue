<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

// Set by AppAccessFilter when the OIDC authorize call is blocked:
// /403?reason=NO_ROLE&app=<client_id>
const deniedApp = computed(() => route.query.app || '')
const reason = computed(() => route.query.reason || '')

const title = computed(() =>
  reason.value === 'APP_DISABLED' ? '403 · 应用已停用' : '403 · 无访问权限',
)

const detail = computed(() => {
  if (reason.value === 'APP_DISABLED') {
    return `应用 ${deniedApp.value} 已被管理员停用，暂时无法登录。`
  }
  if (reason.value === 'NO_ROLE') {
    return `你的账号未被授权访问应用 ${deniedApp.value}，请联系管理员为你的角色开通。`
  }
  return '当前账号没有访问该页面的权限。'
})
</script>

<template>
  <div class="card" style="max-width: 480px; margin: 0 auto; text-align: center;">
    <h2 style="margin-top: 0;">{{ title }}</h2>
    <p>{{ detail }}</p>
    <p class="muted">当前账号：{{ auth.user?.username || '—' }}</p>
    <div style="display: flex; gap: 10px; justify-content: center; margin-top: 16px;">
      <button style="width: auto; margin: 0; padding: 9px 18px;" @click="router.replace('/portal')">
        返回我的应用
      </button>
      <button class="secondary" style="width: auto; margin: 0; padding: 9px 18px;"
              @click="auth.logout().then(() => router.replace('/login'))">
        切换账号
      </button>
    </div>
  </div>
</template>
