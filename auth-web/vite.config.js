import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// The SPA runs on :8081 so that the demo client `mes-web` redirect URI
// (http://localhost:8081/callback) matches without extra configuration.
// Backend endpoints are proxied to :9000, making dev traffic same-origin —
// exactly the same topology as the production same-origin deployment.
// NOTE: /login is intentionally NOT proxied: it is an SPA route (our own
// login page must take over when the authorize flow redirects to /login).
const BACKEND = 'http://localhost:9000'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 8081,
    proxy: {
      '/api': BACKEND,
      '/oauth2': BACKEND,
      '/userinfo': BACKEND,
      '/connect': BACKEND,
      '/.well-known': BACKEND,
    },
  },
})
