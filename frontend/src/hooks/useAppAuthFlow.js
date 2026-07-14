import { useEffect, useRef, useState } from 'react'
import * as authApi from '../api/auth.js'

export function useAppAuthFlow({ login, register }) {
  const [authMode, setAuthMode] = useState(null)
  const [authIntroMode, setAuthIntroMode] = useState(null)
  const [authIntroStriking, setAuthIntroStriking] = useState(false)
  const [formNom, setFormNom] = useState('')
  const [formPrenom, setFormPrenom] = useState('')
  const [formEmail, setFormEmail] = useState('')
  const [formPassword, setFormPassword] = useState('')
  const [authFormError, setAuthFormError] = useState(null)
  const [authFormSuccess, setAuthFormSuccess] = useState(null)
  const [authSubmitting, setAuthSubmitting] = useState(false)

  const authCardRef = useRef(null)
  const lastActiveElRef = useRef(null)
  const authIntroTimerRef = useRef(null)

  useEffect(() => {
    return () => {
      if (authIntroTimerRef.current) {
        clearTimeout(authIntroTimerRef.current)
      }
    }
  }, [])

  useEffect(() => {
    if (!authMode) return

    lastActiveElRef.current = document.activeElement
    const root = authCardRef.current
    const focusable = root?.querySelector(
      'button, [href], input, textarea, select, [tabindex]:not([tabindex="-1"])',
    )
    if (focusable) focusable.focus()

    const onTrap = (e) => {
      if (e.key !== 'Tab') return
      const container = authCardRef.current
      if (!container) return

      const els = Array.from(
        container.querySelectorAll(
          'button, [href], input, textarea, select, [tabindex]:not([tabindex="-1"])',
        ),
      ).filter((el) => !el.hasAttribute('disabled') && !el.getAttribute('aria-hidden'))

      if (els.length === 0) return

      const first = els[0]
      const last = els[els.length - 1]

      if (e.shiftKey) {
        if (document.activeElement === first || !container.contains(document.activeElement)) {
          e.preventDefault()
          last.focus()
        }
      } else if (document.activeElement === last) {
        e.preventDefault()
        first.focus()
      }
    }

    window.addEventListener('keydown', onTrap)
    return () => {
      window.removeEventListener('keydown', onTrap)
      const lastActive = lastActiveElRef.current
      if (lastActive && typeof lastActive.focus === 'function') {
        lastActive.focus()
      }
    }
  }, [authMode])

  const openAuth = (mode, withIntro = false) => {
    setAuthFormError(null)
    setAuthFormSuccess(null)
    if (withIntro) {
      setAuthMode(null)
      setAuthIntroMode(mode)
      setAuthIntroStriking(false)
      return
    }
    setAuthIntroMode(null)
    setAuthIntroStriking(false)
    setAuthMode(mode)
  }

  const closeAuth = () => setAuthMode(null)

  const closeAuthIntro = () => {
    setAuthIntroMode(null)
    setAuthIntroStriking(false)
  }

  const handleAuthIntroStrike = () => {
    if (!authIntroMode || authIntroStriking) return
    setAuthIntroStriking(true)
    if (authIntroTimerRef.current) clearTimeout(authIntroTimerRef.current)
    authIntroTimerRef.current = setTimeout(() => {
      setAuthIntroMode(null)
      setAuthIntroStriking(false)
      setAuthMode(authIntroMode)
    }, 680)
  }

  const handleAuthSubmit = async (e) => {
    e.preventDefault()
    setAuthFormError(null)
    setAuthFormSuccess(null)

    const email = formEmail.trim()
    if (authMode === 'forgot') {
      setAuthSubmitting(true)
      try {
        const msg = await authApi.forgotPassword({ email })
        setAuthFormSuccess(typeof msg === 'string' ? msg : String(msg))
      } catch (err) {
        setAuthFormError(err?.message || String(err))
      } finally {
        setAuthSubmitting(false)
      }
      return
    }

    if (!email) {
      setAuthFormError("L'email est requis.")
      return
    }
    if (!formPassword) {
      setAuthFormError('Le mot de passe est requis.')
      return
    }

    setAuthSubmitting(true)
    try {
      if (authMode === 'login') {
        await login({ email, motDePasse: formPassword })
        setAuthMode(null)
      } else if (authMode === 'register') {
        const nom = formNom.trim()
        const prenom = formPrenom.trim()
        if (!nom || !prenom) {
          setAuthFormError('Le nom et le prénom sont requis.')
          setAuthSubmitting(false)
          return
        }
        await register({ nom, prenom, email, motDePasse: formPassword })
        setAuthMode(null)
      }
    } catch (err) {
      setAuthFormError(err?.message || String(err))
    } finally {
      setAuthSubmitting(false)
    }
  }

  return {
    authMode,
    authIntroMode,
    authIntroStriking,
    formNom,
    formPrenom,
    formEmail,
    formPassword,
    authFormError,
    authFormSuccess,
    authSubmitting,
    authCardRef,
    openAuth,
    closeAuth,
    closeAuthIntro,
    handleAuthIntroStrike,
    handleAuthSubmit,
    setFormNom,
    setFormPrenom,
    setFormEmail,
    setFormPassword,
    setAuthMode,
  }
}
