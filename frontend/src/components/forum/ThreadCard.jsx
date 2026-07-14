import { motion } from 'framer-motion'
import { Clock, MessageSquare } from 'lucide-react'
import { fmtTime, initials, roleBadge } from './forumUtils.jsx'

export default function ThreadCard({ topic, onClick }) {
  return (
    <motion.div
      className="thread-card"
      whileHover={{ x: 3 }}
      transition={{ type: 'spring', stiffness: 320, damping: 22 }}
      onClick={onClick}
      style={{ cursor: 'pointer' }}
    >
      <div className="thread-body">
        <p className="thread-title">{topic.title}</p>
        <p className="thread-excerpt">{topic.content?.slice(0, 160)}{(topic.content?.length ?? 0) > 160 ? '…' : ''}</p>
        <div className="thread-meta">
          <span className="thread-author">
            <div className="author-avatar">{initials(topic.authorNomComplet)}</div>
            {topic.authorNomComplet}
            {roleBadge(topic.authorRole)}
          </span>
          <span className="thread-time">
            <Clock size={12} />
            {fmtTime(topic.updatedAt)}
          </span>
        </div>
      </div>
      <div className="thread-stats">
        <div className="stat-item tooltip-container">
          <MessageSquare size={16} />
          <span>{topic.messagesCount ?? 0}</span>
          <span className="tooltip">Réponses</span>
        </div>
      </div>
    </motion.div>
  )
}
