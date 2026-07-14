const STATUT_RECLAMATION_OPTIONS = ['OUVERTE', 'EN_COURS', 'RESOLUE', 'FERMEE']

export default function AdminReclamationsSection({
  reclamationsTotal,
  reclamationsLoading,
  reclamations,
  Spinner,
  busy,
  onUpdateStatus,
  fmtDate,
}) {
  return (
    <div className="ledger-panel">
      <div className="ledger-feed-header">REGISTRE RECLAMATIONS - {reclamationsTotal} TOTAL</div>
      {reclamationsLoading && <div style={{ padding: '2rem' }}><Spinner /></div>}

      {!reclamationsLoading && reclamations.map((r) => (
        <div key={r.id} className="ledger-item">
          <div className="ledger-item-main">
            <h4>{r.titre || r.sujet || `RECL-${r.id}`}</h4>
            <p style={{ marginTop: '0.35rem' }}>{r.description || r.message || 'Aucune description'}</p>
            <div style={{ marginTop: '0.45rem', fontSize: '0.72rem', opacity: 0.75 }}>
              <span>CLIENT: {r.clientId || r.idClient || '—'}</span>
              <span style={{ margin: '0 0.5rem' }}>·</span>
              <span>CREE LE {fmtDate(r.dateCreation || r.createdAt)}</span>
            </div>
          </div>

          <div className="ledger-item-side">
            <span className={`status-badge-admin ${r.statut === 'RESOLUE' ? 'ok' : r.statut === 'FERMEE' ? '' : 'alert'}`}>
              {r.statut || 'OUVERTE'}
            </span>

            <select
              value={r.statut || 'OUVERTE'}
              onChange={(e) => onUpdateStatus(r.id, e.target.value)}
              disabled={!!busy[r.id]}
              style={{ marginTop: '0.6rem' }}
            >
              {STATUT_RECLAMATION_OPTIONS.map((s) => (
                <option key={s} value={s}>{s}</option>
              ))}
            </select>
          </div>
        </div>
      ))}

      {!reclamationsLoading && reclamations.length === 0 && (
        <div className="ledger-empty">Aucune réclamation pour le moment.</div>
      )}
    </div>
  )
}
