import { apiFetch, authHeaders, parseApiError } from './client.js'

function roleBase(roleUser) {
  if (roleUser === 'avocat') return '/api/messenger/avocat'
  return '/api/messenger'
}

export async function listConversations(token, roleUser, { page = 0, size = 50 } = {}) {
  const base = roleBase(roleUser)
  const res = await apiFetch(`${base}/conversations?page=${page}&size=${size}`, {
    headers: authHeaders(token),
  })
  if (!res.ok) throw new Error(await parseApiError(res))
  return res.json()
}

export async function getConversationMessages(token, roleUser, conversationId, { page = 0, size = 100 } = {}) {
  const base = roleBase(roleUser)
  const res = await apiFetch(`${base}/conversations/${encodeURIComponent(conversationId)}/messages?page=${page}&size=${size}`, {
    headers: authHeaders(token),
  })
  if (!res.ok) throw new Error(await parseApiError(res))
  return res.json()
}

export async function markConversationRead(token, roleUser, conversationId) {
  const base = roleBase(roleUser)
  const res = await apiFetch(`${base}/conversations/${encodeURIComponent(conversationId)}/read`, {
    method: 'POST',
    headers: authHeaders(token),
  })
  if (!res.ok) throw new Error(await parseApiError(res))
}

export async function sendTextMessage(token, roleUser, targetId, content) {
  const isAvocat = roleUser === 'avocat'
  const path = isAvocat
    ? `/api/messenger/avocat/messages/to-client/${encodeURIComponent(targetId)}`
    : `/api/messenger/messages/to-avocat/${encodeURIComponent(targetId)}`
  const res = await apiFetch(path, {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify({ content }),
  })
  if (!res.ok) throw new Error(await parseApiError(res))
  return res.json()
}

export async function openOrGetConversation(token, roleUser, targetId) {
  const isAvocat = roleUser === 'avocat'
  const path = isAvocat
    ? `/api/messenger/avocat/conversations/with-client/${encodeURIComponent(targetId)}`
    : `/api/messenger/conversations/with-avocat/${encodeURIComponent(targetId)}`
  const res = await apiFetch(path, {
    method: 'POST',
    headers: authHeaders(token),
  })
  if (!res.ok) throw new Error(await parseApiError(res))
  return res.json()
}

export async function sendMessageWithAttachments(token, roleUser, conversationId, { content = '', files = [] }) {
  const base = roleBase(roleUser)
  const fd = new FormData()
  if (content?.trim()) fd.append('content', content.trim())
  for (const file of files) {
    fd.append('files', file)
  }
  const res = await apiFetch(`${base}/conversations/${encodeURIComponent(conversationId)}/messages/attachments`, {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: fd,
  })
  if (!res.ok) throw new Error(await parseApiError(res))
  return res.json()
}
