import http from './http'

// ---------- users ----------
export const pageUsers = (params) =>
  http.get('/api/v1/users', { params }).then((r) => r.data.data)

export const createUser = (body) =>
  http.post('/api/v1/users', body).then((r) => r.data.data)

export const updateUser = (id, body) =>
  http.put(`/api/v1/users/${id}`, body).then((r) => r.data.data)

export const changeUserStatus = (id, status) =>
  http.patch(`/api/v1/users/${id}/status`, { status })

export const assignUserRoles = (id, roleIds) =>
  http.put(`/api/v1/users/${id}/roles`, { roleIds })

export const resetUserPassword = (id, newPassword) =>
  http.post(`/api/v1/users/${id}/reset-password`, { newPassword })

export const getUserDetail = (id) =>
  http.get(`/api/v1/users/${id}/detail`).then((r) => r.data.data)

export const moveUserOrg = (id, orgId) =>
  http.patch(`/api/v1/users/${id}/org`, { orgId })

// 用户批量导入/导出（xlsx）
export const exportUsers = (params) =>
  http
    .get('/api/v1/users/export', { params, responseType: 'blob' })
    .then((r) => {
      const url = URL.createObjectURL(r.data)
      const a = document.createElement('a')
      a.href = url
      a.download = `users_${new Date().toISOString().slice(0, 10)}.xlsx`
      a.click()
      URL.revokeObjectURL(url)
    })

export const downloadImportTemplate = () =>
  http
    .get('/api/v1/users/import-template', { responseType: 'blob' })
    .then((r) => {
      const url = URL.createObjectURL(r.data)
      const a = document.createElement('a')
      a.href = url
      a.download = 'user_import_template.xlsx'
      a.click()
      URL.revokeObjectURL(url)
    })

export const importUsers = (file) => {
  const form = new FormData()
  form.append('file', file)
  return http
    .post('/api/v1/users/import', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120_000, // 大文件导入的行级校验+写库较慢，放宽超时
    })
    .then((r) => r.data.data)
}

// ---------- orgs ----------
export const orgTree = () =>
  http.get('/api/v1/orgs/tree').then((r) => r.data.data)

export const createOrg = (body) =>
  http.post('/api/v1/orgs', body).then((r) => r.data.data)

export const updateOrg = (id, body) =>
  http.put(`/api/v1/orgs/${id}`, body).then((r) => r.data.data)

export const deleteOrg = (id) => http.delete(`/api/v1/orgs/${id}`)

// ---------- roles ----------
export const listRoles = () =>
  http.get('/api/v1/roles').then((r) => r.data.data)

export const createRole = (body) =>
  http.post('/api/v1/roles', body).then((r) => r.data.data)

export const updateRole = (id, body) =>
  http.put(`/api/v1/roles/${id}`, body).then((r) => r.data.data)

export const roleUsage = () =>
  http.get('/api/v1/roles/usage').then((r) => r.data.data)

export const deleteRole = (id) =>
  http.delete(`/api/v1/roles/${id}`)

// ---------- management operation audit ----------
export const pageOperationLogs = (params) =>
  http.get('/api/v1/operation-logs', { params }).then((r) => r.data.data)

// ---------- directory sync (WeCom / DingTalk) ----------
export const listSyncSources = () =>
  http.get('/api/v1/sync').then((r) => r.data.data)

export const triggerSync = (source) =>
  http.post(`/api/v1/sync/${source}`).then((r) => r.data.data)
