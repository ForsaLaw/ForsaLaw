import { useCallback, useEffect, useMemo, useState } from 'react'
import * as avocatsApi from '../api/avocats.js'
import * as rdvApi from '../api/rdv.js'

export function useLawyerSpaceData({ token, isAuthenticated, refreshUser, t, navigate, logout }) {
  const [domaines, setDomaines] = useState([])
  const [domainesError, setDomainesError] = useState(null)

  const [profile, setProfile] = useState(null)
  const [noProfile, setNoProfile] = useState(false)
  const [loadError, setLoadError] = useState(null)
  const [loading, setLoading] = useState(true)

  const [createDomain, setCreateDomain] = useState('')
  const [createSpec, setCreateSpec] = useState('')
  const [createYears, setCreateYears] = useState(0)
  const [createVille, setCreateVille] = useState('')
  const [createDesc, setCreateDesc] = useState('')
  const [createCarte, setCreateCarte] = useState('')
  const [createCin, setCreateCin] = useState('')
  const [createBarreau, setCreateBarreau] = useState('')
  const [createBusy, setCreateBusy] = useState(false)
  const [createMsg, setCreateMsg] = useState(null)

  const [editSpec, setEditSpec] = useState('')
  const [editYears, setEditYears] = useState(0)
  const [editVille, setEditVille] = useState('')
  const [editDesc, setEditDesc] = useState('')
  const [editBusy, setEditBusy] = useState(false)
  const [editError, setEditError] = useState(null)
  const [editSaved, setEditSaved] = useState(false)

  const [pwdCurrent, setPwdCurrent] = useState('')
  const [pwdNew, setPwdNew] = useState('')
  const [pwdBusy, setPwdBusy] = useState(false)
  const [pwdMsg, setPwdMsg] = useState(null)

  const [photoBlobUrl, setPhotoBlobUrl] = useState(null)
  const [photoBusy, setPhotoBusy] = useState(false)
  const [photoMsg, setPhotoMsg] = useState(null)

  const [deactivateBusy, setDeactivateBusy] = useState(false)
  const [appointments, setAppointments] = useState([])
  const [appointmentsLoading, setAppointmentsLoading] = useState(false)
  const [proposeStart, setProposeStart] = useState('')
  const [proposeEnd, setProposeEnd] = useState('')
  const [proposeType, setProposeType] = useState('EN_LIGNE')
  const [proposeComment, setProposeComment] = useState('')
  const [agendaSnapshot, setAgendaSnapshot] = useState(null)
  const [agendaBusy, setAgendaBusy] = useState(false)
  const [agendaMsg, setAgendaMsg] = useState(null)
  const [agendaError, setAgendaError] = useState(null)
  const [agendaConfig, setAgendaConfig] = useState({
    zoneId: 'Africa/Tunis',
    dureeCreneauMinutes: 30,
    bufferMinutes: 0,
    agendaActif: true,
  })
  const [newPlage, setNewPlage] = useState({ dayOfWeek: 1, heureDebut: '09:00', heureFin: '17:00' })
  const [newException, setNewException] = useState({
    dateDebut: '',
    dateFin: '',
    libelle: '',
  })

  const jwtRole = useMemo(() => avocatsApi.parseJwtRole(token), [token])
  const canUseAvocatEndpoints = jwtRole === 'avocat'

  const selectedDomainRow = useMemo(
    () => domaines.find((d) => d.code === createDomain),
    [domaines, createDomain],
  )

  const editDomainRow = useMemo(() => {
    if (!profile?.domaine) return null
    return domaines.find((d) => d.code === profile.domaine)
  }, [domaines, profile?.domaine])

  const isApproved = profile?.verificationStatus === 'APPROVED' && profile?.verifie === true
  const isPending = profile && profile.verificationStatus === 'PENDING'
  const isRejected = profile && profile.verificationStatus === 'REJECTED'

  const fmtDateTime = useCallback((v) => {
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
  }, [])

  const reloadProfile = useCallback(async () => {
    if (!token) return
    setLoading(true)
    setLoadError(null)
    try {
      const dto = await avocatsApi.getMyAvocatProfileOrNull(token)
      if (dto == null) {
        setProfile(null)
        setNoProfile(true)
      } else {
        setProfile(dto)
        setNoProfile(false)
        setEditSpec(dto.specialite ?? '')
        setEditYears(dto.anneesExperience ?? 0)
        setEditVille(dto.ville ?? '')
        setEditDesc(dto.description ?? '')
      }
      await refreshUser()
    } catch (e) {
      setLoadError(e?.message || String(e))
    } finally {
      setLoading(false)
    }
  }, [token, refreshUser])

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        const list = await avocatsApi.getDomaines()
        if (!cancelled) {
          setDomaines(list)
          if (list.length) {
            setCreateDomain((prev) => prev || list[0].code)
          }
        }
      } catch (e) {
        if (!cancelled) setDomainesError(e?.message || String(e))
      }
    })()
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    if (!token || !isAuthenticated) return
    reloadProfile()
  }, [token, isAuthenticated, reloadProfile])

  useEffect(() => {
    if (!token || !canUseAvocatEndpoints) {
      setPhotoBlobUrl((prev) => {
        if (prev) URL.revokeObjectURL(prev)
        return null
      })
      return
    }
    let revoked = false
    let url
    ;(async () => {
      try {
        const blob = await avocatsApi.downloadAvocatProfilePhotoBlob(token)
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
  }, [token, canUseAvocatEndpoints, profile?.profilePhotoPublicUrl])

  const reloadAppointments = useCallback(async () => {
    if (!token || !canUseAvocatEndpoints || !isApproved) return
    setAppointmentsLoading(true)
    try {
      const page = await rdvApi.listLawyerAppointments(token, { page: 0, size: 60 })
      setAppointments(page?.content ?? [])
    } catch (e) {
      setLoadError(e?.message || String(e))
    } finally {
      setAppointmentsLoading(false)
    }
  }, [token, canUseAvocatEndpoints, isApproved])

  const reloadAgenda = useCallback(async () => {
    if (!token || !canUseAvocatEndpoints || !isApproved) return
    try {
      const a = await rdvApi.getAgenda(token)
      setAgendaSnapshot(a)
      setAgendaConfig({
        zoneId: a?.zoneId || 'Africa/Tunis',
        dureeCreneauMinutes: Number(a?.dureeCreneauMinutes) || 30,
        bufferMinutes: Number(a?.bufferMinutes) || 0,
        agendaActif: a?.agendaActif !== false,
      })
    } catch {
      setAgendaSnapshot(null)
    }
  }, [token, canUseAvocatEndpoints, isApproved])

  useEffect(() => {
    if (!token || !canUseAvocatEndpoints || !isApproved) return
    reloadAppointments()
  }, [token, canUseAvocatEndpoints, isApproved, reloadAppointments])

  useEffect(() => {
    if (!token || !canUseAvocatEndpoints || !isApproved) return
    reloadAgenda()
  }, [token, canUseAvocatEndpoints, isApproved, reloadAgenda])

  const handleSaveAgendaConfig = useCallback(async (e) => {
    e.preventDefault()
    if (!token) return
    setAgendaBusy(true)
    setAgendaMsg(null)
    setAgendaError(null)
    try {
      await rdvApi.updateAgendaConfig(token, {
        zoneId: agendaConfig.zoneId.trim() || 'Africa/Tunis',
        dureeCreneauMinutes: Number(agendaConfig.dureeCreneauMinutes) || 30,
        bufferMinutes: Number(agendaConfig.bufferMinutes) || 0,
        agendaActif: !!agendaConfig.agendaActif,
      })
      await reloadAgenda()
      setAgendaMsg('Configuration agenda enregistree.')
    } catch (e2) {
      setAgendaError(e2?.message || String(e2))
    } finally {
      setAgendaBusy(false)
    }
  }, [token, agendaConfig, reloadAgenda])

  const handleAddPlage = useCallback(async (e) => {
    e.preventDefault()
    if (!token) return
    setAgendaBusy(true)
    setAgendaMsg(null)
    setAgendaError(null)
    try {
      await rdvApi.addAgendaPlage(token, {
        dayOfWeek: Number(newPlage.dayOfWeek),
        heureDebut: newPlage.heureDebut,
        heureFin: newPlage.heureFin,
      })
      await reloadAgenda()
      setAgendaMsg('Plage ajoutee.')
    } catch (e2) {
      setAgendaError(e2?.message || String(e2))
    } finally {
      setAgendaBusy(false)
    }
  }, [token, newPlage, reloadAgenda])

  const handleDeletePlage = useCallback(async (idPlage) => {
    if (!token) return
    setAgendaBusy(true)
    setAgendaMsg(null)
    setAgendaError(null)
    try {
      await rdvApi.deleteAgendaPlage(token, idPlage)
      await reloadAgenda()
      setAgendaMsg('Plage supprimee.')
    } catch (e2) {
      setAgendaError(e2?.message || String(e2))
    } finally {
      setAgendaBusy(false)
    }
  }, [token, reloadAgenda])

  const handleAddException = useCallback(async (e) => {
    e.preventDefault()
    if (!token) return
    setAgendaBusy(true)
    setAgendaMsg(null)
    setAgendaError(null)
    try {
      await rdvApi.addAgendaException(token, {
        dateDebut: newException.dateDebut,
        dateFin: newException.dateFin || newException.dateDebut,
        libelle: newException.libelle.trim() || undefined,
      })
      setNewException({ dateDebut: '', dateFin: '', libelle: '' })
      await reloadAgenda()
      setAgendaMsg('Exception ajoutee.')
    } catch (e2) {
      setAgendaError(e2?.message || String(e2))
    } finally {
      setAgendaBusy(false)
    }
  }, [token, newException, reloadAgenda])

  const handleDeleteException = useCallback(async (idException) => {
    if (!token) return
    setAgendaBusy(true)
    setAgendaMsg(null)
    setAgendaError(null)
    try {
      await rdvApi.deleteAgendaException(token, idException)
      await reloadAgenda()
      setAgendaMsg('Exception supprimee.')
    } catch (e2) {
      setAgendaError(e2?.message || String(e2))
    } finally {
      setAgendaBusy(false)
    }
  }, [token, reloadAgenda])

  const handleCreate = useCallback(async (e) => {
    e.preventDefault()
    if (!token) return
    setCreateBusy(true)
    setCreateMsg(null)
    try {
      const body = {
        domaine: createDomain,
        specialite: createSpec,
        anneesExperience: Number(createYears) || 0,
        ville: createVille.trim(),
        description: createDesc.trim() || undefined,
        numeroCarteProfessionnelle: createCarte.trim(),
        cin: createCin.trim(),
        barreau: createBarreau.trim(),
      }
      const dto = await avocatsApi.createMyAvocatProfile(token, body)
      setProfile(dto)
      setNoProfile(false)
      setEditSpec(dto.specialite ?? '')
      setEditYears(dto.anneesExperience ?? 0)
      setEditVille(dto.ville ?? '')
      setEditDesc(dto.description ?? '')
      setCreateMsg(null)
      await refreshUser()
    } catch (err) {
      setCreateMsg(err?.message || String(err))
    } finally {
      setCreateBusy(false)
    }
  }, [token, createDomain, createSpec, createYears, createVille, createDesc, createCarte, createCin, createBarreau, refreshUser])

  const handleEdit = useCallback(async (e) => {
    e.preventDefault()
    if (!token || !profile) return
    setEditBusy(true)
    setEditError(null)
    setEditSaved(false)
    try {
      const body = {
        specialite: editSpec || undefined,
        anneesExperience: editYears !== profile.anneesExperience ? Number(editYears) : undefined,
        ville: editVille.trim(),
        description: editDesc.trim(),
      }
      const dto = await avocatsApi.updateMyAvocatProfile(token, body)
      setProfile(dto)
      setEditSaved(true)
    } catch (err) {
      setEditError(err?.message || String(err))
    } finally {
      setEditBusy(false)
    }
  }, [token, profile, editSpec, editYears, editVille, editDesc])

  const handlePassword = useCallback(async (e) => {
    e.preventDefault()
    if (!token) return
    setPwdBusy(true)
    setPwdMsg(null)
    try {
      await avocatsApi.changeAvocatPassword(token, {
        motDePasseActuel: pwdCurrent,
        nouveauMotDePasse: pwdNew,
      })
      setPwdCurrent('')
      setPwdNew('')
      setPwdMsg(t('lawyer_space_password_saved'))
    } catch (err) {
      setPwdMsg(err?.message || String(err))
    } finally {
      setPwdBusy(false)
    }
  }, [token, pwdCurrent, pwdNew, t])

  const handlePhoto = useCallback(async (e) => {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file || !token) return
    setPhotoBusy(true)
    setPhotoMsg(null)
    try {
      const dto = await avocatsApi.uploadAvocatProfilePhoto(token, file)
      setProfile(dto)
      await refreshUser()
      setPhotoMsg(t('lawyer_space_photo_saved'))
      const blob = await avocatsApi.downloadAvocatProfilePhotoBlob(token)
      setPhotoBlobUrl((prev) => {
        if (prev) URL.revokeObjectURL(prev)
        return blob ? URL.createObjectURL(blob) : null
      })
    } catch (err) {
      setPhotoMsg(err?.message || String(err))
    } finally {
      setPhotoBusy(false)
    }
  }, [token, refreshUser, t])

  const handleDeactivate = useCallback(async () => {
    if (!token) return
    if (!window.confirm(t('lawyer_space_deactivate_confirm'))) return
    setDeactivateBusy(true)
    try {
      await avocatsApi.deactivateMyAvocatProfile(token)
      await refreshUser()
      navigate('/', { replace: true })
    } catch (err) {
      window.alert(err?.message || String(err))
    } finally {
      setDeactivateBusy(false)
    }
  }, [token, t, refreshUser, navigate])

  const handleProposeAppointmentSlot = useCallback(async (idRendezVous) => {
    if (!token) return
    try {
      await rdvApi.lawyerProposeSlot(token, idRendezVous, {
        dateHeureDebut: proposeStart,
        dateHeureFin: proposeEnd,
        typeRendezVous: proposeType,
        commentaireAvocat: proposeComment || undefined,
      })
      setProposeComment('')
      await reloadAppointments()
    } catch (e) {
      window.alert(e?.message || String(e))
    }
  }, [token, proposeStart, proposeEnd, proposeType, proposeComment, reloadAppointments])

  const handleCancelAppointment = useCallback(async (idRendezVous) => {
    if (!token) return
    const raison = window.prompt('Raison annulation ?') || ''
    try {
      await rdvApi.lawyerCancelAppointment(token, idRendezVous, raison)
      await reloadAppointments()
    } catch (e) {
      window.alert(e?.message || String(e))
    }
  }, [token, reloadAppointments])

  const handleOpenOnlineRoom = useCallback(async (idRendezVous) => {
    if (!token) return
    try {
      const x = await rdvApi.lawyerMeetingAccess(token, idRendezVous)
      const url = x.joinPath?.startsWith('http') ? x.joinPath : `${window.location.origin}${x.joinPath}`
      window.open(url, '_blank', 'noopener,noreferrer')
    } catch (e) {
      window.alert(e?.message || String(e))
    }
  }, [token])

  const relogin = useCallback(() => {
    logout()
    navigate('/', { replace: true })
  }, [logout, navigate])

  return {
    domaines,
    domainesError,
    profile,
    noProfile,
    loadError,
    loading,
    createDomain,
    setCreateDomain,
    createSpec,
    setCreateSpec,
    createYears,
    setCreateYears,
    createVille,
    setCreateVille,
    createDesc,
    setCreateDesc,
    createCarte,
    setCreateCarte,
    createCin,
    setCreateCin,
    createBarreau,
    setCreateBarreau,
    createBusy,
    createMsg,
    editSpec,
    setEditSpec,
    editYears,
    setEditYears,
    editVille,
    setEditVille,
    editDesc,
    setEditDesc,
    editBusy,
    editError,
    editSaved,
    pwdCurrent,
    setPwdCurrent,
    pwdNew,
    setPwdNew,
    pwdBusy,
    pwdMsg,
    photoBlobUrl,
    photoBusy,
    photoMsg,
    deactivateBusy,
    appointments,
    appointmentsLoading,
    proposeStart,
    setProposeStart,
    proposeEnd,
    setProposeEnd,
    proposeType,
    setProposeType,
    proposeComment,
    setProposeComment,
    agendaSnapshot,
    agendaBusy,
    agendaMsg,
    agendaError,
    agendaConfig,
    setAgendaConfig,
    newPlage,
    setNewPlage,
    newException,
    setNewException,
    canUseAvocatEndpoints,
    selectedDomainRow,
    editDomainRow,
    isApproved,
    isPending,
    isRejected,
    fmtDateTime,
    reloadProfile,
    handleSaveAgendaConfig,
    handleAddPlage,
    handleDeletePlage,
    handleAddException,
    handleDeleteException,
    handleCreate,
    handleEdit,
    handlePassword,
    handlePhoto,
    handleDeactivate,
    handleProposeAppointmentSlot,
    handleCancelAppointment,
    handleOpenOnlineRoom,
    relogin,
  }
}
