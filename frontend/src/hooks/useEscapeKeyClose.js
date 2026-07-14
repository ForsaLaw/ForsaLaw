import { useEffect } from 'react'

export function useEscapeKeyClose({
  authIntroMode,
  authMode,
  isNavOpen,
  closeAuthIntro,
  closeAuth,
  closeNav,
}) {
  useEffect(() => {
    const onKeyDown = (e) => {
      if (e.key !== 'Escape') return

      if (authIntroMode) {
        e.preventDefault()
        closeAuthIntro()
        return
      }

      if (authMode) {
        e.preventDefault()
        closeAuth()
        return
      }

      if (isNavOpen) {
        e.preventDefault()
        closeNav()
      }
    }

    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [authMode, authIntroMode, closeAuth, closeAuthIntro, closeNav, isNavOpen])
}
