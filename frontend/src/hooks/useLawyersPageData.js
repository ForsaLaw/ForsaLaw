import { useEffect, useMemo, useState } from 'react'
import * as avocatsApi from '../api/avocats.js'
import * as rdvApi from '../api/rdv.js'

const DAY_LABELS = {
  1: 'Lun',
  2: 'Mar',
  3: 'Mer',
  4: 'Jeu',
  5: 'Ven',
  6: 'Sam',
  7: 'Dim',
}

const timeLabel = (v) => (typeof v === 'string' ? v.slice(0, 5) : '')

export function useLawyersPageData({ isAuthenticated, user, token }) {
  const [selectedSpecs, setSelectedSpecs] = useState([])
  const [lawyers, setLawyers] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [scheduleByLawyerId, setScheduleByLawyerId] = useState({})
  const [canContactByLawyerId, setCanContactByLawyerId] = useState({})

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      setLoading(true)
      setError(null)
      try {
        const page = await avocatsApi.listPublicAvocats({ page: 0, size: 80 })
        if (!cancelled) setLawyers(page?.content ?? [])
      } catch (e) {
        if (!cancelled) setError(e?.message || String(e))
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [])

  const specialties = useMemo(() => {
    const set = new Set(
      lawyers
        .map((l) => l.specialiteLibelle || l.specialite)
        .filter(Boolean),
    )
    return Array.from(set).sort((a, b) => a.localeCompare(b, 'fr'))
  }, [lawyers])

  const filteredLawyers = useMemo(() => (
    selectedSpecs.length === 0
      ? lawyers
      : lawyers.filter((l) => selectedSpecs.includes(l.specialiteLibelle || l.specialite))
  ), [lawyers, selectedSpecs])

  const toggleSpec = (spec) => {
    setSelectedSpecs((prev) => (
      prev.includes(spec) ? prev.filter((s) => s !== spec) : [...prev, spec]
    ))
  }

  const canContactBase = isAuthenticated && user?.roleUser === 'client'
  const canBook = isAuthenticated && user?.roleUser === 'client'

  useEffect(() => {
    if (lawyers.length === 0) {
      setScheduleByLawyerId({})
      return
    }

    let cancelled = false

    ;(async () => {
      const pairs = await Promise.all(
        lawyers.map(async (lawyer) => {
          try {
            const agenda = await rdvApi.getPublicAgenda(lawyer.id)
            const plages = agenda?.plages ?? []
            if (plages.length === 0) {
              return [lawyer.id, agenda?.agendaActif === false ? 'Agenda inactif' : 'Horaires non renseignes']
            }
            const plagesSummary = plages
              .map((p) => `${DAY_LABELS[p.dayOfWeek] || `J${p.dayOfWeek}`} ${timeLabel(p.heureDebut)}-${timeLabel(p.heureFin)}`)
              .join(' | ')
            const summary = agenda?.agendaActif === false
              ? `Agenda inactif · ${plagesSummary}`
              : plagesSummary
            return [lawyer.id, summary]
          } catch {
            return [lawyer.id, null]
          }
        }),
      )
      if (!cancelled) setScheduleByLawyerId(Object.fromEntries(pairs))
    })()

    return () => {
      cancelled = true
    }
  }, [lawyers])

  useEffect(() => {
    if (!isAuthenticated || !user || user.roleUser !== 'client') {
      setCanContactByLawyerId({})
      return
    }
    let cancelled = false
    ;(async () => {
      try {
        const page = await rdvApi.listClientAppointments(token, { page: 0, size: 300 })
        const confirmedAvocatIds = new Set(
          (page?.content ?? [])
            .filter((r) => r.statutRendezVous === 'CONFIRME')
            .map((r) => String(r.idAvocat ?? r.avocatId ?? ''))
            .filter(Boolean),
        )
        const map = Object.fromEntries(lawyers.map((l) => [l.id, confirmedAvocatIds.has(String(l.id))]))
        if (!cancelled) setCanContactByLawyerId(map)
      } catch {
        if (!cancelled) setCanContactByLawyerId({})
      }
    })()
    return () => {
      cancelled = true
    }
  }, [isAuthenticated, user, token, lawyers])

  return {
    selectedSpecs,
    specialties,
    filteredLawyers,
    toggleSpec,
    loading,
    error,
    canContactBase,
    canBook,
    scheduleByLawyerId,
    canContactByLawyerId,
  }
}
