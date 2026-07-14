export default function AdminUsersSection({ users, usersLoading, Spinner, busy, handleToggleUser }) {
  return (
    <div className="ledger-feed">
      <div className="ledger-feed-header">MATRICE DES UTILISATEURS - {users.length} LOADED</div>
      {usersLoading && <div style={{ padding: '2rem' }}><Spinner /></div>}
      {!usersLoading && users.map((u) => (
        <div key={u.id} className="ledger-row">
          <div className="ledger-row-mono" style={{ width: '80px' }}>{u.id}</div>
          <div>
            <div className="ledger-row-subject">{`${u.prenom || ''} ${u.nom || ''}`.trim() || '-'}</div>
            <div className="ledger-row-mono" style={{ marginTop: '4px' }}>{u.email}</div>
          </div>
          <div style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
            <span className={`role-badge ${u.roleUser}`}>{(u.roleUser || '').toUpperCase()}</span>
            <span className={`status-badge-admin ${u.actif ? 'open' : 'alert'}`}>{u.actif ? 'ACTIF' : 'SUSPENDU'}</span>
            <button className={`tbl-btn ${u.actif ? 'reject' : 'approve'}`} disabled={busy[u.id]} onClick={() => handleToggleUser(u.id, u.actif)}>
              {busy[u.id] ? <Spinner /> : u.actif ? 'SUSPENDRE' : 'REACTIVER'}
            </button>
          </div>
        </div>
      ))}
    </div>
  )
}
