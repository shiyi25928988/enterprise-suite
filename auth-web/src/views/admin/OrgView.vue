<script setup>
import { onMounted, reactive, ref } from 'vue'
import OrgNode from '../../components/OrgNode.vue'
import Modal from '../../components/Modal.vue'
import { orgTree, createOrg, updateOrg, deleteOrg } from '../../api/admin'

const tree = ref([])
const error = ref('')
const TYPES = { 1: '公司', 2: '厂区', 3: '车间', 4: '工段/产线', 5: '部门' }

async function load() {
  error.value = ''
  try {
    tree.value = await orgTree()
  } catch (e) {
    error.value = e.message
  }
}

// ---------- create / edit ----------
const showForm = ref(false)
const editing = ref(null)
const form = reactive({
  orgCode: '', orgName: '', orgType: 9, parentId: null, sortNo: 0,
})

function openCreate(parent) {
  editing.value = null
  Object.assign(form, {
    orgCode: '', orgName: '', orgType: parent ? 4 : 1,
    parentId: parent?.id ?? null, sortNo: 0,
  })
  showForm.value = true
}

function openEdit(node) {
  editing.value = node
  Object.assign(form, {
    orgCode: node.orgCode, orgName: node.orgName, orgType: node.orgType,
    parentId: node.parentId, sortNo: node.sortNo ?? 0,
  })
  showForm.value = true
}

async function submitForm() {
  error.value = ''
  try {
    if (editing.value) {
      await updateOrg(editing.value.id, {
        orgName: form.orgName, sortNo: form.sortNo, status: editing.value.status,
      })
    } else {
      await createOrg({
        orgCode: form.orgCode, orgName: form.orgName, orgType: form.orgType,
        parentId: form.parentId, sortNo: form.sortNo,
      })
    }
    showForm.value = false
    load()
  } catch (e) {
    error.value = e.message
  }
}

async function remove(node) {
  if (!confirm(`确认删除「${node.orgName}」？有下级组织或成员时将被拒绝。`)) return
  error.value = ''
  try {
    await deleteOrg(node.id)
    load()
  } catch (e) {
    error.value = e.message
  }
}

onMounted(load)
</script>

<template>
  <div class="card">
    <div class="toolbar">
      <h3 style="margin: 0;">组织架构</h3>
      <button style="width: auto; margin: 0 0 0 auto; padding: 8px 18px;" @click="openCreate(null)">
        + 新增根组织
      </button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <p class="muted">类型：1 公司 / 2 厂区 / 3 车间 / 4 工段-产线 / 5 部门（制造层级建议按此编码，层级上限 10）</p>

    <ul class="tree">
      <OrgNode v-for="n in tree" :key="n.id" :node="n"
               @add="openCreate" @edit="openEdit" @remove="remove" />
    </ul>
    <p v-if="!tree.length && !error" class="muted">暂无组织，先创建根组织。</p>
  </div>

  <Modal v-if="showForm" :title="editing ? `编辑组织：${editing.orgName}` : '新增组织'" @close="showForm = false">
    <form @submit.prevent="submitForm">
      <label>编码（唯一）</label>
      <input v-model="form.orgCode" :disabled="!!editing" required />
      <label>名称</label>
      <input v-model="form.orgName" required />
      <label v-if="!editing">类型</label>
      <select v-if="!editing" v-model.number="form.orgType">
        <option v-for="(label, val) in TYPES" :key="val" :value="Number(val)">{{ label }}</option>
      </select>
      <label v-if="!editing">上级组织</label>
      <input v-if="!editing" :value="form.parentId ? `#${form.parentId}` : '（根）'" disabled />
      <label>排序号</label>
      <input v-model.number="form.sortNo" type="number" />
      <button type="submit">保存</button>
    </form>
  </Modal>
</template>

<style scoped>
.toolbar { display: flex; align-items: center; margin-bottom: 8px; }
.tree { padding: 0; }
</style>
