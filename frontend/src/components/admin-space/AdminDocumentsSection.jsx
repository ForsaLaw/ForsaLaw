import { Trash2 } from 'lucide-react'

export default function AdminDocumentsSection({
  docsTotal,
  docsLoading,
  Spinner,
  docs,
  busy,
  handleDeleteDocument,
}) {
  return (
    <div className="ledger-feed">
      <div className="ledger-feed-header">COFFRE-FORT VAULT - {docsTotal} SCELLES</div>
      {docsLoading && <div style={{ padding: '2rem' }}><Spinner /></div>}
      {!docsLoading && docs.map((d) => (
        <div key={d.id} className="ledger-row">
          <div className="ledger-row-mono" style={{ width: '80px' }}>{(d.id || '').split('-')[1] || d.id}</div>
          <div>
            <div className="ledger-row-subject">{d.nomDeposeur || d.deposeurId}</div>
            <div className="ledger-row-mono" style={{ marginTop: '0.4rem', color: 'var(--gold)' }}>SHA-256: {d.hashSha256}</div>
          </div>
          <div>
            <span className="status-badge-admin" style={{ marginRight: '1rem' }}>{d.typeContenu}</span>
            <button className="tbl-btn reject" disabled={busy[d.id]} onClick={() => handleDeleteDocument(d.id)}>
              {busy[d.id] ? <Spinner /> : <Trash2 size={12} />}
            </button>
          </div>
        </div>
      ))}
    </div>
  )
}
