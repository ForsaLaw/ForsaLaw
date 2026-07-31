import SosButton from '../sos/SosButton.jsx'

export default function TopRightControls({
  isInboxRoute,
  isAuthenticated,
  user,
  t,
  language,
  onOpenLogin,
  onNavigateClientSpace,
  onNavigateLawyerSpace,
  onLogout,
  onToggleLanguage,
}) {
  if (isInboxRoute) return null

  return (
    <div className="top-right-controls">
      {/* Place en premier et affiche quel que soit l'etat de connexion : une arrestation est
          signalee par un proche, presque jamais par le titulaire d'un compte. */}
      <SosButton />
      {isAuthenticated ? (
        <>
          {user?.roleUser === 'client' ? (
            <>
              <button
                type="button"
                className="auth-top-btn"
                onClick={onNavigateClientSpace}
                title={user?.email ?? ''}
              >
                {t('nav_client_space')}
              </button>
              <button
                type="button"
                className="auth-top-btn"
                onClick={onNavigateLawyerSpace}
                title={user?.email ?? ''}
              >
                {t('nav_lawyer_space')}
              </button>
            </>
          ) : user?.roleUser === 'avocat' ? (
            <button
              type="button"
              className="auth-top-btn"
              onClick={onNavigateLawyerSpace}
              title={user?.email ?? ''}
            >
              {t('nav_lawyer_space')}
            </button>
          ) : (
            <span className="auth-user-label" title={user?.email}>
              {user?.prenom} {user?.nom}
            </span>
          )}
          <button type="button" className="auth-top-btn" onClick={onLogout}>
            Deconnexion
          </button>
        </>
      ) : (
        <button type="button" className="auth-top-btn" onClick={onOpenLogin}>
          {t('top_login')}
        </button>
      )}
      <button type="button" className="lang-toggle-btn" onClick={onToggleLanguage}>
        {language}
      </button>
    </div>
  )
}
