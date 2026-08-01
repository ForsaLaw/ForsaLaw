import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import * as messengerApi from '../api/messenger.js'
import * as rdvApi from '../api/rdv.js'

const initials = (prenom, nom) => ((prenom?.[0] ?? '') + (nom?.[0] ?? '')).toUpperCase()
const fmtTime = (iso) => (iso ? new Date(iso).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' }) : '')
const fmtDateShort = (iso) => (iso ? new Date(iso).toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', year: 'numeric' }) : '')
const isSameDay = (a, b) => a && b && new Date(a).toDateString() === new Date(b).toDateString()

function displayNameForConversation(c, roleUser) {
  if (roleUser === 'avocat') return `${c.clientPrenom ?? ''} ${c.clientNom ?? ''}`.trim() || 'Client'
  return `Me. ${c.avocatPrenom ?? ''} ${c.avocatNom ?? ''}`.trim() || 'Avocat'
}

export function useInboxPageData({
  token,
  isAuthenticated,
  roleUser,
  connected,
  subscribe,
  publish,
  navigate,
  searchParams,
  setSearchParams,
}) {
  const [conversations, setConversations] = useState([])
  const [activeId, setActiveId] = useState(null)
  const [messages, setMessages] = useState([])
  const [inputText, setInputText] = useState('')
  const [selectedFiles, setSelectedFiles] = useState([])
  const [loadingConversations, setLoadingConversations] = useState(true)
  const [loadingMessages, setLoadingMessages] = useState(false)
  const [sending, setSending] = useState(false)
  const [errorMsg, setErrorMsg] = useState(null)
  const [remoteTyping, setRemoteTyping] = useState(false)
  const [showRdvPanel, setShowRdvPanel] = useState(false)
  const [proposeStart, setProposeStart] = useState('')
  const [proposeEnd, setProposeEnd] = useState('')
  const [proposeType, setProposeType] = useState('EN_LIGNE')
  const [proposeComment, setProposeComment] = useState('')
  const [rdvBusy, setRdvBusy] = useState(false)
  const [rdvMsg, setRdvMsg] = useState(null)

  const endRef = useRef(null)
  const textareaRef = useRef(null)
  const fileInputRef = useRef(null)
  const typingTimerRef = useRef(null)
  const remoteTypingTimerRef = useRef(null)

  const activeConversation = useMemo(
    () => conversations.find((c) => c.id === activeId) ?? null,
    [conversations, activeId],
  )
  const isClosed = activeConversation?.status === 'CLOSED'

  const loadConversations = useCallback(async () => {
    if (!token || !roleUser) return
    setLoadingConversations(true)
    setErrorMsg(null)
    try {
      const page = await messengerApi.listConversations(token, roleUser, { page: 0, size: 50 })
      const list = page?.content ?? []
      setConversations(list)
      setActiveId((prev) => (prev && list.some((c) => c.id === prev) ? prev : list[0]?.id ?? null))
    } catch (e) {
      setErrorMsg(e?.message || String(e))
    } finally {
      setLoadingConversations(false)
    }
  }, [token, roleUser])

  const loadMessages = useCallback(async () => {
    if (!token || !roleUser || !activeId) return
    setLoadingMessages(true)
    setErrorMsg(null)
    try {
      const page = await messengerApi.getConversationMessages(token, roleUser, activeId, { page: 0, size: 150 })
      setMessages(page?.content ?? [])
      await messengerApi.markConversationRead(token, roleUser, activeId)
      setConversations((prev) => prev.map((c) => (c.id === activeId ? { ...c, unreadCount: 0 } : c)))
    } catch (e) {
      setErrorMsg(e?.message || String(e))
    } finally {
      setLoadingMessages(false)
    }
  }, [token, roleUser, activeId])

  useEffect(() => {
    if (!token || !isAuthenticated) return
    loadConversations()
  }, [token, isAuthenticated, loadConversations])

  useEffect(() => {
    if (!token || !roleUser) return
    const avocatId = searchParams.get('avocatId')
    const clientUserId = searchParams.get('clientUserId')
    const targetId = roleUser === 'avocat' ? clientUserId : avocatId
    if (!targetId) return
    ;(async () => {
      try {
        const conv = await messengerApi.openOrGetConversation(token, roleUser, targetId)
        setConversations((prev) => (prev.some((c) => c.id === conv.id) ? prev : [conv, ...prev]))
        setActiveId(conv.id)
        setSearchParams({})
      } catch (e) {
        setErrorMsg(e?.message || String(e))
      }
    })()
  }, [token, roleUser, searchParams, setSearchParams])

  useEffect(() => {
    if (!activeId || !connected) return
    const destination = `/topic/messenger/conversation/${activeId}/events`
    const unsub = subscribe(destination, (event) => {
      if (!event?.type) return
      switch (event.type) {
        case 'NEW_MESSAGE':
          if (event.message) {
            setMessages((prev) => {
              if (prev.some((m) => m.id === event.message.id)) return prev
              return [...prev, event.message]
            })
            setConversations((prev) => prev.map((c) =>
              c.id === activeId
                ? { ...c, lastMessageAt: event.message.createdAt, lastMessagePreview: event.message.content?.slice(0, 60) }
                : c,
            ))
          }
          break
        case 'TYPING':
          if (event.typing) {
            setRemoteTyping(event.typing.typing)
            clearTimeout(remoteTypingTimerRef.current)
            if (event.typing.typing) {
              remoteTypingTimerRef.current = setTimeout(() => setRemoteTyping(false), 3000)
            }
          }
          break
        case 'READ_RECEIPT':
          setMessages((prev) => prev.map((m) => ({ ...m, readByOther: true })))
          break
        default:
          break
      }
    })
    return () => {
      unsub()
      setRemoteTyping(false)
      clearTimeout(remoteTypingTimerRef.current)
    }
  }, [activeId, connected, subscribe])

  useEffect(() => {
    if (!activeId) {
      setMessages([])
      return
    }
    loadMessages()
    setInputText('')
    setSelectedFiles([])
    setRemoteTyping(false)
    if (textareaRef.current) textareaRef.current.style.height = 'auto'
  }, [activeId, loadMessages])

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const emitTyping = useCallback((isTyping) => {
    if (!activeId || !connected) return
    publish('/app/messenger/typing', { conversationId: activeId, typing: isTyping })
  }, [activeId, connected, publish])

  const onInput = (e) => {
    setInputText(e.target.value)
    const el = e.target
    el.style.height = 'auto'
    el.style.height = `${Math.min(el.scrollHeight, 120)}px`
    emitTyping(true)
    clearTimeout(typingTimerRef.current)
    typingTimerRef.current = setTimeout(() => emitTyping(false), 2000)
  }

  const sendMessage = useCallback(async () => {
    if (!token || !roleUser || !activeConversation || sending || isClosed) return
    const text = inputText.trim()
    if (!text && selectedFiles.length === 0) return

    setSending(true)
    setErrorMsg(null)
    try {
      if (selectedFiles.length > 0) {
        const created = await messengerApi.sendMessageWithAttachments(token, roleUser, activeConversation.id, {
          content: text,
          files: selectedFiles,
        })
        setMessages((prev) => [...prev, created])
      } else {
        const targetId = roleUser === 'avocat' ? activeConversation.clientUserId : activeConversation.avocatId
        const result = await messengerApi.sendTextMessage(token, roleUser, targetId, text)
        setMessages((prev) => [...prev, result.message])
      }
      setInputText('')
      setSelectedFiles([])
      if (textareaRef.current) textareaRef.current.style.height = 'auto'
      await loadConversations()
    } catch (e) {
      setErrorMsg(e?.message || String(e))
    } finally {
      setSending(false)
    }
  }, [token, roleUser, activeConversation, sending, isClosed, inputText, selectedFiles, loadConversations])

  const onKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      void sendMessage()
    }
  }

  const onSelectFiles = (e) => {
    const files = Array.from(e.target.files ?? [])
    setSelectedFiles(files)
    e.target.value = ''
  }

  const removeFile = (idx) => {
    setSelectedFiles((prev) => prev.filter((_, i) => i !== idx))
  }

  const sendRdvProposal = useCallback(async () => {
    if (!token || !activeConversation) return
    setRdvBusy(true)
    setRdvMsg(null)
    try {
      const rdvPage = await rdvApi.listLawyerAppointments(token, { page: 0, size: 50 })
      const pending = (rdvPage?.content ?? []).find(
        (r) => r.statutRendezVous === 'EN_ATTENTE' &&
          String(r.clientUserId ?? r.clientId) === String(activeConversation.clientUserId),
      )
      if (!pending) {
        setRdvMsg('Aucune demande EN_ATTENTE pour ce client.')
        return
      }
      await rdvApi.lawyerProposeSlot(token, pending.idRendezVous, {
        // Saisie datetime-local (heure locale, sans fuseau) -> ISO-8601 avec decalage (OffsetDateTime).
        dateHeureDebut: proposeStart ? new Date(proposeStart).toISOString() : proposeStart,
        dateHeureFin: proposeEnd ? new Date(proposeEnd).toISOString() : proposeEnd,
        typeRendezVous: proposeType,
        commentaireAvocat: proposeComment.trim() || undefined,
      })
      setRdvMsg('✓ Créneau proposé avec succès.')
      setProposeStart('')
      setProposeEnd('')
      setProposeComment('')
    } catch (e) {
      setRdvMsg(e?.message || String(e))
    } finally {
      setRdvBusy(false)
    }
  }, [token, activeConversation, proposeStart, proposeEnd, proposeType, proposeComment])

  return {
    conversations,
    activeId,
    setActiveId,
    messages,
    inputText,
    selectedFiles,
    loadingConversations,
    loadingMessages,
    sending,
    errorMsg,
    remoteTyping,
    showRdvPanel,
    setShowRdvPanel,
    proposeStart,
    setProposeStart,
    proposeEnd,
    setProposeEnd,
    proposeType,
    setProposeType,
    proposeComment,
    setProposeComment,
    rdvBusy,
    rdvMsg,
    // Expose le setter, que InboxPage appelait deja sans l'avoir : ouvrir puis refermer le
    // panneau « Proposer un RDV » levait une ReferenceError (setRdvMsg is not defined) et
    // laissait le message de la tentative precedente affiche. Defaut trouve par le linter
    // des sa premiere execution sur le depot.
    setRdvMsg,
    endRef,
    textareaRef,
    fileInputRef,
    activeConversation,
    isClosed,
    onInput,
    onKeyDown,
    onSelectFiles,
    removeFile,
    sendMessage,
    sendRdvProposal,
    displayNameForConversation,
    initials,
    fmtTime,
    fmtDateShort,
    isSameDay,
    navigate,
  }
}
