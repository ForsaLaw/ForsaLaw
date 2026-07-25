import { apiFetch, authHeaders, parseApiError } from './client.js'

export async function getDomaines() {
  const res = await apiFetch('/api/avocats/domaines')
  if (!res.ok) throw new Error(await parseApiError(res))
  return res.json()
}

export async function listPublicAvocats({ page = 0, size = 60, specialite, ville, verifie } = {}) {
  const q = new URLSearchParams()
  q.set('page', String(page))
  q.set('size', String(size))
  if (specialite) q.set('specialite', specialite)
  if (ville) q.set('ville', ville)
  if (typeof verifie === 'boolean') q.set('verifie', String(verifie))
  const res = await apiFetch(`/api/avocats?${q.toString()}`)
  if (!res.ok) throw new Error(await parseApiError(res))
  return res.json()
}

export async function getPublicAvocatById(id) {
  const res = await apiFetch(`/api/avocats/${encodeURIComponent(id)}`)
  if (!res.ok) throw new Error(await parseApiError(res))
  return res.json()
}

/**
 * @returns {Promise<object|null>} AvocatDTO, or null if the user has no lawyer profile yet.
 */
export async function getMyAvocatProfileOrNull(token) {
  const res = await apiFetch('/api/avocats/me', { headers: authHeaders(token) })
  if (res.status === 400) {
    const msg = await parseApiError(res)
    if (/profil avocat non trouv/i.test(msg)) return null
    throw new Error(msg)
  }
  if (!res.ok) throw new Error(await parseApiError(res))
  return res.json()
}

export async function createMyAvocatProfile(token, body) {
  const res = await apiFetch('/api/avocats/me', {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(body),
  })
  if (!res.ok) throw new Error(await parseApiError(res))
  return res.json()
}

export async function updateMyAvocatProfile(token, body) {
  const res = await apiFetch('/api/avocats/me', {
    method: 'PUT',
    headers: authHeaders(token),
    body: JSON.stringify(body),
  })
  if (!res.ok) throw new Error(await parseApiError(res))
  return res.json()
}

export async function deactivateMyAvocatProfile(token) {
  const res = await apiFetch('/api/avocats/me', {
    method: 'DELETE',
    headers: authHeaders(token),
  })
  if (!res.ok) throw new Error(await parseApiError(res))
}

export async function changeAvocatPassword(token, body) {
  const res = await apiFetch('/api/avocats/me/password', {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(body),
  })
  if (!res.ok) throw new Error(await parseApiError(res))
}

export async function uploadAvocatProfilePhoto(token, file) {
  const fd = new FormData()
  fd.append('fichier', file)
  const res = await apiFetch('/api/avocats/me/profile-photo', {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: fd,
  })
  if (!res.ok) throw new Error(await parseApiError(res))
  return res.json()
}

export async function downloadAvocatProfilePhotoBlob(token) {
  const res = await apiFetch('/api/avocats/me/profile-photo', {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  if (res.status === 404) return null
  if (!res.ok) throw new Error(await parseApiError(res))
  return res.blob()
}

/** JWT `role` claim (avocat | client | admin) — may lag DB after admin approval until re-login. */
export function parseJwtRole(token) {
  if (!token || typeof token !== 'string') return null
  try {
    const part = token.split('.')[1]
    if (!part) return null
    const base64 = part.replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4)
    const json = atob(padded)
    const payload = JSON.parse(json)
    return typeof payload.role === 'string' ? payload.role : null
  } catch {
    return null
  }
}
