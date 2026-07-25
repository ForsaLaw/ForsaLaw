import { apiFetch, authHeaders, parseApiError } from './client.js'

async function jsonOrThrow(res) {
  if (!res.ok) throw new Error(await parseApiError(res))
  return res.json()
}

// ─── Topics ───────────────────────────────────────────────────────────────────

/**
 * List topics (public, paginated).
 * @returns {Promise<Page<ForumTopicDTO>>}
 */
export async function listTopics(token, { page = 0, size = 30 } = {}) {
  const q = new URLSearchParams({ page, size, sort: 'updatedAt,desc' })
  const res = await apiFetch(`/api/forum/topics?${q}`, {
    headers: token ? authHeaders(token) : { 'Content-Type': 'application/json' },
  })
  return jsonOrThrow(res)
}

/**
 * Get a single topic's detail.
 */
export async function getTopic(token, topicId) {
  const res = await apiFetch(`/api/forum/topics/${encodeURIComponent(topicId)}`, {
    headers: token ? authHeaders(token) : { 'Content-Type': 'application/json' },
  })
  return jsonOrThrow(res)
}

/**
 * Create a new topic (CLIENT or AVOCAT).
 * @param {{ title: string, content: string }} body
 */
export async function createTopic(token, body) {
  const res = await apiFetch('/api/forum/topics', {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(body),
  })
  return jsonOrThrow(res)
}

/**
 * Delete a topic (owner or ADMIN).
 */
export async function deleteTopic(token, topicId) {
  const res = await apiFetch(`/api/forum/topics/${encodeURIComponent(topicId)}`, {
    method: 'DELETE',
    headers: authHeaders(token),
  })
  if (!res.ok) throw new Error(await parseApiError(res))
}

// ─── Messages ─────────────────────────────────────────────────────────────────

/**
 * List messages for a topic (public, reactions populated if JWT present).
 * @returns {Promise<Page<ForumMessageDTO>>}
 */
export async function listMessages(token, topicId, { page = 0, size = 50 } = {}) {
  const q = new URLSearchParams({ page, size, sort: 'createdAt,asc' })
  const res = await apiFetch(`/api/forum/topics/${encodeURIComponent(topicId)}/messages?${q}`, {
    headers: token ? authHeaders(token) : { 'Content-Type': 'application/json' },
  })
  return jsonOrThrow(res)
}

/**
 * Post a new message in a topic (CLIENT or AVOCAT).
 * @param {{ content: string }} body
 */
export async function createMessage(token, topicId, body) {
  const res = await apiFetch(`/api/forum/topics/${encodeURIComponent(topicId)}/messages`, {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(body),
  })
  return jsonOrThrow(res)
}

/**
 * Delete a message (owner or ADMIN).
 */
export async function deleteMessage(token, messageId) {
  const res = await apiFetch(`/api/forum/messages/${encodeURIComponent(messageId)}`, {
    method: 'DELETE',
    headers: authHeaders(token),
  })
  if (!res.ok) throw new Error(await parseApiError(res))
}

// ─── Reactions ────────────────────────────────────────────────────────────────

/**
 * Set a reaction on a message ('LIKE' | 'DISLIKE' | 'LOVE' | 'LAUGH' | 'INSIGHTFUL').
 * Replaces any previous reaction.
 */
export async function setReaction(token, messageId, type) {
  const res = await apiFetch(`/api/forum/messages/${encodeURIComponent(messageId)}/reactions`, {
    method: 'PUT',
    headers: authHeaders(token),
    body: JSON.stringify({ type }),
  })
  return jsonOrThrow(res)
}

/**
 * Remove the current user's reaction from a message.
 */
export async function removeReaction(token, messageId) {
  const res = await apiFetch(`/api/forum/messages/${encodeURIComponent(messageId)}/reactions`, {
    method: 'DELETE',
    headers: authHeaders(token),
  })
  return jsonOrThrow(res)
}
