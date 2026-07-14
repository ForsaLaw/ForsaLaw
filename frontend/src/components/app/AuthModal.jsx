import { AnimatePresence, motion } from 'framer-motion'
import { ArrowRight, Lock, Mail, User } from 'lucide-react'

export default function AuthModal({
  authMode,
  authCardRef,
  authSubmitting,
  authFormError,
  authFormSuccess,
  formNom,
  formPrenom,
  formEmail,
  formPassword,
  onClose,
  onOpenMode,
  onSubmit,
  setFormNom,
  setFormPrenom,
  setFormEmail,
  setFormPassword,
}) {
  return (
    <AnimatePresence>
      {authMode && (
        <motion.div
          className="auth-modal-overlay"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          onClick={onClose}
          role="presentation"
        >
          <motion.div
            className="auth-modal-card"
            initial={{ y: 22, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            exit={{ y: 22, opacity: 0 }}
            onClick={(e) => e.stopPropagation()}
            role="dialog"
            aria-modal="true"
            ref={authCardRef}
          >
            <div className="auth-modal-tabs">
              <button type="button" className={authMode === 'login' ? 'active' : ''} onClick={() => onOpenMode('login')}>
                Connexion
              </button>
              <button type="button" className={authMode === 'register' ? 'active' : ''} onClick={() => onOpenMode('register')}>
                Inscription
              </button>
              <button type="button" className={authMode === 'forgot' ? 'active' : ''} onClick={() => onOpenMode('forgot')}>
                Mdp oublie
              </button>
            </div>

            <form className="auth-modal-form" onSubmit={onSubmit} noValidate>
              {authMode === 'register' && (
                <>
                  <label htmlFor="auth-modal-nom">
                    <User size={14} /> Nom
                  </label>
                  <input
                    id="auth-modal-nom"
                    type="text"
                    autoComplete="family-name"
                    placeholder="Nom"
                    value={formNom}
                    onChange={(e) => setFormNom(e.target.value)}
                  />
                  <label htmlFor="auth-modal-prenom">
                    <User size={14} /> Prenom
                  </label>
                  <input
                    id="auth-modal-prenom"
                    type="text"
                    autoComplete="given-name"
                    placeholder="Prenom"
                    value={formPrenom}
                    onChange={(e) => setFormPrenom(e.target.value)}
                  />
                </>
              )}

              <label htmlFor="auth-modal-email">
                <Mail size={14} /> Email
              </label>
              <input
                id="auth-modal-email"
                type="email"
                autoComplete="email"
                placeholder="vous@domaine.com"
                value={formEmail}
                onChange={(e) => setFormEmail(e.target.value)}
              />

              {authMode !== 'forgot' && (
                <>
                  <label htmlFor="auth-modal-password">
                    <Lock size={14} /> Mot de passe
                  </label>
                  <input
                    id="auth-modal-password"
                    type="password"
                    autoComplete={authMode === 'login' ? 'current-password' : 'new-password'}
                    placeholder="••••••••"
                    value={formPassword}
                    onChange={(e) => setFormPassword(e.target.value)}
                  />
                </>
              )}

              {authMode === 'login' && (
                <button type="button" className="auth-link-btn" onClick={() => onOpenMode('forgot')}>
                  Mot de passe oublie ?
                </button>
              )}

              {authFormError && (
                <p className="auth-form-message auth-form-message--error" role="alert">
                  {authFormError}
                </p>
              )}
              {authFormSuccess && (
                <p className="auth-form-message auth-form-message--success" role="status">
                  {authFormSuccess}
                </p>
              )}

              <button className="auth-submit-main" type="submit" disabled={authSubmitting}>
                {authMode === 'login'
                  ? 'Se connecter'
                  : authMode === 'register'
                    ? 'S inscrire'
                    : 'Envoyer lien reset'}
                <ArrowRight size={17} />
              </button>

              {(authMode === 'login' || authMode === 'register') && (
                <a className="auth-google-btn" href="/api/auth/google">
                  Continuer avec Google
                </a>
              )}
            </form>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}
