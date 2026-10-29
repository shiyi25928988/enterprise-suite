<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import Modal from '../../components/Modal.vue'
import {
  listApps, createApp, updateApp, setAppRoles, setAppStatus,
  rotateAppSecret, deleteApp,
} from '../../api/apps'
import { listRoles } from '../../api/admin'

const apps = ref([])
const roles = ref([])
const error = ref('')
const notice = ref('')

async function load() {
  error.value = ''
  try {
    apps.value = await listApps()
  } catch (e) {
    error.value = e.message
  }
}

onMounted(async () => {
  await load()
  try {
    roles.value = await listRoles()
  } catch { /* 非管理员看不到角色列表，不影响主流程 */ }
})

// ---------------- 新增 / 编辑 ----------------
const showForm = ref(false)
const editing = ref(null)
const emptyForm = () => ({
  appCode: '', appName: '', clientId: '', description: '', icon: '', homeUrl: '',
  clientType: 'PUBLIC', redirectUris: '', postLogoutRedirectUris: '',
  grantTypes: ['authorization_code'], scopes: 'openid,profile',
  accessPolicy: 'ROLES', sortNo: 0, status: 1,
})
const form = reactive(emptyForm())

const grantOptions = [
  { value: 'authorization_code', label: '授权码（浏览器接入，推荐）' },
  { value: 'device_code', label: '设备码（大屏/终端）' },
  { value: 'client_credentials', label: '客户端凭证（服务间调用）' },
]

function openCreate() {
  editing.value = null
  Object.assign(form, emptyForm())
  showForm.value = true
}

function openEdit(app) {
  editing.value = app
  Object.assign(form, {
    appCode: app.appCode,
    appName: app.appName,
    clientId: app.clientId,
    description: app.description || '',
    icon: app.icon || '',
    homeUrl: app.homeUrl || '',
    clientType: app.clientType,
    redirectUris: app.redirectUris || '',
    postLogoutRedirectUris: app.postLogoutRedirectUris || '',
    grantTypes: (app.grantTypes || '').split(',').filter(Boolean),
    scopes: app.scopes || 'openid,profile',
    accessPolicy: app.accessPolicy,
    sortNo: app.sortNo ?? 0,
    status: app.status,
  })
  showForm.value = true
}

async function submitForm() {
  error.value = ''
  try {
    const payload = { ...form }
    if (editing.value) {
      await updateApp(editing.value.id, payload)
      showForm.value = false
      notice.value = `已更新应用「${form.appName}」`
    } else {
      const created = await createApp(payload)
      showForm.value = false
      secretInfo.value = {
        clientId: created.app.clientId,
        clientSecret: created.clientSecret,
        appName: created.app.appName,
        rotated: false,
      }
    }
    load()
  } catch (e) {
    error.value = e.message
  }
}

// ---------------- 密钥 ----------------
const secretInfo = ref(null)

async function rotate(app) {
  if (!confirm(`重新生成「${app.appName}」的 client_secret？旧密钥立即失效，业务系统需同步更新。`)) return
  error.value = ''
  try {
    const res = await rotateAppSecret(app.id)
    secretInfo.value = {
      clientId: app.clientId, clientSecret: res.clientSecret,
      appName: app.appName, rotated: true,
    }
  } catch (e) {
    error.value = e.message
  }
}

async function copySecret() {
  try {
    await navigator.clipboard.writeText(
      `client_id: ${secretInfo.value.clientId}\nclient_secret: ${secretInfo.value.clientSecret}`,
    )
    notice.value = '已复制到剪贴板'
  } catch {
    notice.value = '复制失败，请手动选中复制'
  }
}

// ---------------- 角色授权 ----------------
const showRoles = ref(false)
const roleTarget = ref(null)
const selectedRoleIds = ref([])

function openRoles(app) {
  roleTarget.value = app
  selectedRoleIds.value = [...(app.roleIds || [])]
  showRoles.value = true
}

async function submitRoles() {
  error.value = ''
  try {
    await setAppRoles(roleTarget.value.id, selectedRoleIds.value)
    showRoles.value = false
    notice.value = `已更新「${roleTarget.value.appName}」的角色授权`
    load()
  } catch (e) {
    error.value = e.message
  }
}

function toggleRole(id) {
  const index = selectedRoleIds.value.indexOf(id)
  if (index >= 0) selectedRoleIds.value.splice(index, 1)
  else selectedRoleIds.value.push(id)
}

// ---------------- 启停 / 删除 ----------------
async function toggleStatus(app) {
  error.value = ''
  try {
    await setAppStatus(app.id, app.status === 1 ? 0 : 1)
    load()
  } catch (e) {
    error.value = e.message
  }
}

