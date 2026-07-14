import { motion, AnimatePresence } from 'framer-motion'
import {
  Plus, Loader2,
  AlertTriangle,
} from 'lucide-react'
import { useTranslation } from 'react-i18next'
import NewTopicModal from '../components/forum/NewTopicModal.jsx'
import SidebarWidgets from '../components/forum/SidebarWidgets.jsx'
import ThreadDetail from '../components/forum/ThreadDetail.jsx'
import ThreadCard from '../components/forum/ThreadCard.jsx'
import { useAuth } from '../context/AuthContext.jsx'
import PageHeader from '../components/PageHeader'
import { useForumPageData } from '../hooks/useForumPageData.js'
import '../styles/Forum.css'
// ─── Forum Page ───────────────────────────────────────────────────────────────
export default function ForumPage() {
  const { t } = useTranslation()
  const { token, user, isAuthenticated } = useAuth()
  const {
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
  } = useForumPageData({ token, isAuthenticated, user })

  // If a topic is selected, show detail view
  if (activeTopic) {
    return (
      <div className="forum-page">
        <PageHeader
          className="forum-header"
          tag={t('forum_tag')}
          tagClassName="forum-header-tag"
          title={t('forum_title')}
          titleClassName="forum-title"
        />
        <div className="forum-content">
          <AnimatePresence mode="wait">
            <ThreadDetail
              key={activeTopic.id}
              topic={activeTopic}
              token={token}
              user={user}
              onBack={() => setActiveTopic(null)}
              onTopicDeleted={handleTopicDeleted}
              onMessagesCountChange={handleMessagesCountChange}
            />
          </AnimatePresence>
          <aside className="forum-sidebar">
            <SidebarWidgets t={t} topics={topics} onSelectTopic={setActiveTopic} />
          </aside>
        </div>
      </div>
    )
  }

  return (
    <div className="forum-page">
      <PageHeader
        className="forum-header"
        tag={t('forum_tag')}
        tagClassName="forum-header-tag"
        title={t('forum_title')}
        titleClassName="forum-title"
      />

      {/* Toolbar */}
      <section className="forum-toolbar">
        <div className="forum-filters">
          <span className="forum-count-label">
            {loading ? <Loader2 className="forsalaw-spin" size={14} style={{ color: 'var(--gold)' }} /> : `${topics.length} sujets`}
          </span>
        </div>
        <div className="forum-actions">
          {canPost ? (
            <button className="forum-new-btn" onClick={() => setShowNewPost(true)}>
              <Plus size={14} style={{ display: 'inline', marginRight: 5 }} />
              {t('forum_new_post')}
            </button>
          ) : (
            <span className="forum-login-hint">Connectez-vous pour poster</span>
          )}
        </div>
      </section>

      {/* Main content */}
      <div className="forum-content">
        <div className="forum-threads">
          {err && <div className="forum-err"><AlertTriangle size={14} /> {err}</div>}
          {loading && (
            <div className="forum-loading">
              {[0,1,2,3].map(i => (
                <div key={i} className="forum-skeleton" style={{ animationDelay: `${i * 0.12}s` }} />
              ))}
            </div>
          )}
          <AnimatePresence mode="popLayout">
            {!loading && topics.map((topic, i) => (
              <motion.div
                key={topic.id}
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, scale: 0.97 }}
                transition={{ duration: 0.18, delay: i * 0.04 }}
              >
                <ThreadCard topic={topic} onClick={() => setActiveTopic(topic)} />
              </motion.div>
            ))}
          </AnimatePresence>
          {!loading && topics.length === 0 && !err && (
            <div className="forum-empty">
              <p>Aucun sujet pour le moment.</p>
              {canPost && (
                <button className="forum-new-btn" style={{ marginTop: '1rem' }} onClick={() => setShowNewPost(true)}>
                  Créer le premier sujet
                </button>
              )}
            </div>
          )}
        </div>

        <aside className="forum-sidebar">
          <SidebarWidgets t={t} topics={topics} onSelectTopic={setActiveTopic} />
        </aside>
      </div>

      {/* New Topic Modal */}
      <AnimatePresence>
        {showNewPost && isAuthenticated && (
          <NewTopicModal
            key="new-topic-modal"
            token={token}
            onCreated={handleTopicCreated}
            onClose={() => setShowNewPost(false)}
          />
        )}
      </AnimatePresence>
    </div>
  )
}
