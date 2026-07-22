import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { jwtDecode } from 'jwt-decode'
import * as authApi from '../api/auth.js'
import { fetchCurrentUser } from '../api/client.js'

const STORAGE_KEY = 'forsalaw.auth'

/** ms restant avant expiration du JWT ; <= 0 (ou token invalide) => expiré. */
function millisUntilExpiry(token) {
  if (!token) return -1
  try {
    const { exp } = jwtDecode(token)
    if (typeof exp !== 'number') return -1
    return exp * 1000 - Date.now()
  } catch {
    return -1
  }
}

function isTokenValid(token) {
  return millisUntilExpiry(token) > 0
}

function loadStored() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return { token: null, user: null }
    const parsed = JSON.parse(raw)
    if (parsed && typeof parsed.token === 'string' && parsed.user && typeof parsed.user.email === 'string') {
      // Rejeter d'emblee un JWT expire : purge le stockage et demarre deconnecte.
      if (!isTokenValid(parsed.token)) {
        localStorage.removeItem(STORAGE_KEY)
        return { token: null, user: null }
      }
      return { token: parsed.token, user: parsed.user }
    }
    return { token: null, user: null }
  } catch {
    return { token: null, user: null }
  }
}

function mapAuthResponse(data) {
  return {
    id: data.id,
    email: data.email,
    nom: data.nom,
    prenom: data.prenom,
    roleUser: data.roleUser,
  }
}

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
  const [{ token, user }, setSession] = useState(loadStored)

  const persist = useCallback((next) => {
    setSession(next)
    if (next.token && next.user) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
    } else {
      localStorage.removeItem(STORAGE_KEY)
    }
  }, [])

  const setFromAuthResponse = useCallback(
    (data) => {
      persist({ token: data.token, user: mapAuthResponse(data) })
    },
    [persist],
  )

  const login = useCallback(
    async (body) => {
      const data = await authApi.login(body)
      setFromAuthResponse(data)
      return data
    },
    [setFromAuthResponse],
  )

  const register = useCallback(
    async (body) => {
      const data = await authApi.register(body)
      setFromAuthResponse(data)
      return data
    },
    [setFromAuthResponse],
  )

  const logout = useCallback(() => {
    persist({ token: null, user: null })
  }, [persist])

  // Deconnexion automatique a l'expiration exacte du JWT (sans attendre le prochain appel API).
  useEffect(() => {
    if (!token) return undefined
    const remaining = millisUntilExpiry(token)
    if (remaining <= 0) {
      logout()
      return undefined
    }
    // setTimeout est borne a ~24,8 jours (int 32 bits) ; nos JWT expirent bien avant.
    const timerId = setTimeout(logout, remaining)
    return () => clearTimeout(timerId)
  }, [token, logout])

  const completeOAuthLogin = useCallback(
    async (oauthToken) => {
      const me = await fetchCurrentUser(oauthToken)
      persist({ token: oauthToken, user: mapUserDto(me) })
    },
    [persist],
  )

  const refreshUser = useCallback(async () => {
    if (!token) return null
    const me = await fetchCurrentUser(token)
    const nextUser = mapUserDto(me)
    persist({ token, user: nextUser })
    return nextUser
  }, [token, persist])

  const value = useMemo(
    () => ({
      token,
      user,
      isAuthenticated: Boolean(token && user),
      login,
      register,
      logout,
      completeOAuthLogin,
      refreshUser,
    }),
    [token, user, login, register, logout, completeOAuthLogin, refreshUser],
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
