<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

// "bare" routes (login/callbacks/403) render standalone cards inside the
// narrow centered container; the admin console needs the full viewport.
const route = useRoute()
const wide = computed(() => !route.meta.bare)
</script>

<template>
  <header class="topbar">
    <span class="brand">Enterprise Suite · Auth</span>
  </header>
  <main class="container" :class="{ wide }">
    <router-view />
  </main>
</template>

<style>
:root {
  --primary: #2563eb;
  --border: #e5e7eb;
  --text: #111827;
  --muted: #6b7280;
}
* { box-sizing: border-box; }
body {
  margin: 0;
  font-family: 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  color: var(--text);
  background: #f8fafc;
}
.topbar {
  height: 48px;
  display: flex;
  align-items: center;
  padding: 0 24px;
  background: #fff;
  border-bottom: 1px solid var(--border);
}
.brand { font-weight: 600; }
/* narrow centered container for standalone auth pages */
.container { max-width: 720px; margin: 48px auto; padding: 0 16px; }
/* full-bleed for the admin console layout (sidebar + content) */
.container.wide { max-width: none; margin: 0; padding: 0; }
.card {
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 24px;
}
label { display: block; margin: 12px 0 4px; font-size: 14px; color: var(--muted); }
input {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid var(--border);
  border-radius: 6px;
  font-size: 14px;
}
button {
  margin-top: 16px;
  width: 100%;
  padding: 10px;
  border: none;
  border-radius: 6px;
  background: var(--primary);
  color: #fff;
  font-size: 14px;
  cursor: pointer;
}
button.secondary {
  background: #fff;
  color: var(--primary);
  border: 1px solid var(--primary);
}
button:disabled { opacity: 0.6; cursor: not-allowed; }
.error { margin-top: 12px; color: #dc2626; font-size: 14px; }
.muted { color: var(--muted); font-size: 13px; }
</style>
