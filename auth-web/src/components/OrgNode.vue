<script setup>
import { ref } from 'vue'

defineProps({ node: { type: Object, required: true } })
const emit = defineEmits(['add', 'edit', 'remove'])
const expanded = ref(true)
</script>

<template>
  <li>
    <div class="node">
      <span class="toggle" @click="expanded = !expanded">
        {{ node.children?.length ? (expanded ? '▾' : '▸') : '·' }}
      </span>
      <span class="name">{{ node.orgName }}</span>
      <span class="muted">{{ node.orgCode }}</span>
      <span class="ops">
        <a @click.prevent="emit('add', node)">+子级</a>
        <a @click.prevent="emit('edit', node)">编辑</a>
        <a @click.prevent="emit('remove', node)">删除</a>
      </span>
    </div>
    <ul v-if="expanded && node.children?.length">
      <OrgNode v-for="c in node.children" :key="c.id" :node="c"
               @add="(n) => emit('add', n)" @edit="(n) => emit('edit', n)"
               @remove="(n) => emit('remove', n)" />
    </ul>
  </li>
</template>

<script>
// recursive self-reference
export default { name: 'OrgNode' }
</script>

<style scoped>
li { list-style: none; }
ul { padding-left: 22px; margin: 0; }
.node {
  display: flex; align-items: center; gap: 8px;
  padding: 5px 8px; border-radius: 5px; font-size: 13px;
}
.node:hover { background: #f1f5f9; }
.toggle { cursor: pointer; width: 14px; color: var(--muted); }
.name { font-weight: 500; }
.ops { margin-left: auto; opacity: 0; transition: opacity 0.12s; }
.node:hover .ops { opacity: 1; }
.ops a { margin-left: 10px; color: var(--primary); cursor: pointer; font-size: 12px; }
</style>
