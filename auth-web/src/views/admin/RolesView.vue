<script setup>
import { onMounted, reactive, ref } from 'vue'
import Modal from '../../components/Modal.vue'
import { listRoles, createRole, updateRole, roleUsage, deleteRole } from '../../api/admin'

const roles = ref([])
const usage = ref({}) // roleId → RoleUsageDTO
const error = ref('')

async function load() {
  error.value = ''
  try {
    const [list, u] = await Promise.all([listRoles(), roleUsage()])
    roles.value = list
    const map = {}
    u.forEach((x) => (map[x.roleId] = x))
    usage.value = map
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

const deleting = ref(null) // role row pending confirmation

function askDelete(role) {
  deleting.value = role
}

async function confirmDelete() {
  error.value = ''
  try {
    await deleteRole(deleting.value.id)
    deleting.value = null
    load()
  } catch (e) {
    deleting.value = null
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
        <tr><th>ID</th><th>角色编码</th><th>角色名称</th><th>状态</th><th>用户数</th><th>应用数</th><th>操作</th></tr>
      </thead>
      <tbody>
        <tr v-for="r in roles" :key="r.id">
          <td>{{ r.id }}</td>
          <td><code>{{ r.roleCode }}</code></td>
          <td>{{ r.roleName }}</td>
          <td>{{ r.status === 1 ? '启用' : '停用' }}</td>
          <td>{{ usage[r.id]?.userCount ?? '—' }}</td>
          <td>{{ usage[r.id]?.appCount ?? '—' }}</td>
          <td>
            <a style="cursor: pointer; color: var(--primary); margin-right: 10px;" @click.prevent="openEdit(r)">编辑</a>
            <a
              v-if="!usage[r.id]?.builtIn"
              style="cursor: pointer; color: #991b1b;"
              @click.prevent="askDelete(r)"
            >删除</a>
            <span v-else class="muted" style="font-size: 12px;">内置</span>
          </td>
        </tr>
        <tr v-if="!roles.length && !error">
          <td colspan="7" class="muted" style="text-align: center;">暂无角色</td>
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

  <Modal v-if="deleting" :title="`删除角色：${deleting.roleName}`" @close="deleting = null">
    <p>
      确认删除角色 <b>{{ deleting.roleName }}（{{ deleting.roleCode }}）</b>？
      删除后无法恢复；仍有用户或应用引用时会被拒绝。
    </p>
    <p class="muted" v-if="usage[deleting.id]">
      当前引用：{{ usage[deleting.id].userCount }} 个用户、{{ usage[deleting.id].appCount }} 个应用
    </p>
    <div style="display: flex; gap: 10px; justify-content: flex-end;">
      <button type="button" style="width: auto; margin: 0; padding: 8px 18px;" @click="deleting = null">取消</button>
      <button
        type="button"
        style="width: auto; margin: 0; padding: 8px 18px; background: #991b1b;"
        @click="confirmDelete"
      >确认删除</button>
    </div>
  </Modal>
</template>

<style scoped>
.toolbar { display: flex; align-items: center; margin-bottom: 16px; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th, td { text-align: left; padding: 9px 10px; border-bottom: 1px solid var(--border); }
th { color: var(--muted); font-weight: 500; }
</style>
