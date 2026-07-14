import { Award, Loader2 } from 'lucide-react'

export default function LawyerAppointmentsSection({
  appointmentsLoading,
  appointments,
  fmtDateTime,
  proposeStart,
  setProposeStart,
  proposeEnd,
  setProposeEnd,
  proposeType,
  setProposeType,
  proposeComment,
  setProposeComment,
  onProposeSlot,
  onCancelAppointment,
  onOpenOnlineRoom,
}) {
  return (
    <section className="client-pro-card lawyer-space-section">
      <div className="client-pro-card__head">
        <div className="client-pro-card__icon">
          <Award size={18} />
        </div>
        <div>
          <h2 className="client-pro-card__title">Demandes et rendez-vous</h2>
          <p className="client-pro-card__sub">Proposez un creneau depuis les demandes en attente et suivez les confirmations clients.</p>
        </div>
      </div>
      <div className="client-pro-card__body">
        {appointmentsLoading && <Loader2 className="forsalaw-spin" size={18} />}
        {!appointmentsLoading && appointments.length === 0 && <p>Aucune demande recue.</p>}
        {!appointmentsLoading && appointments.map((a) => (
          <div key={a.idRendezVous} className="lawyer-rdv-card">
            <p className="lawyer-rdv-card__title"><strong>{a.nomClient}</strong> - {a.motifConsultation || 'Demande de rendez-vous'}</p>
            <p className="lawyer-rdv-card__meta">{a.statutRendezVous} · {a.typeRendezVous} · {fmtDateTime(a.dateHeureDebut)}</p>
            {a.commentaireAvocat && <p className="lawyer-rdv-card__meta">Commentaire: {a.commentaireAvocat}</p>}
            {a.statutRendezVous === 'EN_ATTENTE' && (
              <div className="lawyer-rdv-card__proposal">
                <div className="lawyer-rdv-card__proposal-row">
                  <input type="datetime-local" value={proposeStart} onChange={(e) => setProposeStart(e.target.value)} />
                  <input type="datetime-local" value={proposeEnd} onChange={(e) => setProposeEnd(e.target.value)} />
                  <select value={proposeType} onChange={(e) => setProposeType(e.target.value)}>
                    <option value="EN_LIGNE">En ligne</option>
                    <option value="CABINET">Cabinet</option>
                    <option value="TELEPHONE">Telephone</option>
                  </select>
                </div>
                <input placeholder="Commentaire avocat (optionnel)" value={proposeComment} onChange={(e) => setProposeComment(e.target.value)} />
                <button
                  type="button"
                  className="lawyer-space-btn-primary"
                  onClick={() => onProposeSlot(a.idRendezVous)}
                >
                  Proposer ce creneau
                </button>
              </div>
            )}
            <div className="lawyer-rdv-card__actions">
              {a.statutRendezVous !== 'ANNULE' && (
                <button
                  type="button"
                  className="lawyer-space-btn-danger"
                  onClick={() => onCancelAppointment(a.idRendezVous)}
                >
                  Annuler
                </button>
              )}
              {a.statutRendezVous === 'CONFIRME' && a.typeRendezVous === 'EN_LIGNE' && (
                <button
                  type="button"
                  className="lawyer-space-btn-secondary"
                  onClick={() => onOpenOnlineRoom(a.idRendezVous)}
                >
                  Ouvrir la salle
                </button>
              )}
            </div>
          </div>
        ))}
      </div>
    </section>
  )
}
