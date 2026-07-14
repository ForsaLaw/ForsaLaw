import { useCallback, useEffect, useState } from 'react'
import * as documentsApi from '../api/documents.js'

export function fmtDate(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('fr-FR', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function useDossierPageData({ token, isAuthenticated }) {
  const [documents, setDocuments] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(null)
  const [activeDoc, setActiveDoc] = useState(null)
  const [history, setHistory] = useState([])
  const [historyLoading, setHistoryLoading] = useState(false)
  const [verificationResult, setVerificationResult] = useState(null)
  const [verifying, setVerifying] = useState(false)
  const [showUploadModal, setShowUploadModal] = useState(false)

  const loadDocuments = useCallback(async () => {
    if (!token) return
    setLoading(true)
    setLoadError(null)
    try {
      const page = await documentsApi.listMyDocuments(token, { page: 0, size: 50 })
      const list = page?.content ?? []
      setDocuments(list)
      if (list.length > 0 && !activeDoc) {
        setActiveDoc(list[0])
      }
    } catch (e) {
      setLoadError(e?.message || String(e))
    } finally {
      setLoading(false)
    }
  }, [token, activeDoc])

  useEffect(() => {
    if (!token || !isAuthenticated) return
    loadDocuments()
  }, [token, isAuthenticated, loadDocuments])

  useEffect(() => {
    if (!activeDoc || !token) {
      setHistory([])
      setVerificationResult(null)
      return
    }
    let cancelled = false
    setVerificationResult(null)
    setHistoryLoading(true)
    documentsApi.getDocumentHistory(token, activeDoc.id, { size: 50 })
      .then((page) => { if (!cancelled) setHistory(page?.content ?? []) })
      .catch(() => { if (!cancelled) setHistory([]) })
      .finally(() => { if (!cancelled) setHistoryLoading(false) })
    return () => { cancelled = true }
  }, [activeDoc, token])

  const handleDocumentUploaded = (dto) => {
    setDocuments((prev) => [dto, ...prev])
    setActiveDoc(dto)
    setShowUploadModal(false)
  }

  const handleDownload = useCallback(async () => {
    if (!token || !activeDoc) return
    try {
      const blob = await documentsApi.downloadDocument(token, activeDoc.id)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = activeDoc.nomOriginal || 'document.pdf'
      a.click()
      URL.revokeObjectURL(url)

      const page = await documentsApi.getDocumentHistory(token, activeDoc.id, { size: 50 })
      setHistory(page?.content ?? [])
    } catch (err) {
      window.alert(`Erreur lors du téléchargement : ${err.message}`)
    }
  }, [token, activeDoc])

  const handleVerifyIntegrity = useCallback(async () => {
    if (!token || !activeDoc) return
    setVerifying(true)
    setVerificationResult(null)
    try {
      const res = await documentsApi.verifyIntegrity(token, activeDoc.id)
      setVerificationResult(res)

      const page = await documentsApi.getDocumentHistory(token, activeDoc.id, { size: 50 })
      setHistory(page?.content ?? [])
    } catch (err) {
      window.alert(`Échec de la vérification : ${err.message}`)
    } finally {
      setVerifying(false)
    }
  }, [token, activeDoc])

  return {
    documents,
    loading,
    loadError,
    activeDoc,
    setActiveDoc,
    history,
    historyLoading,
    verificationResult,
    verifying,
    showUploadModal,
    setShowUploadModal,
    handleDocumentUploaded,
    handleDownload,
    handleVerifyIntegrity,
  }
}
