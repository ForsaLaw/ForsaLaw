import { useCallback, useEffect, useState } from 'react'
import * as adminApi from '../api/admin.js'

export function useAdminSpaceData(token, activeModule, query) {
  const [busy, setBusy] = useState({})
  const [err, setErr] = useState(null)

  const [avocats, setAvocats] = useState([])
  const [avocatsPending, setAvocatsPending] = useState([])
  const [avocatsLoading, setAvocatsLoading] = useState(false)

  const [users, setUsers] = useState([])
  const [usersTotal, setUsersTotal] = useState(0)
  const [usersLoading, setUsersLoading] = useState(false)

  const [reclamations, setReclamations] = useState([])
  const [reclamationsTotal, setReclamationsTotal] = useState(0)
  const [reclamationsLoading, setReclamationsLoading] = useState(false)

  const [affaires, setAffaires] = useState([])
  const [affairesTotal, setAffairesTotal] = useState(0)
  const [affairesLoading, setAffairesLoading] = useState(false)

  const [rdvs, setRdvs] = useState([])
  const [rdvsTotal, setRdvsTotal] = useState(0)
  const [rdvsLoading, setRdvsLoading] = useState(false)

  const [docs, setDocs] = useState([])
  const [docsTotal, setDocsTotal] = useState(0)
  const [docsLoading, setDocsLoading] = useState(false)

  const [conversations, setConversations] = useState([])
  const [conversationsTotal, setConversationsTotal] = useState(0)
  const [conversationsLoading, setConversationsLoading] = useState(false)

  const [auditLogs, setAuditLogs] = useState([])
  const [auditTotal, setAuditTotal] = useState(0)
  const [auditLoading, setAuditLoading] = useState(false)

  const [waStatus, setWaStatus] = useState(null)
  const [waQr, setWaQr] = useState(null)
  const [waLoading, setWaLoading] = useState(false)
  const [testPhone, setTestPhone] = useState('')
  const [testMsg, setTestMsg] = useState('')
  const [selectedAuditLog, setSelectedAuditLog] = useState(null)

  const loadAvocats = useCallback(async () => {
    if (!token) return
    setAvocatsLoading(true)
    setErr(null)
    try {
      const page = await adminApi.listAvocatsAdmin(token, { size: 50 })
      const list = page?.content ?? []
      setAvocats(list)
      setAvocatsPending(list.filter((a) => !a.verifie && a.actif))
    } catch (e) {
      setErr(e?.message)
    } finally {
      setAvocatsLoading(false)
    }
  }, [token])

  const loadUsers = useCallback(async (search = '') => {
    if (!token) return
    setUsersLoading(true)
    try {
      const page = await adminApi.listUsers(token, { size: 50, search: search || undefined })
      setUsers(page?.content ?? [])
      setUsersTotal(page?.totalElements ?? 0)
    } catch (e) {
      setErr(e?.message)
    } finally {
      setUsersLoading(false)
    }
  }, [token])

  const loadReclamations = useCallback(async () => {
    if (!token) return
    setReclamationsLoading(true)
    try {
      const page = await adminApi.listAllReclamations(token, { size: 50 })
      setReclamations(page?.content ?? [])
      setReclamationsTotal(page?.totalElements ?? 0)
    } catch (e) {
      setErr(e?.message)
    } finally {
      setReclamationsLoading(false)
    }
  }, [token])

  const loadAffaires = useCallback(async () => {
    if (!token) return
    setAffairesLoading(true)
    try {
      const page = await adminApi.listAllAffaires(token, { size: 50 })
      setAffaires(page?.content ?? [])
      setAffairesTotal(page?.totalElements ?? 0)
    } catch (e) {
      setErr(e?.message)
    } finally {
      setAffairesLoading(false)
    }
  }, [token])

  const loadRdvs = useCallback(async () => {
    if (!token) return
    setRdvsLoading(true)
    try {
      const page = await adminApi.listAllRendezVous(token, { size: 50 })
      setRdvs(page?.content ?? [])
      setRdvsTotal(page?.totalElements ?? 0)
    } catch (e) {
      setErr(e?.message)
    } finally {
      setRdvsLoading(false)
    }
  }, [token])

  const loadDocs = useCallback(async () => {
    if (!token) return
    setDocsLoading(true)
    try {
      const page = await adminApi.listAllSystemDocuments(token, { size: 50 })
      setDocs(page?.content ?? [])
      setDocsTotal(page?.totalElements ?? 0)
    } catch (e) {
      setErr(e?.message)
    } finally {
      setDocsLoading(false)
    }
  }, [token])

  const loadConversations = useCallback(async () => {
    if (!token) return
    setConversationsLoading(true)
    try {
      const page = await adminApi.listAllConversations(token, { size: 50 })
      setConversations(page?.content ?? [])
      setConversationsTotal(page?.totalElements ?? 0)
    } catch (e) {
      setErr(e?.message)
    } finally {
      setConversationsLoading(false)
    }
  }, [token])

  const loadAudit = useCallback(async () => {
    if (!token) return
    setAuditLoading(true)
    try {
      const page = await adminApi.listAuditLogs(token, { size: 50 })
      setAuditLogs(page?.content ?? [])
      setAuditTotal(page?.totalElements ?? 0)
    } catch (e) {
      setErr(e?.message)
    } finally {
      setAuditLoading(false)
    }
  }, [token])

  const loadWhatsApp = useCallback(async () => {
    if (!token) return
    setWaLoading(true)
    try {
      const stat = await adminApi.getWhatsAppStatus(token)
      setWaStatus(stat)
    } catch (e) {
      setErr(e?.message)
    } finally {
      setWaLoading(false)
    }
  }, [token])

  useEffect(() => {
    if (!token) return
    if (['overview', 'avocats'].includes(activeModule)) loadAvocats()
    if (['overview', 'users'].includes(activeModule)) loadUsers()
    if (['overview', 'reclamations'].includes(activeModule)) loadReclamations()
    if (['overview', 'affaires'].includes(activeModule)) loadAffaires()
    if (['overview', 'audit'].includes(activeModule)) loadAudit()
    if (activeModule === 'rendezvous') loadRdvs()
    if (activeModule === 'documents') loadDocs()
    if (activeModule === 'messenger') loadConversations()
    if (activeModule === 'whatsapp') loadWhatsApp()
  }, [
    activeModule,
    token,
    loadAffaires,
    loadAudit,
    loadAvocats,
    loadConversations,
    loadDocs,
    loadRdvs,
    loadReclamations,
    loadUsers,
    loadWhatsApp,
  ])

  const handleVerifyAvocat = useCallback(async (avocatId, status) => {
    setBusy((b) => ({ ...b, [avocatId]: true }))
    setErr(null)
    try {
      await adminApi.updateAvocatVerification(token, avocatId, { verificationStatus: status })
      await loadAvocats()
    } catch (e) {
      setErr(e?.message)
    } finally {
      setBusy((b) => ({ ...b, [avocatId]: false }))
    }
  }, [token, loadAvocats])

  const handleToggleUser = useCallback(async (userId, isActive) => {
    setBusy((b) => ({ ...b, [userId]: true }))
    setErr(null)
    try {
      if (isActive) {
        await adminApi.deactivateUser(token, userId)
      } else {
        await adminApi.reactivateUser(token, userId)
      }
      await loadUsers(query)
    } catch (e) {
      setErr(e?.message)
    } finally {
      setBusy((b) => ({ ...b, [userId]: false }))
    }
  }, [token, loadUsers, query])

  const handleUpdateReclamation = useCallback(async (id, statut) => {
    setBusy((b) => ({ ...b, [id]: true }))
    setErr(null)
    try {
      await adminApi.updateReclamationStatus(token, id, statut)
      await loadReclamations()
    } catch (e) {
      setErr(e?.message)
    } finally {
      setBusy((b) => ({ ...b, [id]: false }))
    }
  }, [token, loadReclamations])

  const handleTriggerReminders = useCallback(async () => {
    if (!window.confirm('Forcer l\'envoi global des rappels WhatsApp ?')) return
    setBusy((b) => ({ ...b, trigger: true }))
    setErr(null)
    try {
      await adminApi.triggerRdvReminders(token)
      alert('Rappels déclenchés avec succès.')
    } catch (e) {
      setErr(e?.message)
    } finally {
      setBusy((b) => ({ ...b, trigger: false }))
    }
  }, [token])

  const handleDeleteDocument = useCallback(async (id) => {
    if (!window.confirm('La suppression du coffre-fort va détruire physiquement le fichier. Continuer ?')) return
    setBusy((b) => ({ ...b, [id]: true }))
    setErr(null)
    try {
      await adminApi.deleteSystemDocument(token, id)
      await loadDocs()
    } catch (e) {
      setErr(e?.message)
    } finally {
      setBusy((b) => ({ ...b, [id]: false }))
    }
  }, [token, loadDocs])

  const handleCloseConversation = useCallback(async (id) => {
    if (!window.confirm('Clôturer cette ligne de messagerie d\'urgence ?')) return
    setBusy((b) => ({ ...b, [id]: true }))
    setErr(null)
    try {
      await adminApi.closeConversationGlobal(token, id)
      await loadConversations()
    } catch (e) {
      setErr(e?.message)
    } finally {
      setBusy((b) => ({ ...b, [id]: false }))
    }
  }, [token, loadConversations])

  const handleGenerateWaQr = useCallback(async () => {
    setWaLoading(true)
    setErr(null)
    try {
      const res = await adminApi.getWhatsAppQr(token)
      setWaQr(res.qrCode)
    } catch (e) {
      setErr(e?.message)
    } finally {
      setWaLoading(false)
    }
  }, [token])

  const handleSendWaTest = useCallback(async (e) => {
    e.preventDefault()
    if (!testPhone) return
    setBusy((b) => ({ ...b, testWa: true }))
    setErr(null)
    try {
      await adminApi.sendWhatsAppTest(token, testPhone, testMsg)
      alert('Test expédié.')
    } catch (err) {
      setErr(err?.message)
    } finally {
      setBusy((b) => ({ ...b, testWa: false }))
    }
  }, [token, testPhone, testMsg])

  return {
    busy,
    err,
    avocats,
    avocatsPending,
    avocatsLoading,
    users,
    usersTotal,
    usersLoading,
    reclamations,
    reclamationsTotal,
    reclamationsLoading,
    affaires,
    affairesTotal,
    affairesLoading,
    rdvs,
    rdvsTotal,
    rdvsLoading,
    docs,
    docsTotal,
    docsLoading,
    conversations,
    conversationsTotal,
    conversationsLoading,
    auditLogs,
    auditTotal,
    auditLoading,
    waStatus,
    waQr,
    waLoading,
    testPhone,
    setTestPhone,
    testMsg,
    setTestMsg,
    selectedAuditLog,
    setSelectedAuditLog,
    loadUsers,
    handleVerifyAvocat,
    handleToggleUser,
    handleUpdateReclamation,
    handleTriggerReminders,
    handleDeleteDocument,
    handleCloseConversation,
    handleGenerateWaQr,
    handleSendWaTest,
  }
}
