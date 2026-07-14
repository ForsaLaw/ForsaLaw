import { TRENDING_TAGS, initials } from './forumUtils.jsx'

export default function SidebarWidgets({ t, topics, onSelectTopic }) {
  const topReplied = [...topics]
    .sort((a, b) => (b.messagesCount ?? 0) - (a.messagesCount ?? 0))
    .slice(0, 4)

  return (
    <>
      <div className="sidebar-widget fellawra-widget">
        <img src="/fellawra.png" alt="Fellawra" className="fellawra-widget-img" />
        <p className="fellawra-widget-text">{t('forum_fellawra_text')}</p>
        <button className="fellawra-ask-btn">{t('forum_ask_fellawra')}</button>
      </div>

      {topReplied.length > 0 && (
        <div className="sidebar-widget">
          <p className="sidebar-widget-title">Plus actifs</p>
          {topReplied.map((topic, i) => (
            <div key={topic.id} className="contributor-item" style={{ cursor: 'pointer' }} onClick={() => onSelectTopic(topic)}>
              <span className="contributor-rank">#{i + 1}</span>
              <div className="contributor-avatar sm">{initials(topic.authorNomComplet)}</div>
              <span className="contributor-name" style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', fontSize: '0.72rem' }}>
                {topic.title}
              </span>
              <span className="contributor-posts">{topic.messagesCount ?? 0} rép.</span>
            </div>
          ))}
        </div>
      )}

      <div className="sidebar-widget">
        <p className="sidebar-widget-title">{t('forum_trending')}</p>
        <div className="trending-tags">
          {TRENDING_TAGS.map((tag) => (
            <button key={tag} className="trending-tag">{tag}</button>
          ))}
        </div>
      </div>
    </>
  )
}
