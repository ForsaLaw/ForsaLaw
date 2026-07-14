import { motion } from 'framer-motion'
import { Loader2, Video } from 'lucide-react'

export default function ClientAppointmentsTab({
  token,
  appointments,
  appointmentsLoading,
  fmtDateTime,
  rdvApi,
  reloadAppointments,
}) {
  return (
    <div className="ledger-feed">
      <div className="ledger-feed-header">
        REGISTRE DES AUDIENCES (RDV) - {appointments.length} ENTRIES
      </div>
      {appointmentsLoading && (
        <div style={{ padding: '1rem', color: 'var(--gold)' }}>
          <Loader2 className="forsalaw-spin" size={18} />
        </div>
      )}
      {!appointmentsLoading && appointments.length === 0 && (
        <div style={{ padding: '1rem', opacity: 0.7 }}>Aucune demande de rendez-vous pour le moment.</div>
      )}
      {!appointmentsLoading && appointments.map((a, i) => (
        <motion.div
          key={a.idRendezVous}
          className="ledger-row"
          initial={{ opacity: 0, x: 12 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ delay: i * 0.1 }}
        >
          <div className="appt-date-block">
            <span className="appt-day">{(a.dateHeureDebut ? new Date(a.dateHeureDebut) : new Date()).getDate()}</span>
            <span className="appt-month">{a.dateHeureDebut ? new Date(a.dateHeureDebut).toLocaleString('fr-FR', { month: 'short' }).toUpperCase() : '-'}</span>
          </div>
          <div>
            <div className="ledger-row-subject">{a.motifConsultation || 'Demande de rendez-vous'}</div>
            <div className="ledger-row-mono" style={{ marginTop: '0.4rem' }}>
              ME. {a.nomAvocat} · {fmtDateTime(a.dateHeureDebut)} {a.dateHeureFin ? `-> ${fmtDateTime(a.dateHeureFin)}` : ''} · {a.typeRendezVous}
            </div>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: '0.5rem' }}>
            <span className="status-badge-admin open">{a.statutRendezVous}</span>
            {a.statutRendezVous === 'PROPOSE' && (
              <div style={{ display: 'flex', gap: '0.4rem' }}>
                <button
                  type="button"
                  className="tbl-btn approve"
                  onClick={async () => {
                    try {
                      await rdvApi.clientAcceptProposal(token, a.idRendezVous)
                      await reloadAppointments()
                    } catch (e) {
                      window.alert(e?.message || String(e))
                    }
                  }}
                >
                  Accepter
                </button>
                <button
                  type="button"
                  className="tbl-btn reject"
                  onClick={async () => {
                    const raison = window.prompt('Raison du refus ?') || ''
                    try {
                      await rdvApi.clientRefuseProposal(token, a.idRendezVous, raison)
                      await reloadAppointments()
                    } catch (e) {
                      window.alert(e?.message || String(e))
                    }
                  }}
                >
                  Refuser
                </button>
              </div>
            )}
            {a.statutRendezVous !== 'ANNULE' && (
              <button
                type="button"
                className="tbl-btn reject"
                onClick={async () => {
                  const raison = window.prompt('Raison annulation ?') || ''
                  try {
                    await rdvApi.clientCancelAppointment(token, a.idRendezVous, raison)
                    await reloadAppointments()
                  } catch (e) {
                    window.alert(e?.message || String(e))
                  }
                }}
              >
                Annuler
              </button>
            )}
            {a.statutRendezVous === 'CONFIRME' && a.typeRendezVous === 'EN_LIGNE' && (
              <button
                type="button"
                className="tbl-btn approve"
                style={{ display: 'flex', alignItems: 'center', gap: '6px' }}
                onClick={async () => {
                  try {
                    const x = await rdvApi.clientMeetingAccess(token, a.idRendezVous)
                    const url = x.joinPath?.startsWith('http') ? x.joinPath : `${window.location.origin}${x.joinPath}`
                    window.open(url, '_blank', 'noopener,noreferrer')
                  } catch (e) {
                    window.alert(e?.message || String(e))
                  }
                }}
              >
                <Video size={12} /> ENTRER SALLE
              </button>
            )}
          </div>
        </motion.div>
      ))}
    </div>
  )
}
