import { AnimatePresence, motion } from 'framer-motion'

export default function AuthIntroOverlay({ authIntroMode, authIntroStriking, onStrike }) {
  return (
    <AnimatePresence>
      {authIntroMode && (
        <motion.div
          className="auth-intro-overlay"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
        >
          <motion.div
            className={`auth-intro-stage${authIntroStriking ? ' is-striking' : ''}`}
            initial={{ scale: 0.95, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            exit={{ scale: 0.97, opacity: 0 }}
            transition={{ duration: 0.25 }}
          >
            <p className="auth-intro-title">Validation d&apos;audience</p>
            <p className="auth-intro-sub">Cliquez sur le marteau pour ouvrir la chambre de connexion.</p>
            <button
              type="button"
              className="auth-intro-gavel-btn"
              onClick={onStrike}
              disabled={authIntroStriking}
              aria-label="Frapper avec le marteau"
            >
              <svg
                className="auth-intro-gavel-svg"
                viewBox="0 0 320 130"
                aria-hidden="true"
                focusable="false"
              >
                <defs>
                  <linearGradient id="gavelWood" x1="0%" y1="0%" x2="100%" y2="100%">
                    <stop offset="0%" stopColor="#7b4a2c" />
                    <stop offset="45%" stopColor="#5a331f" />
                    <stop offset="100%" stopColor="#2c180f" />
                  </linearGradient>
                  <linearGradient id="gavelMetal" x1="0%" y1="0%" x2="0%" y2="100%">
                    <stop offset="0%" stopColor="#f4d27b" />
                    <stop offset="100%" stopColor="#ab7a2b" />
                  </linearGradient>
                </defs>
                <g transform="rotate(0 180 72)">
                  <rect x="40" y="64" width="160" height="12" rx="6" fill="url(#gavelWood)" />
                  <rect x="178" y="45" width="80" height="40" rx="9" fill="url(#gavelWood)" />
                  <rect x="201" y="50" width="34" height="30" rx="6" fill="url(#gavelMetal)" />
                  <rect x="170" y="47" width="12" height="36" rx="5" fill="#3c2113" />
                  <rect x="257" y="47" width="12" height="36" rx="5" fill="#3c2113" />
                </g>
              </svg>
            </button>
            <div className="auth-intro-block" aria-hidden />
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}