async function remove(app) {
  if (!confirm(`删除应用「${app.appName}」？对应 OIDC 客户端会一并删除，已发放的授权失效且不可恢复。`)) return
  error.value = ''
  try {
    await deleteApp(app.id)
    notice.value = `已删除应用「${app.appName}」`
    load()
  } catch (e) {
    error.value = e.message
  }
}

const policyLabel = (app) =>
  app.accessPolicy === 'ALL'
    ? '全部用户'
    : (app.roleNames?.length ? app.roleNames.join('、') : '未授权任何角色')
</script>

<template>
  <div class="card">
    <div class="toolbar">
      <div>
        <h3 style="margin: 0;">应用接入</h3>
        <p class="muted" style="margin: 6px 0 0;">
          业务系统以 OIDC 授权码方式接入；在这里登记后即获得 client_id，按角色控制谁能进入。
        </p>
      </div>
      <button style="width: auto; margin: 0 0 0 auto; padding: 8px 18px;" @click="openCreate">+ 接入新应用</button>
    </div>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="notice" class="notice">{{ notice }}</p>

    <table>
      <thead>
        <tr>
          <th>应用</th><th>client_id</th><th>类型</th>
          <th>谁可以访问</th><th>状态</th><th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="a in apps" :key="a.id">
          <td>
            <span class="app-icon">{{ a.icon || '◆' }}</span>
            <b>{{ a.appName }}</b>
            <div class="muted mono">{{ a.appCode }}</div>
          </td>
          <td><code>{{ a.clientId }}</code></td>
          <td>{{ a.clientType === 'CONFIDENTIAL' ? '机密（服务端）' : '公开（SPA/PKCE）' }}</td>
          <td>
            <span :class="a.accessPolicy === 'ALL' ? 'tag-all' : 'tag-roles'">
              {{ a.accessPolicy === 'ALL' ? '全部用户' : '按角色' }}
            </span>
            <div v-if="a.accessPolicy !== 'ALL'" class="muted">{{ policyLabel(a) }}</div>
          </td>
          <td><span :class="a.status === 1 ? 'on' : 'off'">{{ a.status === 1 ? '启用' : '停用' }}</span></td>
          <td class="ops">
            <a @click.prevent="openEdit(a)">编辑</a>
            <a @click.prevent="openRoles(a)">角色授权</a>
            <a v-if="a.clientType === 'CONFIDENTIAL'" @click.prevent="rotate(a)">轮换密钥</a>
            <a @click.prevent="toggleStatus(a)">{{ a.status === 1 ? '停用' : '启用' }}</a>
            <a class="danger" @click.prevent="remove(a)">删除</a>
          </td>
        </tr>
        <tr v-if="!apps.length && !error">
          <td colspan="6" class="muted" style="text-align: center;">还没有接入任何应用</td>
        </tr>
      </tbody>
    </table>
  </div>

  <!-- 新增 / 编辑 -->
  <Modal v-if="showForm" wide
         :title="editing ? `编辑应用：${editing.appName}` : '接入新应用'" @close="showForm = false">
    <form @submit.prevent="submitForm" class="grid">
      <div>
        <label>应用编码 *</label>
        <input v-model="form.appCode" :disabled="!!editing" required placeholder="mes-web" />
      </div>
      <div>
        <label>应用名称 *</label>
        <input v-model="form.appName" required placeholder="MES 生产执行系统" />
      </div>
      <div>
        <label>client_id（留空同应用编码）</label>
        <input v-model="form.clientId" :disabled="!!editing" placeholder="mes-web" />
      </div>
      <div>
        <label>图标（1-2 个字符/emoji）</label>
        <input v-model="form.icon" placeholder="🏭" />
      </div>
      <div class="full">
        <label>入口地址（门户点击跳转）</label>
        <input v-model="form.homeUrl" placeholder="http://localhost:8081/" />
      </div>
      <div class="full">
        <label>描述</label>
        <input v-model="form.description" placeholder="一句话说明这个系统的用途" />
      </div>
      <div>
        <label>客户端类型</label>
        <select v-model="form.clientType">
          <option value="PUBLIC">公开（前端 SPA，PKCE，无密钥）</option>
          <option value="CONFIDENTIAL">机密（服务端应用，有 client_secret）</option>
        </select>
      </div>
      <div>
        <label>访问策略</label>
        <select v-model="form.accessPolicy">
          <option value="ROLES">仅授权角色可访问</option>
          <option value="ALL">全部登录用户可访问</option>
        </select>
      </div>
      <div class="full">
        <label>回调地址（多个用逗号或换行分隔）</label>
        <textarea v-model="form.redirectUris" rows="2"
                  placeholder="http://localhost:8081/callback"></textarea>
      </div>
      <div class="full">
        <label>登出回调地址（可选）</label>
        <input v-model="form.postLogoutRedirectUris" placeholder="http://localhost:8081/" />
      </div>
      <div class="full">
        <label>授权类型</label>
        <label v-for="g in grantOptions" :key="g.value" class="check">
          <input type="checkbox" :value="g.value" v-model="form.grantTypes" />
          <span>{{ g.label }}</span>
        </label>
      </div>
      <div>
        <label>scopes</label>
        <input v-model="form.scopes" placeholder="openid,profile" />
      </div>
      <div>
        <label>排序（越小越靠前）</label>
        <input v-model.number="form.sortNo" type="number" />
      </div>
      <p v-if="error" class="error full">{{ error }}</p>
      <div class="full actions">
        <button type="submit" style="width: auto; margin: 0; padding: 9px 22px;">保存</button>
      </div>
    </form>
  </Modal>

  <!-- 角色授权 -->
  <Modal v-if="showRoles" :title="`角色授权：${roleTarget?.appName}`" @close="showRoles = false">
    <p class="muted" style="margin-top: 0;">
      勾选可以访问该应用的角色。用户只要具备其中任一角色即可通过 OIDC 授权进入。
    </p>
    <div class="role-list">
      <label v-for="r in roles" :key="r.id" class="check">
        <input type="checkbox" :checked="selectedRoleIds.includes(r.id)" @change="toggleRole(r.id)" />
        <span>{{ r.roleName }} <code class="muted">{{ r.roleCode }}</code></span>
      </label>
      <p v-if="!roles.length" class="muted">暂无角色，请先在角色管理中创建。</p>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <p class="muted" v-if="!selectedRoleIds.length">提示：不勾选任何角色等于没人能访问（管理员同样受限）。</p>
    <button @click="submitRoles">保存授权</button>
  </Modal>

  <!-- 密钥一次性展示 -->
  <Modal v-if="secretInfo" title="客户端凭据（仅显示一次）" @close="secretInfo = null">
    <p class="muted" style="margin-top: 0;">
      {{ secretInfo.rotated ? '旧密钥已失效，' : '' }}请立即把下面两项配置到「{{ secretInfo.appName }}」，
      关闭后无法再次查看密钥（可轮换重发）。
    </p>
    <label>client_id</label>
    <input :value="secretInfo.clientId" readonly />
    <label>client_secret</label>
    <input :value="secretInfo.clientSecret || '（公开客户端无密钥，使用 PKCE）'" readonly />
    <button @click="copySecret">复制</button>
  </Modal>
