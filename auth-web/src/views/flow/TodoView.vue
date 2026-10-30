<script setup>
import { computed, onMounted, ref } from 'vue'
import { todoTasks, doneTasks, taskDetail, claimTask, completeTask, getForm } from '../../api/flow'

const tab = ref('todo')
const todo = ref([])
const done = ref([])
const error = ref('')
const notice = ref('')

const detail = ref(null)
const labels = ref({})     // variable key → form label
const comment = ref('')
const acting = ref(false)

const aiBadge = computed(() => {
  const s = detail.value?.variables?.aiSuggestion
  return {
    approve: { text: 'AI 建议：同意', cls: 'approved' },
    reject: { text: 'AI 建议：驳回', cls: 'rejected' },
    escalate: { text: 'AI 建议：转人工判断', cls: 'running' },
    unavailable: { text: 'AI 预审：不可用', cls: '' },
  }[s] || null
})

const formRows = computed(() => {
  if (!detail.value) return []
  return Object.entries(detail.value.variables)
    .filter(([k]) => !['starter', 'approved', 'aiSuggestion', 'aiReason'].includes(k))
    .map(([k, v]) => ({ key: k, label: labels.value[k] || k, value: v }))
})

async function load() {
  error.value = ''
  try {
    const [t, d] = await Promise.all([todoTasks(), doneTasks()])
    todo.value = t
    done.value = d
  } catch (e) {
    error.value = e.message
  }
}

async function open(task) {
  error.value = ''
  comment.value = ''
  notice.value = ''
  try {
    detail.value = await taskDetail(task.id)
    labels.value = {}
  } catch (e) {
    error.value = e.message
  }
}

async function openWithSchema(task) {
  await open(task)
  if (!detail.value) return
  try {
    // processDefinitionId is "<key>:<version>:<id>" — the key is the first segment
    const key = String(detail.value.task.processDefinitionId || '').split(':')[0]
    const form = key ? await getForm(key) : null
    if (form?.schemaJson) {
      const fields = JSON.parse(form.schemaJson).fields || []
      labels.value = Object.fromEntries(fields.map((f) => [f.key, f.label]))
    }
  } catch {
    /* labels are cosmetic — ignore */
  }
}

async function claim() {
  if (!detail.value) return
  try {
    await claimTask(detail.value.task.id)
    notice.value = '已认领'
    await openWithSchema({ id: detail.value.task.id })
  } catch (e) {
    error.value = e.message
  }
}

async function decide(approved) {
  if (!detail.value) return
  acting.value = true
  error.value = ''
  try {
    await completeTask(detail.value.task.id, { approved, comment: comment.value })
    notice.value = approved ? '已通过' : '已驳回'
    detail.value = null
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    acting.value = false
  }
}

const fmtTime = (iso) => (iso ? new Date(iso).toLocaleString() : '—')
const list = computed(() => (tab.value === 'todo' ? todo : done))

onMounted(load)
</script>

<template>
  <div class="card">
    <div class="toolbar">
      <h3 style="margin: 0;">审批中心</h3>
      <button style="width: auto; margin: 0 0 0 auto; padding: 8px 18px;" @click="load">刷新</button>
    </div>
    <div class="tabs">
      <button :class="{ primary: tab === 'todo' }" @click="tab = 'todo'">
        待办 ({{ todo.length }})
      </button>
      <button :class="{ primary: tab === 'done' }" @click="tab = 'done'">
        已办 ({{ done.length }})
      </button>
    </div>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="notice" class="ok">{{ notice }}</p>

    <table>
      <thead>
        <tr><th>任务</th><th>流程实例</th><th>创建时间</th><th>办理人</th><th></th></tr>
      </thead>
      <tbody>
        <tr v-for="t in list" :key="t.id">
          <td>{{ t.name }}</td>
          <td class="muted">{{ String(t.processInstanceId).slice(0, 8) }}…</td>
          <td>{{ fmtTime(t.createTime) }}</td>
          <td>{{ t.assignee || '未认领' }}</td>
          <td>
            <button v-if="tab === 'todo'" class="mini" @click="openWithSchema(t)">办理</button>
            <span v-else class="muted">已完成</span>
          </td>
        </tr>
        <tr v-if="!list.length">
          <td colspan="5" class="muted">
            {{ tab === 'todo' ? '没有待办任务' : '还没有已办记录' }}
          </td>
        </tr>
      </tbody>
    </table>

    <div v-if="detail" class="drawer">
      <h4>
        {{ detail.task.name }}
        <button class="mini" style="float: right;" @click="detail = null">关闭</button>
      </h4>
      <p v-if="aiBadge" class="ai-line">
        <span class="tag" :class="aiBadge.cls">{{ aiBadge.text }}</span>
        <span class="muted">{{ detail.variables.aiReason }}</span>
      </p>

      <table>
        <tbody>
          <tr v-for="r in formRows" :key="r.key">
            <th style="width: 160px;">{{ r.label }}</th>
            <td>{{ r.value }}</td>
          </tr>
        </tbody>
      </table>

      <div v-if="detail.comments?.length" class="comments">
        <h5>审批记录</h5>
        <p v-for="(c, i) in detail.comments" :key="i" class="muted">· {{ c }}</p>
      </div>

      <label class="field">
        <span>审批意见</span>
        <textarea v-model="comment" rows="2" placeholder="可选" />
      </label>
      <div style="display: flex; gap: 10px;">
        <button v-if="!detail.task.assignee" class="mini" style="width: auto;" @click="claim">
          认领
        </button>
        <button :disabled="acting" style="width: auto; margin: 0;" @click="decide(true)">通过</button>
        <button :disabled="acting" class="danger" style="width: auto; margin: 0;" @click="decide(false)">
          驳回
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.tabs { display: flex; gap: 8px; margin-bottom: 14px; }
.tabs button { width: auto; padding: 6px 16px; margin: 0; }
.tag { padding: 2px 8px; border-radius: 10px; font-size: 12px; background: #eef; margin-right: 8px; }
.tag.approved { background: #e6f6ec; color: #1e7a44; }
.tag.rejected { background: #fdeaea; color: #b03a2e; }
.tag.running { background: #fff4e0; color: #9a6a00; }
.ai-line { display: flex; align-items: center; gap: 6px; }
.drawer { margin-top: 20px; border-top: 1px solid var(--border); padding-top: 16px; }
.comments { margin: 12px 0; }
.field { display: block; margin-bottom: 12px; }
.field span { display: block; font-size: 13px; color: var(--muted); margin-bottom: 4px; }
</style>
