import { apiFetch, authHeaders, parseApiError } from './client.js'

async function jsonOrThrow(res) {
  if (!res.ok) throw new Error(await parseApiError(res))
  return res.json()
}

/**
 * Récupère la liste de mes réclamations (paginée).
 * @returns {Promise<Page<ReclamationDTO>>}
 */
export async function listMyReclamations(token, { page = 0, size = 20, statut } = {}) {
  const q = new URLSearchParams({ page, size })
  if (statut) q.set('statut', statut)
  const res = await apiFetch(`/api/reclamations?${q}`, { headers: authHeaders(token) })
  return jsonOrThrow(res)
}

/**
 * Crée une nouvelle réclamation.
 * @param {object} body - { titre, description, categorie, gravite, utilisateurCibleId? }
 */
export async function createReclamation(token, body) {
  const res = await apiFetch('/api/reclamations', {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(body),
  })
  return jsonOrThrow(res)
}

/**
 * Récupère le détail d'une réclamation.
 */
export async function getReclamation(token, id) {
  const res = await apiFetch(`/api/reclamations/${encodeURIComponent(id)}`, {
    headers: authHeaders(token),
  })
  return jsonOrThrow(res)
}

/**
 * Récupère la liste de messages d'une réclamation.
 * @returns {Promise<ReclamationMessageDTO[]>}
 */
export async function getReclamationMessages(token, id) {
  const res = await apiFetch(`/api/reclamations/${encodeURIComponent(id)}/messages`, {
    headers: authHeaders(token),
  })
  return jsonOrThrow(res)
}

/**
 * Ajoute un message texte à une réclamation.
 * @param {string} contenu
 */
export async function addReclamationMessage(token, id, contenu) {
  const res = await apiFetch(`/api/reclamations/${encodeURIComponent(id)}/messages`, {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify({ contenu }),
  })
  return jsonOrThrow(res)
}

/**
 * Upload une pièce jointe à une réclamation (fichier).
 * Backend: POST /api/reclamations/{id}/pieces-jointes, field name 'fichier'.
 * @param {File} file
 */
export async function uploadReclamationAttachment(token, id, file) {
  const fd = new FormData()
  fd.append('fichier', file)
  const res = await apiFetch(`/api/reclamations/${encodeURIComponent(id)}/pieces-jointes`, {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: fd,
  })
  if (!res.ok) throw new Error(await parseApiError(res))
  return res.text()
}
