import http from './http'

// ---------- 应用接入管理（ADMIN）----------
export const listApps = () =>
  http.get('/api/v1/apps').then((r) => r.data.data)

export const createApp = (body) =>
  http.post('/api/v1/apps', body).then((r) => r.data.data) // { app, clientSecret }

export const updateApp = (id, body) =>
  http.put(`/api/v1/apps/${id}`, body).then((r) => r.data.data)

export const setAppRoles = (id, roleIds) =>
  http.put(`/api/v1/apps/${id}/roles`, { roleIds }).then((r) => r.data.data)

export const setAppStatus = (id, status) =>
  http.patch(`/api/v1/apps/${id}/status`, { status }).then((r) => r.data.data)

export const rotateAppSecret = (id) =>
  http.post(`/api/v1/apps/${id}/secret`).then((r) => r.data.data) // { clientSecret }

export const deleteApp = (id) => http.delete(`/api/v1/apps/${id}`)

// ---------- 我的应用（任意登录用户）----------
export const myApps = () =>
  http.get('/api/v1/apps/mine').then((r) => r.data.data)
