import { apiFetch, authHeaders, parseApiError } from './client.js'

async function jsonOrThrow(res) {
  if (!res.ok) throw new Error(await parseApiError(res))
  return res.json()
}

/**
 * Récupère la liste de mes documents (paginée).
 */
export async function listMyDocuments(token, { page = 0, size = 50 } = {}) {
  const q = new URLSearchParams({ page, size })
  const res = await apiFetch(`/api/documents?${q}`, { headers: authHeaders(token) })
  return jsonOrThrow(res)
}

/**
 * Upload un document sécurisé au Coffre-fort Numérique.
 * @param {File} file
 * @param {string} contexteType (optionnel) - ex: 'RECLAMATION', 'AFFAIRE'
 * @param {string} contexteId (optionnel)
 */
export async function uploadDocument(token, file, contexteType = '', contexteId = '') {
  const fd = new FormData()
  fd.append('fichier', file)
  if (contexteType) fd.append('contexteType', contexteType)
  if (contexteId) fd.append('contexteId', contexteId)

  const res = await apiFetch('/api/documents/upload', {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: fd,
  })
  return jsonOrThrow(res)
}

/**
 * Télécharge le document source.
 */
export async function downloadDocument(token, id) {
  const res = await apiFetch(`/api/documents/${encodeURIComponent(id)}/download`, {
    headers: authHeaders(token)
  })
  if (!res.ok) throw new Error(await parseApiError(res))
  return res.blob()
}

/**
 * Vérifie l'intégrité (Sceau SHA-256) d'un document.
 * @returns {Promise<{
 *   idDocument: string,
 *   nomFichier: string,
 *   empreinteStored: string,
 *   empreinteRecalculee: string,
 *   integreite: boolean
 * }>}
 */
export async function verifyIntegrity(token, id) {
  const res = await apiFetch(`/api/documents/${encodeURIComponent(id)}/verifier-integrite`, {
    headers: authHeaders(token)
  })
  return jsonOrThrow(res)
}

/**
 * Liste l'historique d'accès d'un document (Upload, Log, Verify).
 */
export async function getDocumentHistory(token, id, { page = 0, size = 20 } = {}) {
  const q = new URLSearchParams({ page, size })
  const res = await apiFetch(`/api/documents/${encodeURIComponent(id)}/historique?${q}`, {
    headers: authHeaders(token)
  })
  return jsonOrThrow(res)
}
