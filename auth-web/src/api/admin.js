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

// ---------- directory sync (WeCom / DingTalk) ----------
export const listSyncSources = () =>
  http.get('/api/v1/sync').then((r) => r.data.data)

export const triggerSync = (source) =>
  http.post(`/api/v1/sync/${source}`).then((r) => r.data.data)
