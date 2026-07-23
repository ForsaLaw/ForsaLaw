import { useState, useEffect, useMemo, useCallback } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { Clock, ChevronLeft, ChevronRight, Plus, Video, AlertTriangle } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../context/AuthContext.jsx'
import * as rdvApi from '../api/rdv.js'
import '../styles/Calendar.css'

const DAYS = ['LUN', 'MAR', 'MER', 'JEU', 'VEN', 'SAM', 'DIM']

function toLocalIsoDate(date) {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

function getFormattedTime(isoString) {
  if (!isoString) return ''
  try {
    const d = new Date(isoString)
    return d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })
  } catch {
    return ''
  }
}

export default function CalendarPage() {
  const { t } = useTranslation()
  const { user, token, isAuthenticated } = useAuth()
  
  const [baseDate, setBaseDate] = useState(new Date())
  const [appointments, setAppointments] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  // Fetch appointments
  const fetchAppointments = useCallback(async () => {
    if (!isAuthenticated || !token) {
      setLoading(false)
      return
    }
    setLoading(true)
    setError(null)
    try {
      let data = { content: [] }
      if (user?.roleUser === 'client') {
         data = await rdvApi.listClientAppointments(token, { size: 100 })
      } else if (user?.roleUser === 'avocat') {
         data = await rdvApi.listLawyerAppointments(token, { size: 100 })
      }
      setAppointments(data?.content ?? [])
    } catch (e) {
      setError(e?.message || String(e))
    } finally {
      setLoading(false)
    }
  }, [isAuthenticated, token, user?.roleUser])

  useEffect(() => {
    fetchAppointments()
  }, [fetchAppointments])

  const goPrevMonth = () => {
    const next = new Date(baseDate)
    next.setMonth(next.getMonth() - 1)
    setBaseDate(next)
  }

  const goNextMonth = () => {
    const next = new Date(baseDate)
    next.setMonth(next.getMonth() + 1)
    setBaseDate(next)
  }

  const currentMonthLabel = baseDate.toLocaleString('fr-FR', { month: 'long', year: 'numeric' }).toUpperCase()

  // Generate grid mapping
  const gridDays = useMemo(() => {
    const year = baseDate.getFullYear()
    const month = baseDate.getMonth()
    
    const firstDayOfMonth = new Date(year, month, 1)
    const lastDayOfMonth = new Date(year, month + 1, 0)
    
    // getDay() gives 0=Sun. We want 0=Mon, 6=Sun
    let startDayOfWeek = firstDayOfMonth.getDay() - 1;
    if (startDayOfWeek === -1) startDayOfWeek = 6; 

    const days = []

    // Padding from previous month
    const prevMonthLastDay = new Date(year, month, 0).getDate()
    for (let i = startDayOfWeek - 1; i >= 0; i--) {
      days.push({
        id: `prev-${i}`,
        day: prevMonthLastDay - i,
        active: false,
        isoObj: null
      })
    }

    // Current month days
    for (let i = 1; i <= lastDayOfMonth.getDate(); i++) {
       const cd = new Date(year, month, i)
       const iso = toLocalIsoDate(cd)
       
       // dateHeureDebut est un instant (ISO-8601 avec fuseau) : comparer la date LOCALE,
       // et non le prefixe brut de la chaine (qui peut etre en UTC et tomber un autre jour).
       const dayAppts = appointments.filter(a => a.dateHeureDebut && toLocalIsoDate(new Date(a.dateHeureDebut)) === iso)
       
       days.push({
         id: `curr-${i}`,
         day: i,
         active: true,
         isoObj: iso,
         appointments: dayAppts
       })
    }

    // Padding for next month to complete the row (multiple of 7)
    const totalCells = Math.ceil(days.length / 7) * 7;
    const remaining = totalCells - days.length;
    for (let i = 1; i <= remaining; i++) {
        days.push({
          id: `next-${i}`,
          day: i,
          active: false,
          isoObj: null
        })
    }

    return days
  }, [baseDate, appointments])

  return (
    <motion.main 
      className="calendar-page"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
    >
      <header className="calendar-top">
        <div>
          <div className="calendar-eyebrow">{t('calendar_tag')}</div>
          <h1 className="calendar-title">{t('calendar_title')}</h1>
          <div className="calendar-title-divider" />
          <p className="platform-subtitle" style={{ marginBottom: 0 }}>
             Agenda des rendez-vous et échéances
          </p>
        </div>

        <div className="calendar-nav-controls">
          <button className="brutal-btn" style={{ padding: '0.75rem' }} onClick={goPrevMonth}><ChevronLeft size={20} /></button>
          <span className="calendar-month-label">{currentMonthLabel}</span>
          <button className="brutal-btn" style={{ padding: '0.75rem' }} onClick={goNextMonth}><ChevronRight size={20} /></button>
        </div>
      </header>

      <div className="calendar-grid-wrapper">
        {/* Error / Auth Notice */}
        {error && (
            <div style={{ background: 'rgba(180,30,30,0.12)', border: '2px solid #b42020', padding: '1rem', color: '#ff8a80', marginBottom: '1rem', fontSize: '0.8rem', display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
                <AlertTriangle size={16} /> Erreur: {error}
            </div>
        )}
        {!isAuthenticated && !error && (
            <div style={{ padding: '1rem', border: '3px solid var(--black)', textAlign: 'center', marginBottom: '1rem', color: 'rgba(255,255,255,0.4)', fontSize: '0.8rem', textTransform: 'uppercase', letterSpacing: '0.1em' }}>
                Connectez-vous pour voir vos rendez-vous.
            </div>
        )}

        {/* DAYS HEADER */}
        <div className="calendar-days-header">
          {DAYS.map(day => (
            <div key={day} className="calendar-day-name">{day}</div>
          ))}
        </div>

        {/* CALENDAR ACCORDING TO AUTH */}
        <div className="calendar-grid">
          <AnimatePresence mode="popLayout">
          {gridDays.map((dayObj, i) => (
            <motion.div 
               key={dayObj.id} 
               className={`calendar-day-cell ${!dayObj.active ? 'inactive' : ''}`}
               initial={{ opacity: 0, scale: 0.95 }}
               animate={{ opacity: 1, scale: 1 }}
               exit={{ opacity: 0, scale: 0.95 }}
               transition={{ duration: 0.15, delay: i * 0.01 }}
            >
              <span className="calendar-day-number">{dayObj.day}</span>
              
              {dayObj.active && dayObj.appointments && dayObj.appointments.map(appt => (
                <div key={appt.idRendezVous} className="calendar-appt">
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.3rem', opacity: 0.6 }}>
                      <Clock size={10} style={{ flexShrink: 0 }} />
                      <span>{getFormattedTime(appt.dateHeureDebut)}</span>
                  </div>
                  <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', fontWeight: 600, color: 'var(--white)' }}>
                      {appt.motifConsultation || 'Consultation'}
                  </span>
                  
                  {appt.statutRendezVous !== 'CONFIRME' && (
                      <div style={{ marginTop: '0.2rem', fontSize: '0.45rem', padding: '1px 4px', background: 'var(--gold)', color: 'var(--black)', display: 'inline-block', fontWeight: 900, textTransform: 'uppercase', borderRadius: '10px' }}>
                          {appt.statutRendezVous}
                      </div>
                  )}

                  {appt.typeRendezVous === 'EN_LIGNE' && appt.statutRendezVous === 'CONFIRME' && (
                    <div style={{ marginTop: '0.35rem', display: 'flex', alignItems: 'center', gap: '0.25rem', color: 'var(--gold)', cursor: appt.meetingUrl ? 'pointer' : 'default' }}>
                      <Video size={10} /> 
                      <span style={{ fontSize: '0.55rem', whiteSpace: 'nowrap', textDecoration: appt.meetingUrl ? 'underline' : 'none' }}>
                          {appt.meetingUrl ? 'Rejoindre la visio' : 'Visio prévue'}
                      </span>
                    </div>
                  )}
                </div>
              ))}

              {dayObj.active && (!dayObj.appointments || dayObj.appointments.length === 0) && isAuthenticated && user?.roleUser === 'avocat' && (
                <div className="calendar-available" style={{ opacity: 0.2 }}>
                  <Plus size={12} /> Libre
                </div>
              )}
            </motion.div>
          ))}
          </AnimatePresence>
        </div>
      </div>

      <div className="calendar-legend">
         <div className="legend-item">
            <span className="legend-swatch booked" />
            Rendez-vous prévus
         </div>
         <div className="legend-item">
            <span className="legend-swatch available" />
            Plages libres
         </div>
      </div>
    </motion.main>
  )
}
