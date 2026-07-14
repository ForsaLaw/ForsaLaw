export default function AdminAffairesSection({ affairesTotal, affairesLoading, Spinner, affaires, fmtDate }) {
  return (
    <div className="ledger-feed">
      <div className="ledger-feed-header">BASE DE DONNEES AFFAIRES - {affairesTotal} TOTAL</div>
      {affairesLoading && <div style={{ padding: '2rem' }}><Spinner /></div>}
      {!affairesLoading && affaires.map((a) => (
        <div key={a.idAffaire} className="ledger-row">
          <div className="ledger-row-mono" style={{ width: '80px' }}>{a.idAffaire}</div>
          <div>
            <div className="ledger-row-subject">{a.titre || 'Dossier Systematique'}</div>
            <div className="ledger-row-mono" style={{ marginTop: '4px' }}>CREATION: {fmtDate(a.dateCreation)} · MAJ: {fmtDate(a.dateMiseAJour)}</div>
          </div>
          <div>
            <span className="status-badge-admin open">{a.statut || 'INSTRUCTION'}</span>
          </div>
        </div>
      ))}
      {!affairesLoading && affaires.length === 0 && (
        <div style={{ padding: '2rem', fontFamily: 'monospace', color: 'var(--gold)', letterSpacing: '0.1em' }}>AUCUNE AFFAIRE ENREGISTREE</div>
      )}
    </div>
  )
}
