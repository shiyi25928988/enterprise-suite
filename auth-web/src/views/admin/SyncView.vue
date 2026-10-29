<script setup>
import { computed, onMounted, ref } from 'vue'
import { listSyncSources, triggerSync } from '../../api/admin'

const sources = ref([])
const error = ref('')
const running = ref(null)   // source currently being synced
const lastMessage = ref('')

async function load() {
  error.value = ''
  try {
    sources.value = await listSyncSources()
  } catch (e) {
    error.value = e.message
  }
}

async function sync(source) {
  error.value = ''
  lastMessage.value = ''
  running.value = source
  try {
    const result = await triggerSync(source)
    lastMessage.value =
      `${source} 同步完成：组织 +${result.orgsCreated}/更新 ${result.orgsUpdated}，` +
      `用户 +${result.usersCreated}/更新 ${result.usersUpdated}` +
      (result.warnings?.length ? `，${result.warnings.length} 条警告` : '')
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    running.value = null
  }
}

const fmtTime = (iso) => (iso ? new Date(iso).toLocaleString() : '—')

const hasAnySource = computed(() => sources.value.length > 0)

onMounted(load)
</script>

<template>
  <div class="card">
    <div class="toolbar">
      <h3 style="margin: 0;">目录同步</h3>
      <button style="width: auto; margin: 0 0 0 auto; padding: 8px 18px;" @click="load">刷新</button>
    </div>
    <p class="muted">
      从企业微信 / 钉钉导入组织与用户（按 <code>auth.sync.*.enabled</code> 配置启用）。
      同步按外部身份幂等：重复执行不会产生重复数据；与本地账号同名的用户会被跳过。
    </p>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="lastMessage" class="ok">{{ lastMessage }}</p>

    <table>
      <thead>
        <tr>
          <th>来源</th><th>已启用</th><th>上次同步</th>
          <th>组织 (+建/更新)</th><th>用户 (+建/更新)</th><th>警告</th><th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="s in sources" :key="s.source">
          <td><code>{{ s.source }}</code></td>
          <td>{{ s.enabled ? '是' : '否' }}</td>
          <td>{{ fmtTime(s.lastRun?.finishedAt) }}</td>
          <td v-if="s.lastRun">+{{ s.lastRun.orgsCreated }} / {{ s.lastRun.orgsUpdated }}</td>
          <td v-else class="muted">—</td>
          <td v-if="s.lastRun">+{{ s.lastRun.usersCreated }} / {{ s.lastRun.usersUpdated }}</td>
          <td v-else class="muted">—</td>
          <td v-if="s.lastRun">
            <span v-if="!s.lastRun.warnings?.length" class="ok">无</span>
            <span v-else class="warn" :title="s.lastRun.warnings.join('\n')">
              {{ s.lastRun.warnings.length }} 条
            </span>
          </td>
          <td v-else class="muted">—</td>
          <td>
            <button class="mini" :disabled="running === s.source" @click="sync(s.source)">
              {{ running === s.source ? '同步中…' : '立即同步' }}
            </button>
          </td>
        </tr>
        <tr v-if="!hasAnySource && !error">
          <td colspan="7" class="muted" style="text-align: center;">
            尚未启用任何目录来源（在服务端配置 auth.sync.dingtalk.enabled / auth.sync.wecom.enabled）
          </td>
        </tr>
      </tbody>
    </table>

    <div v-if="sources.some((s) => s.lastRun?.warnings?.length)" class="warnings">
      <h4>最近警告明细</h4>
      <ul>
        <template v-for="s in sources" :key="s.source">
          <li v-for="(w, i) in s.lastRun?.warnings || []" :key="s.source + i">
            <code>{{ s.source }}</code>：{{ w }}
          </li>
        </template>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.toolbar { display: flex; align-items: center; margin-bottom: 8px; }
table { width: 100%; border-collapse: collapse; font-size: 13px; margin-top: 8px; }
th, td { text-align: left; padding: 9px 10px; border-bottom: 1px solid var(--border); }
th { color: var(--muted); font-weight: 500; }
button.mini { width: auto; margin: 0; padding: 6px 14px; font-size: 13px; }
.ok { color: #16a34a; }
.warn { color: #d97706; cursor: help; }
.warnings { margin-top: 16px; }
.warnings h4 { margin: 8px 0; }
.warnings li { font-size: 13px; margin: 4px 0; }
</style>
