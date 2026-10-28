<script setup>
import { onMounted, reactive, ref } from 'vue'
import http from '../../api/http'

const status = ref({ enabled: false, username: '' })
const error = ref('')
const setup = reactive({ secret: '', otpauthUri: '' })
const code = reactive({ confirm: '', disable: '' })
const step = ref('idle') // idle | scanning

async function load() {
  error.value = ''
  try {
    status.value = await http.get('/api/auth/mfa/status').then((r) => r.data.data)
  } catch (e) {
    error.value = e.message
  }
}

async function startSetup() {
  error.value = ''
  try {
    const data = await http.post('/api/auth/mfa/setup').then((r) => r.data.data)
    setup.secret = data.secret
    setup.otpauthUri = data.otpauthUri
    step.value = 'scanning'
  } catch (e) {
    error.value = e.message
  }
}

async function confirmSetup() {
  error.value = ''
  try {
    await http.post('/api/auth/mfa/confirm', { code: code.confirm })
    step.value = 'idle'
    code.confirm = ''
    await load()
  } catch (e) {
    error.value = e.code === 40111 ? '动态码不正确' : e.message
  }
}

async function disable() {
  error.value = ''
  try {
    await http.post('/api/auth/mfa/disable', { code: code.disable })
    code.disable = ''
    await load()
  } catch (e) {
    error.value = e.code === 40111 ? '动态码不正确' : e.message
  }
}

onMounted(load)
</script>

<template>
  <div class="card" style="max-width: 560px;">
    <h3 style="margin-top: 0;">安全设置 · 两步验证（TOTP）</h3>
    <p v-if="error" class="error">{{ error }}</p>

    <p>
      当前状态：
      <span :class="['badge', status.enabled ? 'on' : 'off']">
        {{ status.enabled ? '已开启' : '未开启' }}
      </span>
      <span class="muted">（账号：{{ status.username }}）</span>
    </p>

    <template v-if="!status.enabled && step === 'idle'">
      <p class="muted">
        管理员账号建议开启。开启后登录需输入身份验证器（Google / Microsoft / 企业微信）中的 6 位动态码。
      </p>
      <button @click="startSetup">开启两步验证</button>
    </template>

    <template v-if="step === 'scanning'">
      <p>1. 在身份验证器 App 中添加账号，手动输入以下密钥（Base32）：</p>
      <pre class="secret">{{ setup.secret }}</pre>
      <p class="muted">或使用 otpauth URI 生成二维码：</p>
      <pre class="uri">{{ setup.otpauthUri }}</pre>
      <p>2. 输入 App 显示的 6 位动态码完成绑定：</p>
      <input v-model="code.confirm" inputmode="numeric" maxlength="6" placeholder="6 位动态码" />
      <button @click="confirmSetup">确认开启</button>
      <button class="secondary" @click="step = 'idle'">取消</button>
    </template>

    <template v-if="status.enabled">
      <p class="muted">如需关闭，请输入当前动态码确认。</p>
      <input v-model="code.disable" inputmode="numeric" maxlength="6" placeholder="6 位动态码" />
      <button @click="disable">关闭两步验证</button>
    </template>
  </div>
</template>

<style scoped>
.badge { padding: 2px 8px; border-radius: 10px; font-size: 12px; }
.on { background: #dcfce7; color: #166534; }
.off { background: #f1f5f9; color: #475569; }
.secret, .uri {
  background: #f1f5f9; padding: 10px; border-radius: 6px;
  font-size: 12px; word-break: break-all; white-space: pre-wrap;
}
</style>
