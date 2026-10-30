<script setup>
import { onMounted, ref } from 'vue'
import { analyticsOverview, analyticsDefinitions, analyticsNodes } from '../../api/flow'

const overview = ref(null)
const definitions = ref([])
const selectedKey = ref('')
const nodes = ref([])
const error = ref('')

async function load() {
  error.value = ''
  try {
    const [o, d] = await Promise.all([analyticsOverview(), analyticsDefinitions()])
    overview.value = o
    definitions.value = d
  } catch (e) {
    error.value = e.message
  }
}

async function pick(key) {
  selectedKey.value = key
  nodes.value = []
  try {
    nodes.value = await analyticsNodes(key)
  } catch (e) {
    error.value = e.message
  }
}

const fmtCycle = (ms) => {
  if (!ms) return '—'
  if (ms < 60000) return `${Math.round(ms / 1000)} 秒`
  if (ms < 3600000) return `${Math.round(ms / 60000)} 分钟`
  if (ms < 86400000) return `${(ms / 3600000).toFixed(1)} 小时`
  return `${(ms / 86400000).toFixed(1)} 天`
}

const maxNodeAvg = () => Math.max(1, ...nodes.value.map((n) => n.avgMs))
const barWidth = (ms) => `${Math.max(4, Math.round((ms / maxNodeAvg()) * 100))}%`

onMounted(load)
</script>

<template>
  <div class="card">
    <div class="toolbar">
      <h3 style="margin: 0;">流程分析</h3>
      <button style="width: auto; margin: 0 0 0 auto; padding: 8px 18px;" @click="load">刷新</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>

    <template v-if="overview">
      <div class="cards">
        <div class="stat">
          <p class="num">{{ overview.total }}</p>
          <p class="muted">总实例</p>
        </div>
        <div class="stat">
          <p class="num">{{ overview.running }}</p>
          <p class="muted">进行中</p>
        </div>
        <div class="stat">
          <p class="num ok">{{ overview.approved }}</p>
          <p class="muted">已通过</p>
        </div>
        <div class="stat">
          <p class="num bad">{{ overview.rejected }}</p>
          <p class="muted">已驳回</p>
        </div>
        <div class="stat">
          <p class="num">{{ fmtCycle(overview.avgCycleMs) }}</p>
          <p class="muted">平均周期</p>
        </div>
        <div class="stat">
          <p class="num">{{ overview.tasksDone }}</p>
          <p class="muted">累计办结任务</p>
        </div>
        <div class="stat">
          <p class="num" :class="{ bad: overview.overdueActive > 0 }">{{ overview.overdueActive }}</p>
          <p class="muted">超期待办</p>
        </div>
      </div>

      <h4>按流程定义</h4>
      <table>
        <thead>
          <tr><th>流程</th><th>实例数</th><th>已结束</th><th>通过 / 驳回</th><th>平均周期</th><th></th></tr>
        </thead>
        <tbody>
          <tr v-for="d in definitions" :key="d.processKey" :class="{ dim: selectedKey && selectedKey !== d.processKey }">
            <td>{{ d.name || d.processKey }}</td>
            <td>{{ d.instances }}</td>
            <td>{{ d.finished }}</td>
            <td>
              <span class="tag approved">{{ d.approved }}</span>
              <span class="tag rejected">{{ d.rejected }}</span>
            </td>
            <td>{{ fmtCycle(d.avgCycleMs) }}</td>
            <td>
              <button class="mini" :class="{ primary: selectedKey === d.processKey }" style="width: auto;"
                      @click="selectedKey === d.processKey ? ((selectedKey = ''), (nodes = [])) : pick(d.processKey)">
                {{ selectedKey === d.processKey ? '收起节点' : '节点耗时' }}
              </button>
            </td>
          </tr>
          <tr v-if="!definitions.length">
            <td colspan="6" class="muted">还没有流程实例数据</td>
          </tr>
        </tbody>
      </table>

      <template v-if="selectedKey">
        <h4 style="margin-top: 20px;">节点耗时 — {{ selectedKey }}</h4>
        <table>
          <thead>
            <tr><th>节点</th><th>类型</th><th>次数</th><th>平均</th><th>最快 / 最慢</th><th style="width: 30%;">分布</th></tr>
          </thead>
          <tbody>
            <tr v-for="n in nodes" :key="n.activityId">
              <td>{{ n.name }}</td>
              <td class="muted">{{ n.type }}</td>
              <td>{{ n.count }}</td>
              <td>{{ fmtCycle(n.avgMs) }}</td>
              <td class="muted">{{ fmtCycle(n.minMs) }} / {{ fmtCycle(n.maxMs) }}</td>
              <td>
                <div class="bar" :style="{ width: barWidth(n.avgMs) }"></div>
              </td>
            </tr>
            <tr v-if="!nodes.length">
              <td colspan="6" class="muted">该流程还没有已完成的节点记录</td>
            </tr>
          </tbody>
        </table>
      </template>
    </template>
  </div>
</template>

<style scoped>
.cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(110px, 1fr)); gap: 12px; margin-bottom: 20px; }
.stat { border: 1px solid var(--border); border-radius: 8px; padding: 12px; text-align: center; }
.stat .num { font-size: 22px; font-weight: 600; margin: 0 0 4px; }
.stat .num.ok { color: #1e7a44; }
.stat .num.bad { color: #b03a2e; }
.stat p { margin: 0; }
.tag { padding: 2px 8px; border-radius: 10px; font-size: 12px; background: #eef; margin-right: 4px; }
.tag.approved { background: #e6f6ec; color: #1e7a44; }
.tag.rejected { background: #fdeaea; color: #b03a2e; }
.bar { height: 10px; border-radius: 5px; background: #4f7cf0; min-width: 4px; }
tr.dim { opacity: 0.45; }
</style>
