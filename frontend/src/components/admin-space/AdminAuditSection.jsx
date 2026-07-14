export default function AdminAuditSection({
  auditTotal,
  auditLoading,
  Spinner,
  auditLogs,
  selectedAuditLog,
  setSelectedAuditLog,
  fmtDate,
}) {
  return (
    <div className="ledger-feed">
      <div className="ledger-feed-header">JOURNAL SECURITE AUDIT - {auditTotal} LOGS</div>
      <div style={{ display: 'flex' }}>
        <div style={{ width: '30%', borderRight: '4px solid var(--black)', overflowY: 'auto', maxHeight: '60vh', background: 'var(--charcoal)' }}>
          {auditLoading && <div style={{ padding: '2rem' }}><Spinner /></div>}
          {!auditLoading && auditLogs.map((log) => (
            <div key={log.id} onClick={() => setSelectedAuditLog(log)} style={{ padding: '1rem', borderBottom: '2px solid var(--black)', cursor: 'pointer', background: selectedAuditLog?.id === log.id ? 'var(--black)' : 'transparent', borderLeft: selectedAuditLog?.id === log.id ? '4px solid var(--gold)' : '4px solid transparent' }}>
              <div className="ledger-row-subject" style={{ fontSize: '0.7rem' }}>{log.actionName}</div>
              <div className="ledger-row-mono" style={{ marginTop: '0.2rem' }}>{fmtDate(log.createdAt)}</div>
            </div>
          ))}
        </div>
        {selectedAuditLog ? (
          <div style={{ flex: 1, padding: '2rem', display: 'flex', flexDirection: 'column' }}>
            <div className="ledger-feed-header" style={{ marginBottom: '1.5rem', background: 'transparent', padding: 0 }}>DETAILS D'AUDIT [{selectedAuditLog.id}]</div>
            <div className="ledger-row-mono" style={{ marginBottom: '1rem' }}>
              <div style={{ marginBottom: '0.5rem' }}>UTILISATEUR: {selectedAuditLog.userEmail} ({selectedAuditLog.userId})</div>
              <div style={{ marginBottom: '0.5rem' }}>IP SOURCE: {selectedAuditLog.ipAddress}</div>
              <div style={{ marginBottom: '0.5rem' }}>ROLE OBSERVE: {selectedAuditLog.userRole}</div>
            </div>
            <div style={{ flex: 1, background: '#050505', border: '2px solid var(--black)', padding: '1.5rem', fontFamily: 'monospace', fontSize: '0.75rem', color: 'rgba(255,255,255,0.7)', whiteSpace: 'pre-wrap' }}>
              {selectedAuditLog.details}
            </div>
          </div>
        ) : (
          <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'rgba(255,255,255,0.2)', fontFamily: 'monospace' }}>SELECTIONNER UN LOG</div>
        )}
      </div>
    </div>
  )
}
