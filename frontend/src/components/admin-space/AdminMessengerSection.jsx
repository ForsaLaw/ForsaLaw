export default function AdminMessengerSection({
  conversationsTotal,
  conversationsLoading,
  Spinner,
  conversations,
  fmtDate,
  busy,
  handleCloseConversation,
}) {
  return (
    <div className="ledger-feed">
      <div className="ledger-feed-header">RESEAU MESSAGERIE - {conversationsTotal} ACTIVES</div>
      {conversationsLoading && <div style={{ padding: '2rem' }}><Spinner /></div>}
      {!conversationsLoading && conversations.map((c) => (
        <div key={c.id} className="ledger-row">
          <div className="ledger-row-mono" style={{ width: '80px' }}>{c.id}</div>
          <div>
            <div className="ledger-row-subject">AVOCAT: {c.avocatPrenom} {c.avocatNom}</div>
            <div className="ledger-row-mono" style={{ marginTop: '0.4rem' }}>CLIENT: {c.clientPrenom} {c.clientNom} · MAJ: {fmtDate(c.updatedAt)}</div>
          </div>
          <div>
            {c.status === 'CLOSED' ? <span className="status-badge-admin alert">CLOTUREE</span> : (
              <button className="tbl-btn reject" disabled={busy[c.id]} onClick={() => handleCloseConversation(c.id)}>
                {busy[c.id] ? <Spinner /> : 'VERROUILLER LIGNE'}
              </button>
            )}
          </div>
        </div>
      ))}
    </div>
  )
}
