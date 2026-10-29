import axios from 'axios'

/**
 * Same-origin HTTP client.
 * - withCredentials: session cookie (JSESSIONID) drives the OIDC authorize flow
 * - CSRF: backend publishes the token in the XSRF-TOKEN cookie (GET /api/auth/csrf);
 *   axios echoes it back in the X-XSRF-TOKEN header for mutating requests
 */
const http = axios.create({
  baseURL: '/',
  withCredentials: true,
  timeout: 10_000,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
})

/** Response interceptor: unwrap the ApiResult envelope, normalize errors. */
http.interceptors.response.use(
  (resp) => resp,
  async (error) => {
    const resp = error.response
    // responseType: 'blob' 时错误体也是 Blob，需解析出 JSON 才能拿到业务 message
    let data = resp?.data
    if (data instanceof Blob && data.type.includes('json')) {
      try { data = JSON.parse(await data.text()) } catch { /* keep blob */ }
    }
    const apiError = new Error(data?.message || error.message || 'Network error')
    apiError.code = data?.code
    apiError.status = resp?.status
    apiError.data = data?.data // e.g. {challengeId} on MFA_REQUIRED

    const url = String(error.config?.url || '')
    // Session gone (expired, or kicked from the console): send the browser back
    // to the login screen instead of leaving the page stuck on error toasts.
    if (apiError.status === 401 && !url.includes('/api/auth/login')
        && !window.location.pathname.startsWith('/login')) {
      const back = encodeURIComponent(window.location.pathname + window.location.search)
      window.location.assign(`/login?redirect=${back}`)
    }
    // Still on the initial password: the server refuses everything else.
    if (apiError.code === 40103 && window.location.pathname !== '/password') {
      window.location.assign('/password')
    }
    return Promise.reject(apiError)
  },
)

/** Materialize the CSRF token/cookie (call once per app bootstrap). */
export async function ensureCsrf() {
  return http.get('/api/auth/csrf').then((r) => r.data?.data)
}

export default http
