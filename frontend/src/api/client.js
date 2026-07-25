/**
 * Shared helpers for calling the Spring API (same origin + Vite proxy → backend).
 *
 * Auth model: the JWT lives in an httpOnly cookie set by the backend — it is NOT readable
 * from JavaScript. Every API call must therefore send credentials, and every state-changing
 * call must echo the CSRF token (double-submit: readable XSRF-TOKEN cookie → X-XSRF-TOKEN header).
 */

const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS', 'TRACE'])

export async function parseApiError(response) {
  const text = await response.text()
  try {
    const data = JSON.parse(text)
    if (data && typeof data.message === 'string') return data.message
    return text || response.statusText
  } catch {
    return text || response.statusText || `HTTP ${response.status}`
  }
}

/** Reads the (non-httpOnly) CSRF cookie the backend sets. */
function readCsrfToken() {
  const match = document.cookie.match(new RegExp(`(?:^|;\\s*)${CSRF_COOKIE}=([^;]*)`))
  return match ? decodeURIComponent(match[1]) : null
}

/**
 * fetch wrapper: always sends the auth cookie, and adds the CSRF header on unsafe methods.
 * Use this instead of raw fetch() for every API call.
 */
export async function apiFetch(url, options = {}) {
  const method = (options.method || 'GET').toUpperCase()
  const headers = { ...(options.headers || {}) }

  if (!SAFE_METHODS.has(method)) {
    const csrf = readCsrfToken()
    if (csrf) headers[CSRF_HEADER] = csrf
  }

  return fetch(url, { ...options, method, headers, credentials: 'include' })
}

/**
 * Builds request headers.
 *
 * The `token` parameter is kept for backwards compatibility with the many call sites that
 * still pass it: auth now travels in the httpOnly cookie, so a null/undefined token simply
 * means "no Authorization header" — which is the normal browser case.
 */
export function authHeaders(token, extra = {}) {
  const headers = { ...extra }
  if (!headers['Content-Type'] && !headers['content-type']) {
    headers['Content-Type'] = 'application/json'
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  return headers
}

/**
 * Primes the CSRF cookie. GET requests are safe, so this is the cheapest way to make the
 * backend issue XSRF-TOKEN before the first state-changing call (e.g. login).
 */
export async function initCsrfToken() {
  try {
    await apiFetch('/api/auth/csrf', { method: 'GET' })
  } catch {
    /* best-effort: the cookie is also issued by any other GET */
  }
}

/** @returns {Promise<object>} UserDTO from GET /api/users/me */
export async function fetchCurrentUser(token) {
  const res = await apiFetch('/api/users/me', {
    headers: authHeaders(token),
  })
  if (!res.ok) {
    throw new Error(await parseApiError(res))
  }
  return res.json()
}
