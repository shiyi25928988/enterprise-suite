<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { startOidcLogin } from '../api/oidc'
import http from '../api/http'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)
const wecomEnabled = ref(false)
const wecomConfig = ref(null)
const dingtalkEnabled = ref(false)
const dingtalkConfig = ref(null)

// second factor state
const mfa = reactive({ challengeId: '', code: '' })

onMounted(async () => {
  auth.bootstrap().catch(() => {})
  try {
    const cfg = await http.get('/api/auth/wecom/config').then((r) => r.data?.data)
    wecomEnabled.value = !!cfg?.enabled
    wecomConfig.value = cfg
  } catch { /* ignore */ }
  try {
    const cfg = await http.get('/api/auth/dingtalk/config').then((r) => r.data?.data)
    dingtalkEnabled.value = !!cfg?.enabled
    dingtalkConfig.value = cfg
  } catch { /* ignore */ }
})

async function submit() {
  error.value = ''
  loading.value = true
  try {
    const target = route.query.redirect || '/'
    const redirect = await auth.login(username.value, password.value, target)
    router.replace(redirect || target)
  } catch (e) {
    if (e.code === 40110) {
      // password OK, TOTP required
      mfa.challengeId = e.data?.challengeId || ''
      mfa.code = ''
      if (!mfa.challengeId) error.value = '服务器未返回 MFA 挑战，请重试'
    } else {
      error.value = mapError(e)
    }
  } finally {
    loading.value = false
  }
}

async function submitMfa() {
  error.value = ''
  loading.value = true
  try {
    await http.post('/api/auth/mfa/verify', {
      challengeId: mfa.challengeId, code: mfa.code,
    })
    await auth.fetchMe().catch(() => {})
    router.replace(route.query.redirect || '/')
  } catch (e) {
    error.value = e.code === 40112
      ? '验证已超时，请重新登录'
      : (e.code === 40111 ? '动态码不正确' : (e.message || '验证失败'))
    if (e.code === 40112) mfa.challengeId = ''
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

function randomState() {
  return btoa(String.fromCharCode(...crypto.getRandomValues(new Uint8Array(12))))
    .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

async function startWecomLogin() {
  const cfg = wecomConfig.value || {}
  const redirectUri = cfg.redirectUri || `${window.location.origin}/wecom-callback`
  const state = randomState()
  sessionStorage.setItem('wecom.state', state)
  const params = new URLSearchParams({
    login_type: 'CorpApp',
    appid: cfg.corpId,
    agentid: cfg.agentId,
    redirect_uri: redirectUri,
    state,
  })
  window.location.assign(
    `https://login.work.weixin.qq.com/wwlogin/sso/login?${params}`)
}

// M5c: DingTalk QR login — the code lands on /dingtalk-callback
function startDingtalkLogin() {
  const cfg = dingtalkConfig.value || {}
  const redirectUri = cfg.redirectUri || `${window.location.origin}/dingtalk-callback`
  const state = randomState()
  sessionStorage.setItem('dingtalk.state', state)
  const params = new URLSearchParams({
    redirect_uri: redirectUri,
    response_type: 'code',
    client_id: cfg.appKey,
    scope: 'openid',
    state,
    prompt: 'consent',
  })
  window.location.assign(`https://login.dingtalk.com/oauth2/auth?${params}`)
}
</script>

<template>
  <div class="card" style="max-width: 380px; margin: 0 auto;">
    <template v-if="!mfa.challengeId">
      <h2 style="margin-top: 0;">登录</h2>
      <form @submit.prevent="submit">
        <label>用户名</label>
        <input v-model="username" autocomplete="username" required />
        <label>密码</label>
        <input v-model="password" type="password" autocomplete="current-password" required />
        <button type="submit" :disabled="loading">
          {{ loading ? '登录中…' : '登 录' }}
        </button>
        <button v-if="wecomEnabled" type="button" class="secondary" @click="startWecomLogin">
          企业微信扫码登录
        </button>
        <button v-if="dingtalkEnabled" type="button" class="secondary" @click="startDingtalkLogin">
          钉钉扫码登录
        </button>
        <button type="button" class="secondary" @click="startOidcLogin()">
          OIDC 授权码流程（SSO 演示）
        </button>
        <p v-if="error" class="error">{{ error }}</p>
      </form>
      <p class="muted" style="margin-top: 16px;">
        演示账号：admin / admin123（JDBC 模式下来自种子数据）
      </p>
    </template>

    <template v-else>
      <h2 style="margin-top: 0;">两步验证</h2>
      <p class="muted">请输入身份验证器 App 中的 6 位动态码。</p>
      <form @submit.prevent="submitMfa">
        <label>动态码</label>
        <input v-model="mfa.code" inputmode="numeric" maxlength="6"
               autocomplete="one-time-code" pattern="[0-9]{6}" required />
        <button type="submit" :disabled="loading">
          {{ loading ? '验证中…' : '验 证' }}
        </button>
        <button type="button" class="secondary" @click="mfa.challengeId = ''">返回</button>
        <p v-if="error" class="error">{{ error }}</p>
      </form>
    </template>
  </div>
</template>
