<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

/**
 * 组织树级联选择器。
 *
 * - v-model：选中的组织 ID；showEmpty 时可选空值（null，"未分配"/"全部组织"）
 * - 选中后 trigger 显示完整路径（如 "总部 / 一号工厂 / 二车间"）
 * - 树形缩进渲染 orgTree() 的嵌套结构，停用节点带标记
 */
const props = defineProps({
  modelValue: { default: undefined },
  tree: { type: Array, default: () => [] },
  placeholder: { type: String, default: '请选择组织' },
  showEmpty: { type: Boolean, default: false },
  emptyLabel: { type: String, default: '未分配' },
  disabled: { type: Boolean, default: false },
})
const emit = defineEmits(['update:modelValue'])

const open = ref(false)
const root = ref(null)

const pathById = computed(() => {
  const map = {}
  const walk = (list, parentPath) => list?.forEach((o) => {
    map[o.id] = parentPath ? `${parentPath} / ${o.orgName}` : o.orgName
    walk(o.children, map[o.id])
  })
  walk(props.tree, '')
  return map
})

const label = computed(() => {
  const v = props.modelValue
  if (v === undefined || v === null) {
    return v === null && props.showEmpty ? props.emptyLabel : ''
  }
  return pathById.value[v] || `#${v}`
})

const flat = computed(() => {
  const out = []
  const walk = (list, depth) => list?.forEach((o) => {
    out.push({ id: o.id, name: o.orgName, depth, status: o.status })
    walk(o.children, depth + 1)
  })
  walk(props.tree, 0)
  return out
})

function pick(id) {
  emit('update:modelValue', id)
  open.value = false
}

function pickEmpty() {
  emit('update:modelValue', null)
  open.value = false
}

function onDocClick(e) {
  if (root.value && !root.value.contains(e.target)) open.value = false
}
onMounted(() => document.addEventListener('click', onDocClick))
onBeforeUnmount(() => document.removeEventListener('click', onDocClick))
</script>

<template>
  <div ref="root" class="org-select">
    <button type="button" class="trigger" :disabled="disabled" @click="open = !open">
      <span :class="{ placeholder: label === '' }">{{ label || placeholder }}</span>
      <span class="caret">{{ open ? '▴' : '▾' }}</span>
    </button>
    <div v-if="open" class="panel">
      <div v-if="showEmpty" class="option empty" :class="{ selected: modelValue === null }" @click="pickEmpty">
        {{ emptyLabel }}
      </div>
      <div
        v-for="o in flat"
        :key="o.id"
        class="option"
        :class="{ selected: o.id === modelValue }"
        :style="{ paddingLeft: 12 + o.depth * 18 + 'px' }"
        @click="pick(o.id)"
      >
        {{ o.name }}<span v-if="o.status === 0" class="off">（停用）</span>
      </div>
      <div v-if="!flat.length" class="option muted">暂无组织</div>
    </div>
  </div>
</template>

<style scoped>
.org-select { position: relative; display: inline-block; min-width: 180px; vertical-align: middle; }
.trigger {
  display: flex; align-items: center; justify-content: space-between; gap: 8px;
  width: 100%; min-height: 38px; padding: 6px 12px; margin: 0;
  background: var(--card, #fff); border: 1px solid var(--border); border-radius: 8px;
  font-size: 13px; cursor: pointer; text-align: left;
}
.trigger:disabled { opacity: .6; cursor: not-allowed; }
.placeholder { color: var(--muted); }
.caret { color: var(--muted); font-size: 11px; }
.panel {
  position: absolute; z-index: 30; top: calc(100% + 4px); left: 0;
  min-width: 100%; max-height: 260px; overflow-y: auto;
  background: var(--card, #fff); border: 1px solid var(--border); border-radius: 8px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, .12); padding: 4px;
}
.option { padding: 7px 12px; font-size: 13px; cursor: pointer; border-radius: 6px; white-space: nowrap; }
.option:hover { background: rgba(99, 102, 241, .1); }
.option.selected { color: var(--primary); font-weight: 600; }
.option.empty { color: var(--muted); border-bottom: 1px solid var(--border); border-radius: 0; margin-bottom: 2px; }
.option.off, .off { color: var(--muted); font-size: 12px; }
.muted { color: var(--muted); cursor: default; }
</style>
