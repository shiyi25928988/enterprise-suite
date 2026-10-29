<script setup>
import { computed, onMounted, ref } from 'vue'
import { myApps } from '../api/apps'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const apps = ref([])
const error = ref('')
const loading = ref(true)

const displayName = computed(() => auth.user?.realName || auth.user?.username || '')

async function load() {
  loading.value = true
  error.value = ''
  try {
    apps.value = await myApps()
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function open(app) {
  if (!app.homeUrl) return
  window.open(app.homeUrl, '_blank', 'noopener')
}

onMounted(load)
</script>

<template>
  <div class="portal">
    <div class="hero">
      <h2>我的应用</h2>
      <p class="muted">
        {{ displayName }}，以下是你的角色被授权访问的系统，点击即可免密进入（统一登录）。
      </p>
    </div>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="loading" class="muted">加载中…</p>

    <div class="grid">
      <button v-for="a in apps" :key="a.id" class="app-card" :disabled="!a.homeUrl" @click="open(a)">
        <span class="icon">{{ a.icon || '◆' }}</span>
        <span class="body">
          <b>{{ a.appName }}</b>
          <span class="muted">{{ a.description || a.appCode }}</span>
        </span>
        <span class="go">{{ a.homeUrl ? '进入 →' : '未配置入口' }}</span>
      </button>
    </div>

    <p v-if="!loading && !apps.length && !error" class="empty">
      暂时没有可访问的应用。请联系管理员为你的角色授权。
    </p>

    <p v-if="auth.isAdmin" class="muted" style="margin-top: 24px;">
      管理员：到
      <router-link to="/apps">应用接入</router-link>
      登记新系统、调整角色授权。
    </p>
  </div>
</template>

<style scoped>
.portal { padding: 28px 32px; max-width: 1080px; }
.hero h2 { margin: 0 0 6px; font-size: 20px; }
.hero { margin-bottom: 22px; }
.grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 14px;
}
.app-card {
  display: flex; align-items: center; gap: 12px; width: 100%; margin: 0;
  padding: 16px; background: #fff; border: 1px solid var(--border);
  border-radius: 10px; text-align: left; cursor: pointer; color: var(--text);
  transition: box-shadow .15s, transform .15s;
}
.app-card:not(:disabled):hover {
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.08); transform: translateY(-1px);
}
.app-card:disabled { cursor: default; opacity: .7; }
.icon {
  width: 40px; height: 40px; border-radius: 10px; background: #eff6ff;
  display: inline-flex; align-items: center; justify-content: center; font-size: 18px;
  flex: 0 0 auto;
}
.body { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
.body b { font-size: 14px; }
.body .muted { font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.go { margin-left: auto; font-size: 12px; color: var(--primary); flex: 0 0 auto; }
.empty {
  margin-top: 32px; padding: 28px; text-align: center; color: var(--muted);
  background: #fff; border: 1px dashed var(--border); border-radius: 10px; font-size: 14px;
}
</style>
