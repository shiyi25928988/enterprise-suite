<template>
  <div class="wrap">
    <h1>404</h1>
    <p>页面不存在或已被移除。</p>
    <a href="/" @click.prevent="back">返回首页</a>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
const router = useRouter()
function back() {
  const token = document.cookie.includes('JSESSIONID') || document.cookie.includes('SESSION')
  router.replace(token ? '/' : '/login')
}
</script>

<style scoped>
.wrap { min-height: 60vh; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 10px; }
h1 { font-size: 56px; margin: 0; color: var(--muted); }
a { color: var(--primary); cursor: pointer; }
</style>
