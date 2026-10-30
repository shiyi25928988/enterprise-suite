<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { listStartable, getForm, startInstance, myInstances, instanceDetail } from '../../api/flow'

const startable = ref([])
const instances = ref([])
const error = ref('')
const notice = ref('')

const selectedKey = ref('')
const schema = ref(null)
const formValues = reactive({})
const submitting = ref(false)
const businessKey = ref('')
const detail = ref(null)

const current = computed(() => startable.value.find((s) => s.processKey === selectedKey.value))
const fields = computed(() => {
  if (!schema.value?.schemaJson) return []
  try {
    return JSON.parse(schema.value.schemaJson).fields || []
  } catch {
    return []
  }
})

async function load() {
  error.value = ''
  try {
    const [s, i] = await Promise.all([listStartable(), myInstances()])
    startable.value = s
    instances.value = i
  } catch (e) {
    error.value = e.message
  }
}

async function pick(key) {
  selectedKey.value = key
  schema.value = null
  Object.keys(formValues).forEach((k) => delete formValues[k])
  businessKey.value = ''
  notice.value = ''
  try {
    schema.value = await getForm(key)
  } catch (e) {
    error.value = e.message
  }
}

async function submit() {
  error.value = ''
  notice.value = ''
  submitting.value = true
  try {
    const vars = {}
    fields.value.forEach((f) => {
      const v = formValues[f.key]
      if (v !== undefined && v !== '') vars[f.key] = v
    })
    const result = await startInstance({
      processKey: selectedKey.value,
      businessKey: businessKey.value || null,
      variables: vars,
    })
    notice.value = `已提交，流程实例 ${result.processInstanceId.slice(0, 8)}…`
    Object.keys(formValues).forEach((k) => delete formValues[k])
    businessKey.value = ''
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    submitting.value = false
  }
}

async function openInstance(row) {
  error.value = ''
  try {
    detail.value = await instanceDetail(row.id)
  } catch (e) {
    error.value = e.message
  }
}

const fmtTime = (iso) => (iso ? new Date(iso).toLocaleString() : '—')
const stateLabel = (s) =>
  ({ running: '审批中', approved: '已通过', rejected: '已驳回', finished: '已结束' })[s] || s

onMounted(load)
</script>

<template>
  <div class="card">
    <div class="toolbar">
      <h3 style="margin: 0;">我的申请</h3>
      <button style="width: auto; margin: 0 0 0 auto; padding: 8px 18px;" @click="load">刷新</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="notice" class="ok">{{ notice }}</p>

    <div class="split">
      <div>
        <h4>发起申请</h4>
        <div style="display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 12px;">
          <button
            v-for="s in startable"
            :key="s.processKey"
            :class="{ primary: selectedKey === s.processKey }"
            style="width: auto; padding: 6px 14px;"
            @click="pick(s.processKey)"
          >
            {{ s.name || s.processKey }}
          </button>
          <span v-if="!startable.length" class="muted">暂无可用流程</span>
        </div>

        <div v-if="selectedKey">
          <label class="field">
            <span>业务单号（可选）</span>
            <input v-model="businessKey" placeholder="如 HR-2026-001" />
          </label>
          <label v-for="f in fields" :key="f.key" class="field">
            <span>{{ f.label }}<em v-if="f.required" class="req">*</em></span>
            <select v-if="f.type === 'enum'" v-model="formValues[f.key]">
              <option value="">请选择</option>
              <option v-for="o in f.options" :key="o" :value="o">{{ o }}</option>
            </select>
            <textarea
              v-else-if="f.type === 'text'"
              v-model="formValues[f.key]"
              rows="3"
            />
            <input
              v-else
              v-model="formValues[f.key]"
              :type="f.type === 'number' ? 'number' : f.type === 'date' ? 'date' : 'text'"
            />
          </label>
          <button :disabled="submitting" @click="submit">
            {{ submitting ? '提交中…' : '提交' }}
          </button>
        </div>
      </div>

      <div>
        <h4>申请记录</h4>
        <table>
          <thead>
            <tr><th>流程</th><th>单号</th><th>发起时间</th><th>状态</th><th></th></tr>
          </thead>
          <tbody>
            <tr v-for="i in instances" :key="i.id">
              <td>{{ i.processName || i.processKey }}</td>
              <td>{{ i.businessKey || '—' }}</td>
              <td>{{ fmtTime(i.startedAt) }}</td>
              <td>
                <span class="tag" :class="i.endState">{{ stateLabel(i.endState) }}</span>
              </td>
              <td><button class="mini" @click="openInstance(i)">详情</button></td>
            </tr>
            <tr v-if="!instances.length">
              <td colspan="5" class="muted">还没有申请记录</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-if="detail" class="drawer">
      <h4>
        申请详情
        <button class="mini" style="float: right;" @click="detail = null">关闭</button>
      </h4>
      <p class="muted">
        {{ detail.instance.processName }} · {{ detail.instance.businessKey || '无单号' }} ·
        {{ fmtTime(detail.instance.startedAt) }}
      </p>
      <table>
        <tbody>
          <tr v-for="(v, k) in detail.variables" :key="k">
            <th style="width: 160px;">{{ k }}</th>
            <td>{{ v }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.split { display: grid; grid-template-columns: 1fr 1.2fr; gap: 24px; }
.field { display: block; margin-bottom: 12px; }
.field span { display: block; font-size: 13px; color: var(--muted); margin-bottom: 4px; }
.field .req { color: #c0392b; font-style: normal; margin-left: 2px; }
.tag { padding: 2px 8px; border-radius: 10px; font-size: 12px; background: #eef; }
.tag.approved { background: #e6f6ec; color: #1e7a44; }
.tag.rejected { background: #fdeaea; color: #b03a2e; }
.tag.running { background: #fff4e0; color: #9a6a00; }
.drawer { margin-top: 20px; border-top: 1px solid var(--border); padding-top: 16px; }
</style>
