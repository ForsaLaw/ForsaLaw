import { motion } from 'framer-motion'
import { AlertTriangle, Loader2, Plus, X } from 'lucide-react'
import { useState } from 'react'
import * as forumApi from '../../api/forum.js'

export default function NewTopicModal({ token, onCreated, onClose }) {
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState(null)

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!title.trim() || !content.trim()) {
      setErr('Le titre et le contenu sont requis.')
      return
    }
    setBusy(true)
    setErr(null)
    try {
      const topic = await forumApi.createTopic(token, { title: title.trim(), content: content.trim() })
      onCreated(topic)
    } catch (e2) {
      setErr(e2?.message || String(e2))
    } finally {
      setBusy(false)
    }
  }

  return (
    <motion.div
      className="forum-modal-overlay"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      onClick={onClose}
    >
      <motion.div
        className="forum-modal"
        initial={{ scale: 0.92, opacity: 0, y: 16 }}
        animate={{ scale: 1, opacity: 1, y: 0 }}
        exit={{ scale: 0.92, opacity: 0 }}
        transition={{ type: 'spring', stiffness: 280, damping: 24 }}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="forum-modal__header">
          <span>Nouveau sujet</span>
          <button type="button" className="forum-modal__close" onClick={onClose}><X size={14} /></button>
        </div>
        <form className="forum-modal__form" onSubmit={handleSubmit}>
          <label className="forum-modal__label">
            Titre
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="Ex : Mon employeur m'a licencié sans préavis…"
              autoFocus
              maxLength={200}
            />
          </label>
          <label className="forum-modal__label">
            Contenu
            <textarea
              rows={6}
              value={content}
              onChange={(e) => setContent(e.target.value)}
              placeholder="Décrivez votre situation en détail…"
            />
          </label>
          {err && (
            <p className="forum-modal__error">
              <AlertTriangle size={13} /> {err}
            </p>
          )}
          <button type="submit" className="forum-modal__submit" disabled={busy}>
            {busy ? <Loader2 className="forsalaw-spin" size={14} /> : <Plus size={14} />}
            Publier le sujet
          </button>
        </form>
      </motion.div>
    </motion.div>
  )
}
