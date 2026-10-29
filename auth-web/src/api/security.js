import http from './http'

/** Self-service password change (revokes the account's other sessions). */
export const changeMyPassword = (oldPassword, newPassword) =>
  http.post('/api/auth/password', { oldPassword, newPassword }).then((r) => r.data)

/** Online sessions — ADMIN only. */
export const listSessions = () => http.get('/api/v1/sessions').then((r) => r.data.data)

export const revokeSession = (sessionId) =>
  http.delete(`/api/v1/sessions/${sessionId}`).then((r) => r.data.data)

export const revokeUserSessions = (username) =>
  http.delete(`/api/v1/sessions/users/${encodeURIComponent(username)}`).then((r) => r.data.data)
