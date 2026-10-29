<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { changeMyPassword } from '../api/security'

const auth = useAuthStore()
const router = useRouter()

const form = ref({ oldPassword: '', newPassword: '', confirm: '' })
const error = ref('')
const done = ref(false)
const loading = ref(false)

const forced = computed(() => !!auth.user?.mustChangePassword)

async function submit() {
  error.value = ''
  if (form.value.newPassword !== form.value.confirm) {
    error.value = '两次输入的新密码不一致'
    return
  }
  loading.value = true
  try {
    await changeMyPassword(form.value.oldPassword, form.value.newPassword)
    done.value = true
    await auth.fetchMe().catch(() => {})
    setTimeout(() => router.replace(auth.isAdmin ? '/users' : '/portal'), 800)
  } catch (e) {
    error.value = e.code === 40101 ? '当前密码不正确' : e.message
  } finally {
    loading.value = false
  }
}

async function signOut() {
  await auth.logout()
  router.replace('/login')
}
</script>

<template>
  <div class="card" style="max-width: 420px; margin: 0 auto;">
    <h2 style="margin-top: 0;">修改密码</h2>

    <p v-if="forced" class="warn-box">
      当前使用的是初始密码，请先设置新密码后再使用平台。
    </p>

    <template v-if="!done">
      <form @submit.prevent="submit">
        <label>当前密码</label>
        <input v-model="form.oldPassword" type="password" autocomplete="current-password" required />
        <label>新密码（≥8 位，含大小写/数字/符号中的 3 类）</label>
        <input v-model="form.newPassword" type="password" autocomplete="new-password" required />
        <label>确认新密码</label>
        <input v-model="form.confirm" type="password" autocomplete="new-password" required />
        <button type="submit" :disabled="loading">{{ loading ? '提交中…' : '确认修改' }}</button>
        <button type="button" class="secondary" @click="signOut">退出登录</button>
        <p v-if="error" class="error">{{ error }}</p>
      </form>
      <p class="muted" style="margin-top: 12px;">
        修改成功后，该账号在其他设备上的登录会立即失效。
      </p>
    </template>

    <p v-else class="ok-box">密码已更新，正在跳转…</p>
  </div>
</template>

<style scoped>
.warn-box {
  background: #fff7ed; border: 1px solid #fdba74; color: #9a3412;
  padding: 10px 12px; border-radius: 6px; font-size: 13px; margin: 0 0 14px;
}
.ok-box {
  background: #ecfdf5; border: 1px solid #6ee7b7; color: #047857;
  padding: 10px 12px; border-radius: 6px; font-size: 13px;
}
</style>
