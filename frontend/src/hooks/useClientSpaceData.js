import { useCallback, useEffect, useMemo, useState } from 'react'
import * as usersApi from '../api/users.js'
import * as rdvApi from '../api/rdv.js'
import * as reclamationApi from '../api/reclamation.js'

export function formatApiDate(v) {
  if (v == null) return '—'
  if (typeof v === 'string') return v.includes('T') ? v.split('T')[0] : v
  if (Array.isArray(v) && v.length >= 3) {
    const [y, m, d] = v
    return `${String(y)}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`
  }
  return String(v)
}

export function fmtDateTime(v) {
  if (!v) return '—'
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return String(v)
  return d.toLocaleString('fr-FR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function useClientSpaceData({
  token,
  user,
  isAuthenticated,
  activeTab,
  t,
  navigate,
  logout,
  refreshUser,
}) {
  const [me, setMe] = useState(null)
  const [loadError, setLoadError] = useState(null)
  const [loadingMe, setLoadingMe] = useState(true)

  const [cases, setCases] = useState([])
  const [casesLoading, setCasesLoading] = useState(false)

  const [editNom, setEditNom] = useState('')
  const [editPrenom, setEditPrenom] = useState('')
  const [editEmail, setEditEmail] = useState('')
  const [profileEditing, setProfileEditing] = useState(false)
  const [profileSaving, setProfileSaving] = useState(false)
  const [profileMsg, setProfileMsg] = useState(null)

  const [pwdCurrent, setPwdCurrent] = useState('')
  const [pwdNew, setPwdNew] = useState('')
  const [pwdSaving, setPwdSaving] = useState(false)
  const [pwdMsg, setPwdMsg] = useState(null)

  const [notif, setNotif] = useState(null)
  const [notifLoading, setNotifLoading] = useState(false)
  const [notifSaving, setNotifSaving] = useState(false)
  const [notifMsg, setNotifMsg] = useState(null)

  const [photoBlobUrl, setPhotoBlobUrl] = useState(null)
  const [photoUploading, setPhotoUploading] = useState(false)
  const [photoMsg, setPhotoMsg] = useState(null)

  const [deleteBusy, setDeleteBusy] = useState(false)
  const [appointments, setAppointments] = useState([])
  const [appointmentsLoading, setAppointmentsLoading] = useState(false)

  const reloadMe = useCallback(async () => {
    if (!token) return
    setLoadingMe(true)
    setLoadError(null)
    try {
      const dto = await usersApi.getMe(token)
      setMe(dto)
      setEditNom(dto.nom ?? '')
      setEditPrenom(dto.prenom ?? '')
      setEditEmail(dto.email ?? '')
    } catch (e) {
      setLoadError(e?.message || String(e))
    } finally {
      setLoadingMe(false)
    }
  }, [token])

  useEffect(() => {
    if (!token || !isAuthenticated) return
    reloadMe()
  }, [token, isAuthenticated, reloadMe])

  useEffect(() => {
    if (!token || activeTab !== 'profile') return
    let cancelled = false
    ;(async () => {
      setNotifLoading(true)
      setNotifMsg(null)
      try {
        const p = await usersApi.getNotificationPreferences(token)
        if (!cancelled) setNotif(p)
      } catch (e) {
        if (!cancelled) setNotifMsg(e?.message || String(e))
      } finally {
        if (!cancelled) setNotifLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [token, activeTab])

  useEffect(() => {
    if (!token || activeTab !== 'appointments') return
    let cancelled = false
    ;(async () => {
      setAppointmentsLoading(true)
      try {
        const page = await rdvApi.listClientAppointments(token, { page: 0, size: 50 })
        if (!cancelled) setAppointments(page?.content ?? [])
      } catch (e) {
        if (user?.roleUser === 'admin') return
        if (!cancelled) setLoadError(e?.message || String(e))
      } finally {
        if (!cancelled) setAppointmentsLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [token, activeTab, user?.roleUser])

  const reloadAppointments = useCallback(async () => {
    if (!token) return
    const page = await rdvApi.listClientAppointments(token, { page: 0, size: 50 })
    setAppointments(page?.content ?? [])
  }, [token])

  useEffect(() => {
    if (!token || activeTab !== 'cases') return
    let cancelled = false
    ;(async () => {
      setCasesLoading(true)
      try {
        const page = await reclamationApi.listMyReclamations(token, { page: 0, size: 50 })
        if (!cancelled) setCases(page?.content ?? [])
      } catch (e) {
        if (user?.roleUser === 'admin') return
        if (!cancelled) setLoadError(e?.message || String(e))
      } finally {
        if (!cancelled) setCasesLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [token, activeTab, user?.roleUser])

  useEffect(() => {
    if (!token) {
      setPhotoBlobUrl(null)
      return
    }
    let revoked = false
    let url
    ;(async () => {
      try {
        const blob = await usersApi.downloadProfilePhotoBlob(token)
        if (blob && !revoked) {
          url = URL.createObjectURL(blob)
          setPhotoBlobUrl(url)
        } else {
          setPhotoBlobUrl(null)
        }
      } catch {
        setPhotoBlobUrl(null)
      }
    })()
    return () => {
      revoked = true
      if (url) URL.revokeObjectURL(url)
    }
  }, [token, me?.profilePhotoUrl])

  const initials = useMemo(() => {
    const p = me?.prenom ?? user?.prenom
    const n = me?.nom ?? user?.nom
    return ((p?.[0] ?? '') + (n?.[0] ?? '')).toUpperCase() || '?'
  }, [me, user])

  const displayName = useMemo(() => {
    const p = me?.prenom ?? user?.prenom
    const n = me?.nom ?? user?.nom
    return [p, n].filter(Boolean).join(' ') || user?.email || '—'
  }, [me, user])

  const handleSaveProfile = useCallback(async (e) => {
    e.preventDefault()
    if (!token) return
    setProfileSaving(true)
    setProfileMsg(null)
    try {
      const updated = await usersApi.updateMe(token, {
        nom: editNom.trim(),
        prenom: editPrenom.trim(),
        email: editEmail.trim(),
      })
      setMe(updated)
      await refreshUser()
      setProfileEditing(false)
      setProfileMsg(t('client_profile_saved'))
    } catch (err) {
      setProfileMsg(err?.message || String(err))
    } finally {
      setProfileSaving(false)
    }
  }, [token, editNom, editPrenom, editEmail, refreshUser, t])

  const handleChangePassword = useCallback(async (e) => {
    e.preventDefault()
    if (!token) return
    setPwdSaving(true)
    setPwdMsg(null)
    try {
      await usersApi.changePassword(token, {
        motDePasseActuel: pwdCurrent,
        nouveauMotDePasse: pwdNew,
      })
      setPwdCurrent('')
      setPwdNew('')
      setPwdMsg(t('client_password_saved'))
    } catch (err) {
      setPwdMsg(err?.message || String(err))
    } finally {
      setPwdSaving(false)
    }
  }, [token, pwdCurrent, pwdNew, t])

  const handleSaveNotif = useCallback(async () => {
    if (!token || !notif) return
    setNotifSaving(true)
    setNotifMsg(null)
    try {
      const updated = await usersApi.updateNotificationPreferences(token, notif)
      setNotif(updated)
      setNotifMsg(t('client_notif_saved'))
    } catch (err) {
      setNotifMsg(err?.message || String(err))
    } finally {
      setNotifSaving(false)
    }
  }, [token, notif, t])

  const handlePhotoChange = useCallback(async (e) => {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file || !token) return
    setPhotoUploading(true)
    setPhotoMsg(null)
    try {
      const updated = await usersApi.uploadProfilePhoto(token, file)
      setMe(updated)
      await refreshUser()
      setPhotoMsg(t('client_photo_saved'))
      const blob = await usersApi.downloadProfilePhotoBlob(token)
      setPhotoBlobUrl((prev) => {
        if (prev) URL.revokeObjectURL(prev)
        return blob ? URL.createObjectURL(blob) : null
      })
    } catch (err) {
      setPhotoMsg(err?.message || String(err))
    } finally {
      setPhotoUploading(false)
    }
  }, [token, refreshUser, t])

  const handleDeleteAccount = useCallback(async () => {
    if (!token) return
    if (!window.confirm(t('client_delete_confirm'))) return
    setDeleteBusy(true)
    try {
      await usersApi.deleteMe(token)
      logout()
      navigate('/', { replace: true })
    } catch (err) {
      window.alert(err?.message || String(err))
    } finally {
      setDeleteBusy(false)
    }
  }, [token, t, logout, navigate])

  const toggleNotif = useCallback((key) => {
    setNotif((prev) => (prev ? { ...prev, [key]: !prev[key] } : prev))
  }, [])

  return {
    me,
    loadError,
    loadingMe,
    cases,
    casesLoading,
    editNom,
    setEditNom,
    editPrenom,
    setEditPrenom,
    editEmail,
    setEditEmail,
    profileEditing,
    setProfileEditing,
    profileSaving,
    profileMsg,
    setProfileMsg,
    pwdCurrent,
    setPwdCurrent,
    pwdNew,
    setPwdNew,
    pwdSaving,
    pwdMsg,
    notif,
    notifLoading,
    notifSaving,
    notifMsg,
    photoBlobUrl,
    photoUploading,
    photoMsg,
    deleteBusy,
    appointments,
    appointmentsLoading,
    initials,
    displayName,
    reloadAppointments,
    handleSaveProfile,
    handleChangePassword,
    handleSaveNotif,
    handlePhotoChange,
    handleDeleteAccount,
    toggleNotif,
  }
}
