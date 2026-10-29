<script setup>
import { onMounted, reactive, ref } from 'vue'
import { pageOperationLogs } from '../../api/admin'

const loading = ref(false)
const error = ref('')
const rows = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 20, operator: '', action: '', start: '', end: '' })

async function load() {
  loading.value = true
  error.value = ''
  try {
    const params = { page: query.page, size: query.size }
    if (query.operator) params.operator = query.operator
    if (query.action) params.action = query.action
    if (query.start) params.start = `${query.start}T00:00:00`
    if (query.end) params.end = `${query.end}T23:59:59`
    const data = await pageOperationLogs(params)
    rows.value = data.records
    total.value = data.total
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function search() { query.page = 1; load() }

function reset() {
  Object.assign(query, { page: 1, operator: '', action: '', start: '', end: '' })
  load()
}

const isSuccess = (code) => code >= 200 && code < 400

onMounted(load)
</script>

<template>
  <div class="card">
    <div class="toolbar">
      <h3 style="margin: 0; margin-right: 12px;">操作日志</h3>
      <input v-model="query.operator" placeholder="操作人" style="width: 150px;" @keyup.enter="search" />
      <input v-model="query.action" placeholder="动作（如 USER_STATUS）" style="width: 200px;" @keyup.enter="search" />
      <input v-model="query.start" type="date" style="width: 150px;" />
      <span class="muted">至</span>
      <input v-model="query.end" type="date" style="width: 150px;" />
      <button style="width: auto; margin: 0; padding: 8px 18px;" @click="search">查询</button>
      <button style="width: auto; margin: 0; padding: 8px 18px;" @click="reset">重置</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>

    <table>
      <thead>
        <tr><th>时间</th><th>操作人</th><th>动作</th><th>请求</th><th>结果</th><th>来源 IP</th></tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row.id">
          <td class="muted" style="white-space: nowrap;">{{ row.createTime }}</td>
          <td>{{ row.operator }}</td>
          <td><code>{{ row.action }}</code></td>
          <td class="muted" style="max-width: 320px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;" :title="row.method + ' ' + row.uri">
            {{ row.method }} {{ row.uri }}
          </td>
          <td>
            <span :class="['badge', isSuccess(row.statusCode) ? 'ok' : 'fail']">{{ row.statusCode }}</span>
          </td>
          <td class="muted">{{ row.ip || '—' }}</td>
        </tr>
        <tr v-if="!rows.length && !loading">
          <td colspan="6" class="muted" style="text-align: center;">暂无数据</td>
        </tr>
      </tbody>
    </table>

    <div class="pager">
      <span class="muted">共 {{ total }} 条</span>
      <button style="width:auto;margin:0;padding:6px 14px;" :disabled="query.page <= 1" @click="query.page--; load()">上一页</button>
      <span>第 {{ query.page }} 页</span>
      <button style="width:auto;margin:0;padding:6px 14px;" :disabled="query.page * query.size >= total" @click="query.page++; load()">下一页</button>
    </div>
  </div>
</template>

<style scoped>
.toolbar { display: flex; gap: 10px; margin-bottom: 16px; align-items: center; }
.toolbar input { width: auto; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th, td { text-align: left; padding: 9px 10px; border-bottom: 1px solid var(--border); }
th { color: var(--muted); font-weight: 500; }
.badge { padding: 2px 8px; border-radius: 10px; font-size: 12px; }
.ok { background: #dcfce7; color: #166534; }
.fail { background: #fee2e2; color: #991b1b; }
.pager { display: flex; gap: 12px; align-items: center; margin-top: 14px; }
</style>
