import { Activity } from 'lucide-react'

export default function AdminRendezvousSection({
  rdvsTotal,
  busy,
  handleTriggerReminders,
  Spinner,
  rdvsLoading,
  rdvs,
  fmtDate,
}) {
  return (
    <div className="ledger-feed">
      <div className="ledger-feed-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <span>MATRICE RENDEZ-VOUS - {rdvsTotal} TOTAL</span>
        <button className="tbl-btn approve" disabled={busy.trigger} onClick={handleTriggerReminders} style={{ padding: '0.2rem 0.5rem', display: 'flex', alignItems: 'center', gap: '5px' }}>
          {busy.trigger ? <Spinner /> : <Activity size={12} />} DAEMON RAPPELS
        </button>
      </div>
      {rdvsLoading && <div style={{ padding: '2rem' }}><Spinner /></div>}
      {!rdvsLoading && rdvs.map((r) => (
        <div key={r.idRendezVous} className="ledger-row">
          <div className="ledger-row-mono" style={{ width: '80px' }}>{r.idRendezVous}</div>
          <div>
            <div className="ledger-row-subject">{r.typeRendezVous}</div>
            <div className="ledger-row-mono" style={{ marginTop: '4px' }}>DEBUT: {fmtDate(r.dateHeureDebut)} · FIN: {fmtDate(r.dateHeureFin)}</div>
          </div>
          <div>
            <span className={`status-badge-admin ${r.statutRendezVous === 'CONFIRME' ? 'open' : 'alert'}`}>{r.statutRendezVous}</span>
          </div>
        </div>
      ))}
    </div>
  )
}
