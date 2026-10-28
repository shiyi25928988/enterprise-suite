import { defineStore } from 'pinia'
import http, { ensureCsrf } from '../api/http'

/**
 * Session-based auth state. Tokens from the OIDC flow are kept in memory
 * only (never localStorage) per the platform security baseline.
 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: null,       // { username, realName, authorities: [] }
    tokens: null,     // { access_token, id_token, refresh_token } — memory only
    loaded: false,
  }),
  actions: {
    async bootstrap() {
      await ensureCsrf().catch(() => {})
      try {
        await this.fetchMe()
      } finally {
        this.loaded = true
      }
    },
    async fetchMe() {
      const body = await http.get('/api/auth/me').then((r) => r.data)
      this.user = body?.data ?? null
      return this.user
    },
    async login(username, password, redirect) {
      const body = await http
        .post('/api/auth/login', { username, password, redirect })
        .then((r) => r.data)
      await this.fetchMe().catch(() => {})
      return body?.data?.redirect || '/'
    },
    async logout() {
      await http.post('/api/auth/logout').catch(() => {})
      this.user = null
      this.tokens = null
    },
    setTokens(tokens) {
      this.tokens = tokens
    },
  },
})
