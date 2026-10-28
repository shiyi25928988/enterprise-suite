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
  (error) => {
    const resp = error.response
    const apiError = new Error(resp?.data?.message || error.message || 'Network error')
    apiError.code = resp?.data?.code
    apiError.status = resp?.status
    apiError.data = resp?.data?.data // e.g. {challengeId} on MFA_REQUIRED
    return Promise.reject(apiError)
  },
)

/** Materialize the CSRF token/cookie (call once per app bootstrap). */
export async function ensureCsrf() {
  return http.get('/api/auth/csrf').then((r) => r.data?.data)
}

export default http
