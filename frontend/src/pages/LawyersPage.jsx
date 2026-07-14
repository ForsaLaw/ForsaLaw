import { motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { Loader2 } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import PageHeader from '../components/PageHeader'
import LawyerTradingCard from '../components/lawyers/LawyerTradingCard.jsx'
import { useAuth } from '../context/AuthContext.jsx'
import { useLawyersPageData } from '../hooks/useLawyersPageData.js'
import '../styles/Lawyers.css'

const LawyersPage = () => {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { isAuthenticated, user, token } = useAuth()
  const {
    selectedSpecs,
    specialties,
    filteredLawyers,
    toggleSpec,
    loading,
    error,
    canContactBase,
    canBook,
    scheduleByLawyerId,
    canContactByLawyerId,
  } = useLawyersPageData({ isAuthenticated, user, token })

  return (
    <div className="lawyers-page">
      <PageHeader
        className="lawyers-header"
        tag={t('lawyers_tag')}
        tagClassName="lawyers-header-tag"
        title={t('lawyers_title')}
        titleClassName="lawyers-title"
      />

      {error && (
        <div style={{ color: '#ffb4a8', padding: '0 2rem 1rem' }}>{error}</div>
      )}

      <div className="lawyers-content">
        <aside className="lawyers-sidebar">
          <div className="filter-group">
            <h3 className="filter-title">{t('lawyers_filter')}</h3>
            {specialties.map((spec) => (
              <label key={spec} className="filter-checkbox">
                <input
                  type="checkbox"
                  checked={selectedSpecs.includes(spec)}
                  onChange={() => toggleSpec(spec)}
                />
                {spec}
              </label>
            ))}
          </div>
        </aside>

        <main className="cards-grid">
          {loading && (
            <div style={{ padding: '2rem', color: 'var(--gold)' }}>
              <Loader2 className="forsalaw-spin" size={24} />
            </div>
          )}

          {!loading && filteredLawyers.map((lawyer, idx) => (
            <motion.div
              key={lawyer.id}
              initial={{ opacity: 0, scale: 0.9, y: 20 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              transition={{ delay: idx * 0.05, duration: 0.25 }}
            >
              <LawyerTradingCard
                lawyer={lawyer}
                canContact={canContactBase && !!canContactByLawyerId[lawyer.id]}
                canBook={canBook}
                showActions={user?.roleUser !== 'avocat'}
                t={t}
                scheduleSummary={scheduleByLawyerId[lawyer.id] ?? null}
                contactLabel={
                  !isAuthenticated
                    ? 'Connexion requise'
                    : user?.roleUser !== 'client'
                      ? 'Client requis'
                      : '1er RDV confirme requis'
                }
                onContact={() => {
                  const allowed = canContactBase && !!canContactByLawyerId[lawyer.id]
                  if (!allowed) {
                    if (!isAuthenticated) navigate('/auth')
                    return
                  }
                  navigate(`/inbox?avocatId=${encodeURIComponent(lawyer.id)}`)
                }}
                onBook={() => {
                  if (!canBook) {
                    navigate('/auth')
                    return
                  }
                  navigate(`/appointments/new/${encodeURIComponent(lawyer.id)}`)
                }}
              />
            </motion.div>
          ))}

          {!loading && filteredLawyers.length === 0 && (
            <div style={{ padding: '2rem', opacity: 0.8 }}>Aucun avocat ne correspond au filtre.</div>
          )}
        </main>
      </div>
    </div>
  )
}

export default LawyersPage
