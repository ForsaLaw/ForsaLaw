import { MapPin, Star, User } from 'lucide-react'

const rankFromProfile = (avocat) => {
  if (avocat.verificationStatus === 'APPROVED') return 'S-TIER'
  if (avocat.verificationStatus === 'PENDING') return 'A-TIER'
  return 'B-TIER'
}

export default function LawyerTradingCard({
  lawyer,
  canContact,
  canBook,
  onContact,
  onBook,
  t,
  scheduleSummary,
  contactLabel,
  showActions = true,
}) {
  const fullName = `Me. ${lawyer.userPrenom ?? ''} ${lawyer.userNom ?? ''}`.trim()
  const specialty = lawyer.specialiteLibelle || lawyer.specialite || '—'
  const statusText = lawyer.verificationStatus === 'APPROVED'
    ? 'Profil vérifié'
    : lawyer.verificationStatus === 'PENDING'
      ? 'Demande en attente'
      : 'Demande non approuvée'

  return (
    <div className="lawyer-card-wrapper">
      <div className="lawyer-card">
        <div className="card-header">
          <div className="card-header-bg" />
          <div className="card-rank">{rankFromProfile(lawyer)}</div>
          <div className="card-avatar">
            {lawyer.profilePhotoPublicUrl ? (
              <img src={lawyer.profilePhotoPublicUrl} alt={fullName} />
            ) : (
              <User size={40} className="icon-heavy-shadow" />
            )}
          </div>
        </div>

        <div className="card-body">
          <h3 className="card-name">{fullName}</h3>
          <span className="card-specialty">{specialty}</span>

          <div className="card-stats">
            <div className="stat-box">
              <span className="stat-value">{lawyer.verifie ? '100%' : '—'}</span>
              <span className="stat-label">profil</span>
            </div>
            <div className="stat-box">
              <span className="stat-value">{lawyer.anneesExperience ?? 0} {t('lawyer_years')}</span>
              <span className="stat-label">{t('lawyer_xp')}</span>
            </div>
            <div className="stat-box">
              <span className="stat-value">{lawyer.totalDossiers ?? 0}</span>
              <span className="stat-label">{t('lawyer_cases')}</span>
            </div>
            <div className="stat-box">
              <span className="stat-value" style={{ color: 'var(--gold)' }}>
                {(lawyer.noteMoyenne ?? 0).toFixed(1)} <Star size={10} className="icon-heavy-shadow" style={{ display: 'inline', fill: 'var(--gold)' }} />
              </span>
              <span className="stat-label">{t('lawyer_rating')}</span>
            </div>
          </div>

          <p className="card-location">
            <MapPin size={14} className="icon-heavy-shadow" /> {lawyer.ville || 'Tunisie'}
          </p>
          <p className="card-location" style={{ marginTop: '0.2rem', opacity: 0.8 }}>
            {statusText}
          </p>
          <div className="card-schedule">
            <p className="card-schedule__title">Horaires</p>
            {scheduleSummary ? (
              <p className="card-schedule__line">{scheduleSummary}</p>
            ) : (
              <p className="card-schedule__line card-schedule__line--muted">Horaires indisponibles pour le moment</p>
            )}
          </div>

          {showActions && (
            <div style={{ marginTop: 'auto', display: 'grid', gap: '0.5rem' }}>
              <button className="brutal-btn card-action" onClick={onContact} disabled={!canContact}>
                {canContact ? 'Contacter' : contactLabel}
              </button>
              <button className="brutal-btn card-action" onClick={onBook} disabled={!canBook}>
                {canBook ? 'Prendre RDV' : 'Client requis'}
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
