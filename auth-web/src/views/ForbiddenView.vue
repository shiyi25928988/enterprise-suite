<script setup>
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
</script>

<template>
  <div class="card" style="max-width: 480px; margin: 0 auto; text-align: center;">
    <h2 style="margin-top: 0;">403 · 无访问权限</h2>
    <p>用户组件管理后台仅对 <b>管理员（ADMIN 角色）</b> 开放。</p>
    <p class="muted">当前账号：{{ auth.user?.username || '—' }}，请联系管理员分配 ADMIN 角色。</p>
    <button @click="auth.logout().then(() => router.replace('/login'))">切换账号</button>
  </div>
</template>
