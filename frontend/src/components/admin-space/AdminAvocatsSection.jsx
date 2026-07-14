export default function AdminAvocatsSection({
  avocatsPending,
  avocatsLoading,
  Spinner,
  busy,
  handleVerifyAvocat,
}) {
  return (
    <div className="ledger-feed">
      <div className="ledger-feed-header">PENDING AVOCATS VERIFICATION - {avocatsPending.length} ENTRIES</div>
      {avocatsLoading && <div style={{ padding: '2rem' }}><Spinner /></div>}
      {!avocatsLoading && avocatsPending.length === 0 && (
        <div style={{ padding: '2rem', fontFamily: 'monospace', color: 'var(--gold)', letterSpacing: '0.1em' }}>FILE D'ATTENTE VIDE</div>
      )}
      {!avocatsLoading && avocatsPending.map((a) => (
        <div key={a.id} className="ledger-row">
          <div className="ledger-row-mono" style={{ width: '80px' }}>{a.id}</div>
          <div>
            <div className="ledger-row-subject">{a.nomComplet || `${a.prenom || ''} ${a.nom || ''}`.trim() || '-'}</div>
            <div className="ledger-row-mono" style={{ marginTop: '4px' }}>{a.ville} · {a.specialite}</div>
          </div>
          <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
            <span className="status-badge-admin alert">{a.verificationStatus || 'EN_ATTENTE'}</span>
            <button className="tbl-btn approve" disabled={busy[a.id]} onClick={() => handleVerifyAvocat(a.id, 'APPROVED')}>APP. [Y]</button>
            <button className="tbl-btn reject" disabled={busy[a.id]} onClick={() => handleVerifyAvocat(a.id, 'REJECTED')}>REJ. [N]</button>
          </div>
        </div>
      ))}
    </div>
  )
}
