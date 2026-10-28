/**
 * Minimal OIDC Authorization Code + PKCE helper (public client `mes-web`).
 *
 * The SPA is deployed same-origin with the authorization server, so the
 * authorize/token endpoints are reached directly (proxied in dev by Vite).
 * Only crypto-browser primitives are used — no external OIDC dependency.
 */
const CLIENT_ID = 'mes-web'
const SCOPES = 'openid profile'
const REDIRECT_URI = `${window.location.origin}/callback`

function b64url(bytes) {
  return btoa(String.fromCharCode(...bytes))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '')
}

function randomB64url(len = 32) {
  return b64url(crypto.getRandomValues(new Uint8Array(len)))
}

async function pkceChallenge(verifier) {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier))
  return b64url(new Uint8Array(digest))
}

/** Launch the authorization code flow (redirects the browser away). */
export async function startOidcLogin() {
  const verifier = randomB64url()
  const state = randomB64url(16)
  const nonce = randomB64url(16)
  sessionStorage.setItem('oidc.verifier', verifier)
  sessionStorage.setItem('oidc.state', state)
  const params = new URLSearchParams({
    response_type: 'code',
    client_id: CLIENT_ID,
    redirect_uri: REDIRECT_URI,
    scope: SCOPES,
    state,
    nonce,
    code_challenge: await pkceChallenge(verifier),
    code_challenge_method: 'S256',
  })
  window.location.assign(`/oauth2/authorize?${params}`)
}

/** Exchange the authorization code for tokens; returns { access_token, id_token, ... }. */
export async function exchangeCode(code) {
  const expectedState = sessionStorage.getItem('oidc.state')
  const verifier = sessionStorage.getItem('oidc.verifier')
  if (!verifier) throw new Error('Missing PKCE verifier — restart the login flow')

  const body = new URLSearchParams({
    grant_type: 'authorization_code',
    client_id: CLIENT_ID,
    redirect_uri: REDIRECT_URI,
    code,
    code_verifier: verifier,
  })
  const resp = await fetch('/oauth2/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
  })
  if (!resp.ok) {
    const detail = await resp.json().catch(() => ({}))
    throw new Error(`Token exchange failed: ${detail.error || resp.status}`)
  }
  sessionStorage.removeItem('oidc.verifier')
  sessionStorage.removeItem('oidc.state')
  return resp.json()
}

export function parseCallbackQuery() {
  const q = new URLSearchParams(window.location.search)
  return { code: q.get('code'), state: q.get('state'), error: q.get('error') }
}
