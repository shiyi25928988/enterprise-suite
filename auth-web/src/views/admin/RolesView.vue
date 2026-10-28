<script setup>
import { onMounted, reactive, ref } from 'vue'
import Modal from '../../components/Modal.vue'
import { listRoles, createRole, updateRole } from '../../api/admin'

const roles = ref([])
const error = ref('')

async function load() {
  error.value = ''
  try {
    roles.value = await listRoles()
  } catch (e) {
    error.value = e.message
  }
}

const showForm = ref(false)
const editing = ref(null)
const form = reactive({ roleCode: '', roleName: '' })

function openCreate() {
  editing.value = null
  Object.assign(form, { roleCode: '', roleName: '' })
  showForm.value = true
}

function openEdit(role) {
  editing.value = role
  Object.assign(form, { roleCode: role.roleCode, roleName: role.roleName })
  showForm.value = true
}

async function submitForm() {
  error.value = ''
  try {
    if (editing.value) {
      await updateRole(editing.value.id, { roleName: form.roleName })
    } else {
      await createRole({ ...form })
    }
    showForm.value = false
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
      <h3 style="margin: 0;">角色管理</h3>
      <button style="width: auto; margin: 0 0 0 auto; padding: 8px 18px;" @click="openCreate">+ 新增角色</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>

    <table>
      <thead>
        <tr><th>ID</th><th>角色编码</th><th>角色名称</th><th>状态</th><th>操作</th></tr>
      </thead>
      <tbody>
        <tr v-for="r in roles" :key="r.id">
          <td>{{ r.id }}</td>
          <td><code>{{ r.roleCode }}</code></td>
          <td>{{ r.roleName }}</td>
          <td>{{ r.status === 1 ? '启用' : '停用' }}</td>
          <td><a style="cursor: pointer; color: var(--primary);" @click.prevent="openEdit(r)">编辑</a></td>
        </tr>
        <tr v-if="!roles.length && !error">
          <td colspan="5" class="muted" style="text-align: center;">暂无角色</td>
        </tr>
      </tbody>
    </table>
    <p class="muted">权限点（菜单/按钮/API）绑定将在管理后台完善阶段提供。</p>
  </div>

  <Modal v-if="showForm" :title="editing ? `编辑角色：${editing.roleName}` : '新增角色'" @close="showForm = false">
    <form @submit.prevent="submitForm">
      <label>角色编码（大写字母/数字/下划线，如 MES_ADMIN）</label>
      <input v-model="form.roleCode" :disabled="!!editing" required />
      <label>角色名称</label>
      <input v-model="form.roleName" required />
      <button type="submit">保存</button>
    </form>
  </Modal>
</template>

<style scoped>
.toolbar { display: flex; align-items: center; margin-bottom: 16px; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th, td { text-align: left; padding: 9px 10px; border-bottom: 1px solid var(--border); }
th { color: var(--muted); font-weight: 500; }
</style>
