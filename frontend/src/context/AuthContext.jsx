import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import * as authApi from '../api/auth.js'
import { fetchCurrentUser, initCsrfToken } from '../api/client.js'

/**
 * Auth state.
 *
 * The JWT now lives in an httpOnly cookie: JavaScript cannot read it, so there is nothing to
 * persist in localStorage (which removes the XSS token-theft risk) and no `exp` to decode.
 * The session is instead resolved from the server on load via GET /api/users/me — a 401 simply
 * means "not logged in". Session expiry is enforced by the cookie's Max-Age and by the backend
 * rejecting expired tokens.
 *
 * `token` is still exposed (always null) because many call sites pass it to the api helpers;
 * a null token just means no Authorization header, and the cookie carries the credentials.
 */

function mapUserDto(dto) {
  return {
    id: dto.id,
    email: dto.email,
    nom: dto.nom,
    prenom: dto.prenom,
    roleUser: dto.roleUser,
    actif: dto.actif,
    profilePhotoUrl: dto.profilePhotoUrl,
  }
}

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  // true until the initial "am I logged in?" probe finishes, so guarded routes don't flash.
  const [bootstrapping, setBootstrapping] = useState(true)

  const loadCurrentUser = useCallback(async () => {
    try {
      const me = await fetchCurrentUser()
      const next = mapUserDto(me)
      setUser(next)
      return next
    } catch {
      // 401 / network error => treat as logged out.
      setUser(null)
      return null
    }
  }, [])

  // On mount: prime the CSRF cookie, then resolve the session from the auth cookie.
  useEffect(() => {
    let cancelled = false
    ;(async () => {
      await initCsrfToken()
      await loadCurrentUser()
      if (!cancelled) setBootstrapping(false)
    })()
    return () => {
      cancelled = true
    }
  }, [loadCurrentUser])

  const login = useCallback(
    async (body) => {
      // The backend sets the httpOnly auth cookie on this response.
      const data = await authApi.login(body)
      await loadCurrentUser()
      return data
    },
    [loadCurrentUser],
  )

  const register = useCallback(
    async (body) => {
      const data = await authApi.register(body)
      await loadCurrentUser()
      return data
    },
    [loadCurrentUser],
  )

  const logout = useCallback(async () => {
    try {
      // Only the server can clear an httpOnly cookie.
      await authApi.logout()
    } catch {
      /* clear local state regardless */
    }
    setUser(null)
  }, [])

  /** Google OAuth2 callback: the cookie is already set by the backend redirect. */
  const completeOAuthLogin = useCallback(async () => {
    const next = await loadCurrentUser()
    if (!next) {
      throw new Error("Echec de la connexion Google : session introuvable.")
    }
    return next
  }, [loadCurrentUser])

  const refreshUser = useCallback(() => loadCurrentUser(), [loadCurrentUser])

  const value = useMemo(
    () => ({
      token: null, // kept for compatibility: auth travels in the httpOnly cookie
      user,
      isAuthenticated: Boolean(user),
      bootstrapping,
      login,
      register,
      logout,
      completeOAuthLogin,
      refreshUser,
    }),
    [user, bootstrapping, login, register, logout, completeOAuthLogin, refreshUser],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error('useAuth must be used within AuthProvider')
  }
  return ctx
}
