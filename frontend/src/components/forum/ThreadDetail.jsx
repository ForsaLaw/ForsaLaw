import { useCallback, useEffect, useRef, useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import {
  AlertTriangle,
  ChevronLeft,
  Clock,
  Heart,
  Laugh,
  Lightbulb,
  Loader2,
  Send,
  ThumbsDown,
  ThumbsUp,
  Trash2,
} from 'lucide-react'
import * as forumApi from '../../api/forum.js'
import { fmtTime, initials, roleBadge } from './forumUtils.jsx'

const REACTIONS = [
  { type: 'LIKE', icon: ThumbsUp, label: 'J\'aime', color: '#4ade80' },
  { type: 'DISLIKE', icon: ThumbsDown, label: 'Pas d\'accord', color: '#f87171' },
  { type: 'LOVE', icon: Heart, label: 'J\'adore', color: '#f472b6' },
  { type: 'LAUGH', icon: Laugh, label: 'Drôle', color: '#facc15' },
  { type: 'INSIGHTFUL', icon: Lightbulb, label: 'Instructif', color: '#60a5fa' },
]

const REACTION_EMOJI = { LIKE: '👍', DISLIKE: '👎', LOVE: '❤️', LAUGH: '😂', INSIGHTFUL: '💡' }

export default function ThreadDetail({ topic, token, user, onBack, onTopicDeleted, onMessagesCountChange }) {
  const [messages, setMessages] = useState([])
  const [loading, setLoading] = useState(true)
  const [reply, setReply] = useState('')
  const [sending, setSending] = useState(false)
  const [err, setErr] = useState(null)
  const [reactionMenu, setReactionMenu] = useState(null)
  const bottomRef = useRef(null)

  const canInteract = user && (user.roleUser === 'client' || user.roleUser === 'avocat' || user.roleUser === 'admin')
  const canDelete = (msg) => user && (user.email === msg.authorEmail || user.id === msg.authorUserId || user.roleUser === 'admin')
  const canDeleteTopic = user && (user.id === topic.authorUserId || user.roleUser === 'admin')

  const loadMessages = useCallback(async () => {
    setLoading(true)
    try {
      const page = await forumApi.listMessages(token, topic.id, { size: 100 })
      setMessages(page?.content ?? [])
    } catch (e) {
      setErr(e?.message || String(e))
    } finally {
      setLoading(false)
    }
  }, [token, topic.id])

  useEffect(() => { loadMessages() }, [loadMessages])
  useEffect(() => { if (!loading) bottomRef.current?.scrollIntoView({ behavior: 'smooth' }) }, [messages, loading])

  const handleSend = async () => {
    if (!reply.trim() || !token) return
    setSending(true)
    setErr(null)
    try {
      const msg = await forumApi.createMessage(token, topic.id, { content: reply.trim() })
      setMessages((prev) => [...prev, msg])
      setReply('')
      if (onMessagesCountChange) onMessagesCountChange(1)
    } catch (e) {
      setErr(e?.message || String(e))
    } finally {
      setSending(false)
    }
  }

  const handleDeleteMsg = async (msgId) => {
    if (!token || !window.confirm('Supprimer ce message ?')) return
    try {
      await forumApi.deleteMessage(token, msgId)
      setMessages((prev) => prev.filter((m) => m.id !== msgId))
      if (onMessagesCountChange) onMessagesCountChange(-1)
    } catch (e) {
      setErr(e?.message || String(e))
    }
  }

  const handleDeleteTopic = async () => {
    if (!token || !window.confirm('Supprimer ce sujet et tous ses messages ?')) return
    try {
      await forumApi.deleteTopic(token, topic.id)
      onTopicDeleted(topic.id)
    } catch (e) {
      setErr(e?.message || String(e))
    }
  }

  const handleReaction = async (msgId, type) => {
    if (!token) return
    setReactionMenu(null)
    const msg = messages.find((m) => m.id === msgId)
    if (!msg) return
    try {
      let updated
      if (msg.myReaction === type) updated = await forumApi.removeReaction(token, msgId)
      else updated = await forumApi.setReaction(token, msgId, type)
      setMessages((prev) => prev.map((m) => (m.id === msgId ? updated : m)))
    } catch (e) {
      setErr(e?.message || String(e))
    }
  }

  return (
    <motion.div className="thread-detail" initial={{ opacity: 0, x: 24 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: 24 }} transition={{ duration: 0.22 }}>
      <div className="thread-detail__header">
        <button type="button" className="thread-detail__back" onClick={onBack}>
          <ChevronLeft size={16} /> Retour au forum
        </button>
        {canDeleteTopic && (
          <button type="button" className="thread-detail__del-topic" onClick={handleDeleteTopic}>
            <Trash2 size={13} /> Supprimer le sujet
          </button>
        )}
      </div>

      <div className="thread-detail__title">
        <h2>{topic.title}</h2>
        <div className="thread-detail__meta">
          <div className="author-avatar sm">{initials(topic.authorNomComplet)}</div>
          <span>{topic.authorNomComplet}</span>
          {roleBadge(topic.authorRole)}
          <Clock size={11} style={{ opacity: 0.4 }} />
          <span style={{ opacity: 0.4, fontSize: '0.7rem' }}>{fmtTime(topic.createdAt)}</span>
        </div>
      </div>

      <div className="thread-detail__op"><p>{topic.content}</p></div>

      <div className="thread-detail__messages">
        {err && <div className="forum-err"><AlertTriangle size={14} /> {err}</div>}
        {loading && <div className="thread-detail__loading"><Loader2 className="forsalaw-spin" size={22} style={{ color: 'var(--gold)' }} /></div>}
        {!loading && messages.length === 0 && <div className="thread-detail__empty">Aucune réponse pour le moment. Soyez le premier à répondre.</div>}
        {!loading && messages.map((msg, i) => {
          const totalReactions = Object.values(msg.reactionCounts ?? {}).reduce((s, v) => s + v, 0)
          return (
            <motion.div key={msg.id} className="forum-msg" initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.04 }}>
              <div className="forum-msg__avatar">{initials(msg.authorNomComplet)}</div>
              <div className="forum-msg__body">
                <div className="forum-msg__meta">
                  <span className="forum-msg__author">{msg.authorNomComplet}</span>
                  {roleBadge(msg.authorRole)}
                  <span className="forum-msg__time"><Clock size={10} /> {fmtTime(msg.createdAt)}</span>
                  <div style={{ marginLeft: 'auto', display: 'flex', gap: '0.4rem' }}>
                    {canInteract && (
                      <button type="button" className="forum-msg__react-btn" onClick={() => setReactionMenu(reactionMenu === msg.id ? null : msg.id)} title="Réagir">
                        {msg.myReaction ? REACTION_EMOJI[msg.myReaction] : '😶'}
                      </button>
                    )}
                    {canDelete(msg) && (
                      <button type="button" className="forum-msg__del" onClick={() => handleDeleteMsg(msg.id)}>
                        <Trash2 size={11} />
                      </button>
                    )}
                  </div>
                </div>
                <p className="forum-msg__content">{msg.content}</p>
                {totalReactions > 0 && (
                  <div className="forum-msg__reactions">
                    {Object.entries(msg.reactionCounts ?? {}).filter(([, v]) => v > 0).map(([type, count]) => (
                      <span
                        key={type}
                        className={`forum-msg__reaction-pill${msg.myReaction === type ? ' mine' : ''}`}
                        onClick={() => canInteract && handleReaction(msg.id, type)}
                        style={{ cursor: canInteract ? 'pointer' : 'default' }}
                      >
                        {REACTION_EMOJI[type]} {count}
                      </span>
                    ))}
                  </div>
                )}
                <AnimatePresence>
                  {reactionMenu === msg.id && (
                    <motion.div className="forum-reaction-picker" initial={{ opacity: 0, scale: 0.85, y: 4 }} animate={{ opacity: 1, scale: 1, y: 0 }} exit={{ opacity: 0, scale: 0.85, y: 4 }} transition={{ duration: 0.15 }}>
                      {REACTIONS.map((r) => {
                        const Icon = r.icon
                        const active = msg.myReaction === r.type
                        return (
                          <button
                            key={r.type}
                            type="button"
                            className={`forum-reaction-btn${active ? ' active' : ''}`}
                            title={r.label}
                            style={{ '--rc': r.color }}
                            onClick={() => handleReaction(msg.id, r.type)}
                          >
                            <Icon size={16} />
                          </button>
                        )
                      })}
                    </motion.div>
                  )}
                </AnimatePresence>
              </div>
            </motion.div>
          )
        })}
        <div ref={bottomRef} />
      </div>

      {canInteract ? (
        <div className="thread-detail__reply">
          <textarea
            className="thread-detail__textarea"
            rows={3}
            value={reply}
            onChange={(e) => setReply(e.target.value)}
            placeholder="Votre réponse…"
            onKeyDown={(e) => { if (e.key === 'Enter' && e.ctrlKey) handleSend() }}
          />
          <button type="button" className="thread-detail__send" disabled={sending || !reply.trim()} onClick={handleSend}>
            {sending ? <Loader2 className="forsalaw-spin" size={14} /> : <Send size={14} />}
            Publier
          </button>
        </div>
      ) : (
        <div className="thread-detail__login-prompt">Connectez-vous pour participer à la discussion.</div>
      )}
    </motion.div>
  )
}
