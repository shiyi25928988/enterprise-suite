import http from './http'

/** M11 message center API — keep in sync with MessageController. */

export const inbox = (unreadOnly = false, page = 1, size = 20) =>
  http
    .get('/api/v1/messages', { params: { unreadOnly, page, size } })
    .then((r) => r.data.data)

export const unreadCount = () =>
  http.get('/api/v1/messages/unread-count').then((r) => r.data.data.count)

export const markRead = (id) =>
  http.post(`/api/v1/messages/${id}/read`).then((r) => r.data.data)

export const markAllRead = () =>
  http.post('/api/v1/messages/read-all').then((r) => r.data.data)

export const announce = (body) =>
  http.post('/api/v1/messages/announcements', body).then((r) => r.data.data)
