import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import RouteLoadingScreen from '../components/RouteLoadingScreen.jsx'

export default function GoogleOAuthCallbackPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const { completeOAuthLogin } = useAuth()
  const [error, setError] = useState(null)

  useEffect(() => {
    // Le JWT n'est plus passe dans l'URL : le backend a depose un cookie HttpOnly avant de
    // rediriger. On resout donc la session cote serveur (GET /api/users/me).
    if (searchParams.get('error')) {
      setError('La connexion Google a échoué. Veuillez réessayer.')
      return
    }
    let cancelled = false
    ;(async () => {
      try {
        await completeOAuthLogin()
        if (!cancelled) navigate('/', { replace: true })
      } catch (e) {
        if (!cancelled) setError(e?.message || String(e))
      }
    })()
    return () => {
      cancelled = true
    }
  }, [searchParams, completeOAuthLogin, navigate])

  if (error) {
    return (
      <div
        style={{
          minHeight: '100vh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          padding: '2rem',
          color: '#f5f5f5',
          background: '#121212',
          textAlign: 'center',
          maxWidth: '32rem',
          margin: '0 auto',
        }}
      >
        <div>
          <p style={{ color: '#e8c76b', letterSpacing: '0.12em', fontSize: '0.75rem' }}>OAuth</p>
          <p style={{ marginTop: '1rem', lineHeight: 1.5 }}>{error}</p>
          <button
            type="button"
            style={{
              marginTop: '1.5rem',
              padding: '0.6rem 1rem',
              background: 'rgba(232,199,107,0.2)',
              border: '1px solid rgba(232,199,107,0.45)',
              color: '#f5f5f5',
              cursor: 'pointer',
            }}
            onClick={() => navigate('/', { replace: true })}
          >
            Retour à l’accueil
          </button>
        </div>
      </div>
    )
  }

  return <RouteLoadingScreen />
}
