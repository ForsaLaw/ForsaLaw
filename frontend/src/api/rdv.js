import { apiFetch, authHeaders, parseApiError } from './client.js'

async function jsonOrThrow(res) {
  if (!res.ok) throw new Error(await parseApiError(res))
  return res.json()
}

export async function listClientAppointments(token, { page = 0, size = 50 } = {}) {
  const res = await apiFetch(`/api/rendezvous/mes-demandes?page=${page}&size=${size}`, {
    headers: authHeaders(token),
  })
  return jsonOrThrow(res)
}

export async function createClientAppointmentRequest(token, avocatId, body) {
  const res = await apiFetch(`/api/rendezvous/avocats/${encodeURIComponent(avocatId)}/demandes`, {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(body),
  })
  return jsonOrThrow(res)
}

export async function listAvailableSlots(token, avocatId, debutIso, finIso) {
  const q = new URLSearchParams({ debut: debutIso, fin: finIso })
  const res = await apiFetch(`/api/rendezvous/avocats/${encodeURIComponent(avocatId)}/creneaux-disponibles?${q.toString()}`, {
    headers: authHeaders(token),
  })
  return jsonOrThrow(res)
}

export async function listPublicAvailableSlots(avocatId, debutIso, finIso) {
  const q = new URLSearchParams({ debut: debutIso, fin: finIso })
  const res = await apiFetch(`/api/rendezvous/public/avocats/${encodeURIComponent(avocatId)}/creneaux-disponibles?${q.toString()}`)
  return jsonOrThrow(res)
}

export async function getPublicAgenda(avocatId) {
  const res = await apiFetch(`/api/rendezvous/public/avocats/${encodeURIComponent(avocatId)}/agenda`)
  return jsonOrThrow(res)
}

export async function clientAcceptProposal(token, rdvId) {
  const res = await apiFetch(`/api/rendezvous/${encodeURIComponent(rdvId)}/accepter-proposition`, {
    method: 'PATCH',
    headers: authHeaders(token),
  })
  return jsonOrThrow(res)
}

export async function clientRefuseProposal(token, rdvId, raisonRefus) {
  const res = await apiFetch(`/api/rendezvous/${encodeURIComponent(rdvId)}/refuser-proposition`, {
    method: 'PATCH',
    headers: authHeaders(token),
    body: JSON.stringify({ raisonRefus }),
  })
  return jsonOrThrow(res)
}

export async function clientCancelAppointment(token, rdvId, raisonAnnulation) {
  const res = await apiFetch(`/api/rendezvous/${encodeURIComponent(rdvId)}/annuler`, {
    method: 'PATCH',
    headers: authHeaders(token),
    body: JSON.stringify({ raisonAnnulation }),
  })
  return jsonOrThrow(res)
}

export async function clientMeetingAccess(token, rdvId) {
  const res = await apiFetch(`/api/rendezvous/${encodeURIComponent(rdvId)}/meeting-access`, {
    headers: authHeaders(token),
  })
  return jsonOrThrow(res)
}

export async function listLawyerAppointments(token, { page = 0, size = 50 } = {}) {
  const res = await apiFetch(`/api/rendezvous/avocat/demandes-recues?page=${page}&size=${size}`, {
    headers: authHeaders(token),
  })
  return jsonOrThrow(res)
}

export async function lawyerProposeSlot(token, rdvId, body) {
  const res = await apiFetch(`/api/rendezvous/avocat/${encodeURIComponent(rdvId)}/proposer-creneau`, {
    method: 'PATCH',
    headers: authHeaders(token),
    body: JSON.stringify(body),
  })
  return jsonOrThrow(res)
}

export async function lawyerCancelAppointment(token, rdvId, raisonAnnulation) {
  const res = await apiFetch(`/api/rendezvous/avocat/${encodeURIComponent(rdvId)}/annuler`, {
    method: 'PATCH',
    headers: authHeaders(token),
    body: JSON.stringify({ raisonAnnulation }),
  })
  return jsonOrThrow(res)
}

export async function lawyerMeetingAccess(token, rdvId) {
  const res = await apiFetch(`/api/rendezvous/avocat/${encodeURIComponent(rdvId)}/meeting-access`, {
    headers: authHeaders(token),
  })
  return jsonOrThrow(res)
}

export async function getAgenda(token) {
  const res = await apiFetch('/api/rendezvous/avocat/agenda', { headers: authHeaders(token) })
  return jsonOrThrow(res)
}

export async function updateAgendaConfig(token, body) {
  const res = await apiFetch('/api/rendezvous/avocat/agenda/config', {
    method: 'PUT',
    headers: authHeaders(token),
    body: JSON.stringify(body),
  })
  return jsonOrThrow(res)
}

export async function addAgendaPlage(token, body) {
  const res = await apiFetch('/api/rendezvous/avocat/agenda/plages', {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(body),
  })
  return jsonOrThrow(res)
}

export async function deleteAgendaPlage(token, idPlage) {
  const res = await apiFetch(`/api/rendezvous/avocat/agenda/plages/${encodeURIComponent(idPlage)}`, {
    method: 'DELETE',
    headers: authHeaders(token),
  })
  if (!res.ok) throw new Error(await parseApiError(res))
}

export async function addAgendaException(token, body) {
  const res = await apiFetch('/api/rendezvous/avocat/agenda/exceptions', {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(body),
  })
  return jsonOrThrow(res)
}

export async function deleteAgendaException(token, idException) {
  const res = await apiFetch(`/api/rendezvous/avocat/agenda/exceptions/${encodeURIComponent(idException)}`, {
    method: 'DELETE',
    headers: authHeaders(token),
  })
  if (!res.ok) throw new Error(await parseApiError(res))
}