</template>

<style scoped>
.toolbar { display: flex; align-items: flex-start; margin-bottom: 16px; gap: 12px; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th, td { text-align: left; padding: 10px; border-bottom: 1px solid var(--border); vertical-align: top; }
th { color: var(--muted); font-weight: 500; }
.app-icon {
  display: inline-flex; align-items: center; justify-content: center;
  width: 24px; height: 24px; border-radius: 6px; background: #eff6ff;
  margin-right: 6px; font-size: 13px;
}
.mono { font-family: ui-monospace, Consolas, monospace; font-size: 12px; }
.ops a { margin-right: 10px; color: var(--primary); cursor: pointer; white-space: nowrap; }
.ops a.danger { color: #dc2626; }
.tag-all, .tag-roles {
  display: inline-block; padding: 1px 8px; border-radius: 10px; font-size: 12px;
}
.tag-all { background: #ecfdf5; color: #047857; }
.tag-roles { background: #eff6ff; color: #1d4ed8; }
.on { color: #047857; }
.off { color: #b91c1c; }
.notice {
  margin: 0 0 12px; padding: 8px 12px; border-radius: 6px;
  background: #ecfdf5; color: #047857; font-size: 13px;
}
.grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px 16px; }
.grid .full { grid-column: 1 / -1; }
.grid .actions { display: flex; justify-content: flex-end; }
select, textarea {
  width: 100%; padding: 8px 10px; border: 1px solid var(--border);
  border-radius: 6px; font-size: 14px; font-family: inherit;
}
label.check {
  display: flex; align-items: center; gap: 8px; margin: 6px 0;
  font-size: 14px; color: var(--text); cursor: pointer;
}
label.check input { width: auto; margin: 0; }
.role-list { max-height: 240px; overflow-y: auto; }
</style>
