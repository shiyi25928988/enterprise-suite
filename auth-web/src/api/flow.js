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
