import { apiFetch, authHeaders, parseApiError } from './client.js'

async function jsonOrThrow(res) {
  if (!res.ok) throw new Error(await parseApiError(res))
  return res.json()
}

// ─── Admin User Management ─────────────────────────────────────────────────────
export async function listUsers(token, { page = 0, size = 20, search } = {}) {
  const q = new URLSearchParams({ page, size })
  if (search) q.set('search', search)
  const res = await apiFetch(`/api/admin/users?${q}`, { headers: authHeaders(token) })
  return jsonOrThrow(res)
}

export async function deactivateUser(token, id) {
  const res = await apiFetch(`/api/admin/users/${encodeURIComponent(id)}`, {
    method: 'DELETE',
    headers: authHeaders(token),
  })
  if (!res.ok) throw new Error(await parseApiError(res))
}

export async function reactivateUser(token, id) {
  const res = await apiFetch(`/api/admin/users/${encodeURIComponent(id)}/reactivate`, {
    method: 'PATCH',
    headers: authHeaders(token),
  })
  if (!res.ok) throw new Error(await parseApiError(res))
}

// ─── Admin Avocat Management ───────────────────────────────────────────────────
export async function listAvocatsAdmin(token, { page = 0, size = 20, verifie, actif } = {}) {
  const q = new URLSearchParams({ page, size })
  if (verifie !== undefined) q.set('verifie', verifie)
  if (actif !== undefined) q.set('actif', actif)
  const res = await apiFetch(`/api/admin/avocats?${q}`, { headers: authHeaders(token) })
  return jsonOrThrow(res)
}

export async function updateAvocatVerification(token, avocatId, body) {
  const res = await apiFetch(`/api/admin/avocats/${encodeURIComponent(avocatId)}/verification`, {
    method: 'PATCH',
    headers: authHeaders(token),
    body: JSON.stringify(body),
  })
  return jsonOrThrow(res)
}

export async function deactivateAvocat(token, avocatId) {
  const res = await apiFetch(`/api/admin/avocats/${encodeURIComponent(avocatId)}`, {
    method: 'DELETE',
    headers: authHeaders(token),
  })
  if (!res.ok) throw new Error(await parseApiError(res))
}

// ─── Admin Reclamation Management ─────────────────────────────────────────────
export async function listAllReclamations(token, { page = 0, size = 20, statut } = {}) {
  const q = new URLSearchParams({ page, size })
  if (statut) q.set('statut', statut)
  const res = await apiFetch(`/api/admin/reclamations?${q}`, { headers: authHeaders(token) })
  return jsonOrThrow(res)
}

export async function updateReclamationStatus(token, id, statut) {
  const res = await apiFetch(`/api/admin/reclamations/${encodeURIComponent(id)}/statut`, {
    method: 'PATCH',
    headers: authHeaders(token),
    body: JSON.stringify({ statut }),
  })
  return jsonOrThrow(res)
}

// ─── Admin Affaire Management ────────────────────────────────────────────────
export async function listAllAffaires(token, { page = 0, size = 20, statut } = {}) {
  const q = new URLSearchParams({ page, size })
  if (statut) q.set('statut', statut)
  const res = await apiFetch(`/api/admin/affaires?${q}`, { headers: authHeaders(token) })
  return jsonOrThrow(res)
}

export async function getAffaireTimeline(token, id) {
  const res = await apiFetch(`/api/admin/affaires/${encodeURIComponent(id)}/timeline`, { headers: authHeaders(token) })
  return jsonOrThrow(res)
}

// ─── Admin RendezVous Management ─────────────────────────────────────────────
export async function listAllRendezVous(token, { page = 0, size = 20 } = {}) {
  const q = new URLSearchParams({ page, size })
  const res = await apiFetch(`/api/admin/rendezvous?${q}`, { headers: authHeaders(token) })
  return jsonOrThrow(res)
}

export async function triggerRdvReminders(token) {
  const res = await apiFetch(`/api/admin/rendezvous/trigger-reminders`, {
    method: 'POST',
    headers: authHeaders(token),
  })
  return jsonOrThrow(res)
}

// ─── Admin Document Management ───────────────────────────────────────────────
export async function listAllSystemDocuments(token, { page = 0, size = 20 } = {}) {
  const q = new URLSearchParams({ page, size })
  const res = await apiFetch(`/api/admin/documents?${q}`, { headers: authHeaders(token) })
  return jsonOrThrow(res)
}

export async function deleteSystemDocument(token, id) {
  const res = await apiFetch(`/api/admin/documents/${encodeURIComponent(id)}`, {
    method: 'DELETE',
    headers: authHeaders(token),
  })
  if (!res.ok) throw new Error(await parseApiError(res))
}

// ─── Admin Messenger Management ──────────────────────────────────────────────
export async function listAllConversations(token, { page = 0, size = 20 } = {}) {
  const q = new URLSearchParams({ page, size })
  const res = await apiFetch(`/api/admin/messenger/conversations?${q}`, { headers: authHeaders(token) })
  return jsonOrThrow(res)
}

export async function closeConversationGlobal(token, id) {
  const res = await apiFetch(`/api/admin/messenger/conversations/${encodeURIComponent(id)}/close`, {
    method: 'POST',
    headers: authHeaders(token),
  })
  if (!res.ok) throw new Error(await parseApiError(res))
}

// ─── Admin Audit Log Management ──────────────────────────────────────────────
export async function listAuditLogs(token, { page = 0, size = 50 } = {}) {
  const q = new URLSearchParams({ page, size })
  const res = await apiFetch(`/api/admin/audit-logs?${q}`, { headers: authHeaders(token) })
  return jsonOrThrow(res)
}

// ─── WhatsApp Management ─────────────────────────────────────────────────────
export async function getWhatsAppStatus(token) {
  const res = await apiFetch(`/api/admin/whatsapp/status`, { headers: authHeaders(token) })
  return jsonOrThrow(res)
}

export async function getWhatsAppQr(token) {
  const res = await apiFetch(`/api/admin/whatsapp/qr`, { headers: authHeaders(token) })
  return jsonOrThrow(res)
}

export async function sendWhatsAppTest(token, telephone, message) {
  const res = await apiFetch(`/api/admin/whatsapp/test`, {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify({ telephone, message }),
  })
  return jsonOrThrow(res)
}
