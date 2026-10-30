import http from './http'

/** W1 workflow API — keep in sync with FlowController. */

export const listStartable = () => http.get('/api/v1/flow/startable').then((r) => r.data.data)

export const listDefinitions = () => http.get('/api/v1/flow/definitions').then((r) => r.data.data)

export const deployProcess = (name, bpmnXml) =>
  http.post('/api/v1/flow/deploy', { name, bpmnXml }).then((r) => r.data.data)

export const listForms = () => http.get('/api/v1/flow/forms').then((r) => r.data.data)

export const getForm = (processKey) =>
  http.get(`/api/v1/flow/forms/${processKey}`).then((r) => r.data.data)

export const saveForm = (processKey, body) =>
  http.put(`/api/v1/flow/forms/${processKey}`, body).then((r) => r.data.data)

export const startInstance = (body) =>
  http.post('/api/v1/flow/instances', body).then((r) => r.data.data)

export const myInstances = () =>
  http.get('/api/v1/flow/instances/mine').then((r) => r.data.data)

export const instanceDetail = (id) =>
  http.get(`/api/v1/flow/instances/${id}`).then((r) => r.data.data)

export const todoTasks = () => http.get('/api/v1/flow/todo').then((r) => r.data.data)

export const doneTasks = () => http.get('/api/v1/flow/done').then((r) => r.data.data)

export const taskDetail = (id) => http.get(`/api/v1/flow/tasks/${id}`).then((r) => r.data.data)

export const claimTask = (id) =>
  http.post(`/api/v1/flow/tasks/${id}/claim`).then((r) => r.data.data)

export const completeTask = (id, body) =>
  http.post(`/api/v1/flow/tasks/${id}/complete`, body).then((r) => r.data.data)

// ---- W2: delegation / transfer ----

export const delegateTask = (id, user) =>
  http.post(`/api/v1/flow/tasks/${id}/delegate`, { user }).then((r) => r.data.data)

export const transferTask = (id, user) =>
  http.post(`/api/v1/flow/tasks/${id}/transfer`, { user }).then((r) => r.data.data)

export const unclaimTask = (id) =>
  http.post(`/api/v1/flow/tasks/${id}/unclaim`).then((r) => r.data.data)

// ---- W2: analytics ----

export const analyticsOverview = () =>
  http.get('/api/v1/flow/analytics/overview').then((r) => r.data.data)

export const analyticsDefinitions = () =>
  http.get('/api/v1/flow/analytics/definitions').then((r) => r.data.data)

export const analyticsNodes = (processKey) =>
  http.get(`/api/v1/flow/analytics/nodes/${processKey}`).then((r) => r.data.data)

export const instanceTimeline = (id) =>
  http.get(`/api/v1/flow/instances/${id}/timeline`).then((r) => r.data.data)
