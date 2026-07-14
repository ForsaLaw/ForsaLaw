import { useCallback, useEffect, useState } from 'react'
import * as forumApi from '../api/forum.js'

export function useForumPageData({ token, isAuthenticated, user }) {
  const [topics, setTopics] = useState([])
  const [loading, setLoading] = useState(true)
  const [err, setErr] = useState(null)
  const [showNewPost, setShowNewPost] = useState(false)
  const [activeTopic, setActiveTopic] = useState(null)

  const canPost = isAuthenticated && user && (user.roleUser === 'client' || user.roleUser === 'avocat')

  const loadTopics = useCallback(async () => {
    setLoading(true)
    setErr(null)
    try {
      const page = await forumApi.listTopics(token, { size: 50 })
      setTopics(page?.content ?? [])
    } catch (e) {
      setErr(e?.message || String(e))
    } finally {
      setLoading(false)
    }
  }, [token])

  useEffect(() => { loadTopics() }, [loadTopics])

  const handleTopicCreated = (topic) => {
    setTopics((prev) => [topic, ...prev])
    setShowNewPost(false)
    setActiveTopic(topic)
  }

  const handleTopicDeleted = (topicId) => {
    setTopics((prev) => prev.filter((t) => t.id !== topicId))
    setActiveTopic(null)
  }

  const handleMessagesCountChange = (delta) => {
    setActiveTopic((prev) => (prev ? { ...prev, messagesCount: (prev.messagesCount ?? 0) + delta } : null))
    setTopics((prev) => prev.map((t) => (t.id === activeTopic?.id ? { ...t, messagesCount: (t.messagesCount ?? 0) + delta } : t)))
  }

  return {
    topics,
    loading,
    err,
    showNewPost,
    setShowNewPost,
    activeTopic,
    setActiveTopic,
    canPost,
    handleTopicCreated,
    handleTopicDeleted,
    handleMessagesCountChange,
  }
}
