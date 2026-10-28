<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const showToken = ref(false)

const displayName = computed(() => auth.user?.realName || auth.user?.username || '')
const roles = computed(() =>
  (auth.user?.authorities || []).map((a) => (typeof a === 'string' ? a : a.authority)),
)

onMounted(() => {
  if (!auth.user) auth.fetchMe().catch(() => {})
})

async function logout() {
  await auth.logout()
  router.replace('/login')
}
</script>

<template>
  <div class="card">
    <h2 style="margin-top: 0;">你好，{{ displayName }}</h2>
    <p class="muted">用户名：{{ auth.user?.username || '—' }}</p>
    <p>角色：<code v-if="roles.length">{{ roles.join(', ') }}</code><span v-else class="muted">无</span></p>

    <template v-if="auth.tokens">
      <p>
        OIDC 令牌已获取（仅保存在内存中）。
        <a href="#" @click.prevent="showToken = !showToken">{{ showToken ? '隐藏' : '查看' }} access_token</a>
      </p>
      <pre v-if="showToken" style="white-space: pre-wrap; word-break: break-all; background: #f1f5f9; padding: 12px; border-radius: 6px; font-size: 12px;">{{ auth.tokens.access_token }}</pre>
    </template>
    <p v-else class="muted">
      当前为 Session 登录。在登录页点击「OIDC 授权码流程」可演示完整 SSO 令牌签发。
    </p>

    <button @click="logout">退出登录</button>
  </div>
</template>
