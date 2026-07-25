import { apiFetch, authHeaders, parseApiError } from './client.js'

export async function getMe(token) {
  const res = await apiFetch('/api/users/me', { headers: authHeaders(token) })
  if (!res.ok) throw new Error(await parseApiError(res))
  return res.json()
}

export async function updateMe(token, body) {
  const res = await apiFetch('/api/users/me', {
    method: 'PUT',
    headers: authHeaders(token),
    body: JSON.stringify(body),
  })
  if (!res.ok) throw new Error(await parseApiError(res))
  return res.json()
}

export async function deleteMe(token) {
  const res = await apiFetch('/api/users/me', {
    method: 'DELETE',
    headers: authHeaders(token),
  })
  if (!res.ok) throw new Error(await parseApiError(res))
}

export async function getNotificationPreferences(token) {
  const res = await apiFetch('/api/users/me/notification-preferences', {
    headers: authHeaders(token),
  })
  if (!res.ok) throw new Error(await parseApiError(res))
  return res.json()
}

export async function updateNotificationPreferences(token, body) {
  const res = await apiFetch('/api/users/me/notification-preferences', {
    method: 'PUT',
    headers: authHeaders(token),
    body: JSON.stringify(body),
  })
  if (!res.ok) throw new Error(await parseApiError(res))
  return res.json()
}

export async function changePassword(token, body) {
  const res = await apiFetch('/api/users/me/password', {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(body),
  })
  if (!res.ok) throw new Error(await parseApiError(res))
}

export async function uploadProfilePhoto(token, file) {
  const fd = new FormData()
  fd.append('fichier', file)
  const res = await apiFetch('/api/users/me/profile-photo', {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: fd,
  })
  if (!res.ok) throw new Error(await parseApiError(res))
  return res.json()
}

/** Image bytes — utiliser URL.createObjectURL sur le blob, puis revokeObjectURL au démontage. */
export async function downloadProfilePhotoBlob(token) {
  const res = await apiFetch('/api/users/me/profile-photo', {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  if (res.status === 404) return null
  if (!res.ok) throw new Error(await parseApiError(res))
  return res.blob()
}
