<script setup>
import { onMounted, ref } from 'vue'
import { exchangeCode, parseCallbackQuery } from '../api/oidc'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const message = ref('正在完成授权…')
const error = ref('')

onMounted(async () => {
  const { code, state, error: authError } = parseCallbackQuery()
  if (authError) {
    error.value = `授权失败：${authError}`
    return
  }
  // state check: compare with what startOidcLogin stored
  const expected = sessionStorage.getItem('oidc.state')
  if (state && expected && state !== expected) {
    error.value = 'state 校验失败，请重新发起登录'
    return
  }
  try {
    const tokens = await exchangeCode(code)
    auth.setTokens(tokens)
    await auth.fetchMe().catch(() => {})
    message.value = '授权完成，正在跳转…'
    setTimeout(() => window.location.replace('/'), 600)
  } catch (e) {
    error.value = e.message
  }
})
</script>

<template>
  <div class="card" style="max-width: 480px; margin: 0 auto;">
    <p v-if="error" class="error">{{ error }}</p>
    <p v-else>{{ message }}</p>
  </div>
</template>
