<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import Modal from '../../components/Modal.vue'
import OrgTreeSelect from '../../components/OrgTreeSelect.vue'
import {
  pageUsers, createUser, updateUser, changeUserStatus,
  assignUserRoles, resetUserPassword, listRoles, orgTree,
  exportUsers, downloadImportTemplate, importUsers,
  getUserDetail, moveUserOrg,
} from '../../api/admin'

// ---------- list state ----------
const loading = ref(false)
const error = ref('')
const rows = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 20, keyword: '', orgId: null, status: null })

const STATUS = { 1: '启用', 0: '禁用', 2: '锁定' }

const roles = ref([])
const orgOptions = ref([])
const orgNameById = computed(() => {
  const map = {}
  const walk = (list) => list?.forEach((o) => { map[o.id] = o.orgName; walk(o.children) })
  walk(orgOptions.value)
  return map
})
const orgName = (id) => orgNameById.value[id] || `#${id ?? '-'}`

async function load() {
  loading.value = true
  error.value = ''
  try {
    const params = { page: query.page, size: query.size }
    if (query.keyword) params.keyword = query.keyword
    if (query.orgId) params.orgId = query.orgId
    if (query.status !== null && query.status !== '') params.status = query.status
    const data = await pageUsers(params)
    rows.value = data.records
    total.value = data.total
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function search() { query.page = 1; load() }

// ---------- create / edit ----------
const showForm = ref(false)
const editing = ref(null) // null = create
const form = reactive({
  username: '', password: '', realName: '', employeeNo: '',
  mobile: '', email: '', orgId: null, roleIds: [],
})

function openCreate() {
  editing.value = null
  Object.assign(form, {
    username: '', password: '', realName: '', employeeNo: '',
    mobile: '', email: '', orgId: null, roleIds: [],
  })
  showForm.value = true
}

function openEdit(row) {
  editing.value = row
  Object.assign(form, {
    username: row.username, password: '', realName: row.realName,
    employeeNo: '', mobile: '', email: '', orgId: row.orgId, roleIds: [...(row.roleIds || [])],
  })
  showForm.value = true
}

async function submitForm() {
  error.value = ''
  try {
    if (editing.value) {
      const { username, password, roleIds, ...editable } = form
      await updateUser(editing.value.id, editable)
      await assignUserRoles(editing.value.id, form.roleIds)
    } else {
      await createUser({ ...form })
    }
    showForm.value = false
    load()
  } catch (e) {
    error.value = e.message
  }
}

// ---------- roles dialog ----------
const showRoles = ref(false)
const rolesTarget = ref(null)
const pickedRoleIds = ref([])

function openRoles(row) {
  rolesTarget.value = row
  pickedRoleIds.value = [...(row.roleIds || [])]
  showRoles.value = true
}

async function submitRoles() {
  try {
    await assignUserRoles(rolesTarget.value.id, pickedRoleIds.value)
    showRoles.value = false
    load()
  } catch (e) {
    error.value = e.message
  }
}

// ---------- reset password ----------
const showPwd = ref(false)
const pwdTarget = ref(null)
const newPassword = ref('')

function openPwd(row) {
  pwdTarget.value = row
  newPassword.value = ''
  showPwd.value = true
}

async function submitPwd() {
  try {
    await resetUserPassword(pwdTarget.value.id, newPassword.value)
    showPwd.value = false
  } catch (e) {
    error.value = e.message
  }
}

async function toggleStatus(row) {
  try {
    await changeUserStatus(row.id, row.status === 1 ? 0 : 1)
    load()
  } catch (e) {
    error.value = e.message
  }
}

// ---------- batch export / import ----------
const exporting = ref(false)

async function doExport() {
  exporting.value = true
  error.value = ''
  try {
    const params = {}
    if (query.keyword) params.keyword = query.keyword
    if (query.orgId) params.orgId = query.orgId
    if (query.status !== null && query.status !== '') params.status = query.status
    await exportUsers(params)
  } catch (e) {
    error.value = `导出失败：${e.message}`
  } finally {
    exporting.value = false
  }
}

const showImport = ref(false)
const importFile = ref(null)
const importing = ref(false)
const importResult = ref(null)

function onImportFile(e) {
  importFile.value = e.target.files[0] || null
  importResult.value = null
}

async function submitImport() {
  if (!importFile.value) return
  importing.value = true
  error.value = ''
  try {
    importResult.value = await importUsers(importFile.value)
    load()
  } catch (e) {
    // blob/JSON 错误统一处理：后端校验失败返回 JSON，axios blob 下需手动解析
    error.value = e.message
  } finally {
    importing.value = false
  }
}

// ---------- user detail (full profile + org move) ----------
const showDetail = ref(false)
const detail = ref(null)
const detailLoading = ref(false)
const moveOrgId = ref(undefined) // undefined = untouched, null = 未分配

async function openDetail(row) {
  showDetail.value = true
  detail.value = null
  detailLoading.value = true
  moveOrgId.value = undefined
  error.value = ''
  try {
    detail.value = await getUserDetail(row.id)
  } catch (e) {
    error.value = e.message
    showDetail.value = false
  } finally {
    detailLoading.value = false
  }
}

const moveDirty = computed(() => moveOrgId.value !== undefined)

async function submitMove() {
  if (!detail.value) return
  error.value = ''
  try {
    await moveUserOrg(detail.value.id, moveOrgId.value)
    showDetail.value = false
    load()
  } catch (e) {
    error.value = e.message
  }
}

const SOURCE = { 1: '本地', 2: 'LDAP', 3: '企业微信', 4: '钉钉' }

onMounted(async () => {
  load()
  listRoles().then((r) => (roles.value = r)).catch(() => {})
  orgTree().then((r) => (orgOptions.value = r)).catch(() => {})
})
</script>

<template>
  <div class="card">
    <div class="toolbar">
      <input v-model="query.keyword" placeholder="用户名 / 姓名" style="width: 180px;" @keyup.enter="search" />
      <OrgTreeSelect v-model="query.orgId" :tree="orgOptions" placeholder="全部组织" show-empty empty-label="全部组织" />
      <select v-model="query.status" style="width: 120px;">
        <option :value="null">全部状态</option>
        <option value="1">启用</option>
        <option value="0">禁用</option>
        <option value="2">锁定</option>
      </select>
      <button style="width: auto; margin: 0; padding: 8px 18px;" @click="search">查询</button>
      <button style="width: auto; margin: 0 0 0 auto; padding: 8px 18px;" :disabled="exporting" @click="doExport">{{ exporting ? '导出中…' : '导出 Excel' }}</button>
      <button style="width: auto; margin: 0; padding: 8px 18px;" @click="showImport = true">批量导入</button>
      <button style="width: auto; margin: 0; padding: 8px 18px;" @click="openCreate">+ 新增用户</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>

    <table>
      <thead>
        <tr>
          <th>ID</th><th>用户名</th><th>姓名</th><th>组织</th>
          <th>状态</th><th>最后登录</th><th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row.id">
          <td>{{ row.id }}</td>
          <td>{{ row.username }}</td>
          <td>{{ row.realName }}</td>
          <td>{{ orgName(row.orgId) }}</td>
          <td>
            <span :class="['badge', `s${row.status}`]">{{ STATUS[row.status] || row.status }}</span>
          </td>
          <td class="muted">{{ row.lastLoginAt || '—' }}</td>
          <td class="actions">
            <a @click.prevent="openDetail(row)">详情</a>
            <a @click.prevent="openEdit(row)">编辑</a>
            <a @click.prevent="openRoles(row)">角色</a>
            <a @click.prevent="openPwd(row)">重置密码</a>
            <a @click.prevent="toggleStatus(row)">{{ row.status === 1 ? '禁用' : '启用' }}</a>
          </td>
        </tr>
        <tr v-if="!rows.length && !loading">
          <td colspan="7" class="muted" style="text-align: center;">暂无数据</td>
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

  <Modal v-if="showForm" :title="editing ? `编辑用户：${editing.username}` : '新增用户'" @close="showForm = false">
    <form @submit.prevent="submitForm">
      <label>用户名</label>
      <input v-model="form.username" :disabled="!!editing" required />
      <label v-if="!editing">初始密码（≥8 位，含至少 3 类字符）</label>
      <input v-if="!editing" v-model="form.password" type="password" required />
      <label>姓名</label>
      <input v-model="form.realName" required />
      <label>工号</label>
      <input v-model="form.employeeNo" />
      <label>手机号</label>
      <input v-model="form.mobile" />
      <label>邮箱</label>
      <input v-model="form.email" type="email" />
      <label>所属组织</label>
      <OrgTreeSelect v-model="form.orgId" :tree="orgOptions" placeholder="未分配" show-empty empty-label="未分配" />
      <label>角色</label>
      <div class="checkboxes">
        <label v-for="r in roles" :key="r.id" class="inline">
          <input v-model="form.roleIds" type="checkbox" :value="r.id" style="width: auto;" />
          {{ r.roleName }}（{{ r.roleCode }}）
        </label>
      </div>
      <button type="submit">保存</button>
    </form>
  </Modal>

  <Modal v-if="showRoles" :title="`分配角色：${rolesTarget?.username}`" @close="showRoles = false">
    <div class="checkboxes">
      <label v-for="r in roles" :key="r.id" class="inline">
        <input v-model="pickedRoleIds" type="checkbox" :value="r.id" style="width: auto;" />
        {{ r.roleName }}（{{ r.roleCode }}）
      </label>
    </div>
    <button @click="submitRoles">保存</button>
  </Modal>

  <Modal v-if="showPwd" :title="`重置密码：${pwdTarget?.username}`" @close="showPwd = false">
    <label>新密码（≥8 位，含至少 3 类字符）</label>
    <input v-model="newPassword" type="password" />
    <button @click="submitPwd">确认重置</button>
  </Modal>

  <Modal v-if="showDetail" :title="`用户详情：${detail?.username || '…'}`" @close="showDetail = false">
    <p v-if="detailLoading" class="muted">加载中…</p>
    <template v-else-if="detail">
      <table class="detail">
        <tbody>
          <tr><th>用户名</th><td>{{ detail.username }}</td></tr>
          <tr><th>姓名</th><td>{{ detail.realName || '—' }}</td></tr>
          <tr><th>工号</th><td>{{ detail.employeeNo || '—' }}</td></tr>
          <tr><th>手机号</th><td>{{ detail.mobile || '—' }}</td></tr>
          <tr><th>邮箱</th><td>{{ detail.email || '—' }}</td></tr>
          <tr><th>来源</th><td>{{ SOURCE[detail.source] || detail.source }}</td></tr>
          <tr><th>状态</th><td><span :class="['badge', `s${detail.status}`]">{{ STATUS[detail.status] || detail.status }}</span></td></tr>
          <tr><th>强制改密</th><td>{{ detail.mustChangePassword === 1 ? '是（尚未替换初始密码）' : '否' }}</td></tr>
          <tr><th>MFA</th><td>{{ detail.mfaEnabled === 1 ? '已启用 TOTP' : '未启用' }}</td></tr>
          <tr><th>组织</th><td>{{ detail.orgName || '未分配' }}<span v-if="detail.orgPath" class="muted">（{{ detail.orgPath }}）</span></td></tr>
          <tr><th>角色</th><td>
            <span v-if="!detail.roles?.length" class="muted">无</span>
            <span v-for="r in detail.roles" :key="r.id" class="role-chip">{{ r.name || r.code || ('#' + r.id) }}</span>
          </td></tr>
          <tr><th>最后登录</th><td class="muted">{{ detail.lastLoginAt || '从未登录' }}</td></tr>
          <tr><th>密码更新时间</th><td class="muted">{{ detail.passwordUpdatedAt || '—' }}</td></tr>
          <tr><th>创建时间</th><td class="muted">{{ detail.createTime || '—' }}</td></tr>
        </tbody>
      </table>

      <label style="margin-top: 14px;">移动组织</label>
      <div style="display: flex; gap: 10px;">
        <OrgTreeSelect
          v-model="moveOrgId"
          :tree="orgOptions"
          :placeholder="`选择目标组织（当前：${detail.orgName || '未分配'}）`"
          show-empty
          empty-label="未分配"
          style="flex: 1;"
        />
        <button type="button" style="width: auto; margin: 0; padding: 8px 18px;" :disabled="!moveDirty" @click="submitMove">移动</button>
      </div>
    </template>
  </Modal>

  <Modal v-if="showImport" title="批量导入用户" @close="showImport = false">
    <p class="muted" style="margin-top: 0;">
      1. 下载模板 → 2. 按说明填写 → 3. 上传 xlsx 文件。
      导入按行校验，<b>成功的行会创建，失败的行会在下方列出原因</b>，单次上限 5000 行。
    </p>
    <p><a @click.prevent="downloadImportTemplate()">下载导入模板</a></p>
    <label>选择文件（.xlsx，≤5MB）</label>
    <input type="file" accept=".xlsx" @change="onImportFile" />
    <button :disabled="!importFile || importing" @click="submitImport">
      {{ importing ? '导入中…' : '开始导入' }}
    </button>

    <div v-if="importResult" style="margin-top: 14px;">
      <p>
        共 <b>{{ importResult.total }}</b> 行：
        <span style="color: #166534;">成功 {{ importResult.succeeded }}</span>，
        <span style="color: #991b1b;">失败 {{ importResult.failed }}</span>
      </p>
      <table v-if="importResult.errors?.length">
        <thead><tr><th>行号</th><th>用户名</th><th>失败原因</th></tr></thead>
        <tbody>
          <tr v-for="e in importResult.errors" :key="e.row">
            <td>{{ e.row }}</td><td>{{ e.username || '—' }}</td><td>{{ e.reason }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </Modal>
</template>

<style scoped>
.toolbar { display: flex; gap: 10px; margin-bottom: 16px; align-items: center; }
.toolbar input, .toolbar select { width: auto; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th, td { text-align: left; padding: 9px 10px; border-bottom: 1px solid var(--border); }
th { color: var(--muted); font-weight: 500; }
.badge { padding: 2px 8px; border-radius: 10px; font-size: 12px; }
.s1 { background: #dcfce7; color: #166534; }
.s0 { background: #fee2e2; color: #991b1b; }
.s2 { background: #fef3c7; color: #92400e; }
.actions a { margin-right: 10px; cursor: pointer; color: var(--primary); }
.pager { display: flex; gap: 12px; align-items: center; margin-top: 14px; }
.checkboxes { display: flex; flex-direction: column; gap: 4px; }
label.inline { display: flex; align-items: center; gap: 8px; }
table.detail { width: 100%; border-collapse: collapse; font-size: 13px; }
table.detail th { width: 110px; text-align: left; padding: 6px 8px; color: var(--muted); font-weight: 500; vertical-align: top; border-bottom: 1px solid var(--border); }
table.detail td { padding: 6px 8px; border-bottom: 1px solid var(--border); }
.role-chip { display: inline-block; padding: 2px 10px; margin: 0 6px 4px 0; background: var(--bg, #f1f5f9); border: 1px solid var(--border); border-radius: 10px; font-size: 12px; }
</style>
