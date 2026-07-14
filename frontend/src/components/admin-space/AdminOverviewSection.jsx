import { motion } from 'framer-motion'

export default function AdminOverviewSection({
  usersLoading,
  usersTotal,
  avocatsLoading,
  avocatsPending,
  reclamationsLoading,
  reclamationsTotal,
  affairesLoading,
  affairesTotal,
  auditLoading,
  auditLogs,
  fmtDate,
  Spinner,
  affaires,
  rdvsLoading,
  rdvsTotal,
  conversationsLoading,
  conversationsTotal,
  docsLoading,
  docsTotal,
}) {
  const stats = [
    {
      label: 'Utilisateurs',
      value: usersLoading ? '...' : usersTotal,
      subtitle: 'Comptes actifs',
    },
    {
      label: 'Avocats en attente',
      value: avocatsLoading ? '...' : avocatsPending.length,
      subtitle: 'Verification manuelle',
      alert: !avocatsLoading && avocatsPending.length > 0,
    },
    {
      label: 'Reclamations',
      value: reclamationsLoading ? '...' : reclamationsTotal,
      subtitle: 'Demandes ouvertes',
    },
    {
      label: 'Affaires',
      value: affairesLoading ? '...' : affairesTotal,
      subtitle: 'Dossiers suivis',
    },
  ]

  const activityRows = [
    { label: 'Messagerie', value: conversationsLoading ? 0 : conversationsTotal, max: 40, loading: conversationsLoading },
    { label: 'Documents', value: docsLoading ? 0 : docsTotal, max: 120, loading: docsLoading },
    { label: 'Rendez-vous', value: rdvsLoading ? 0 : rdvsTotal, max: 90, loading: rdvsLoading },
    { label: 'Audit logs', value: auditLoading ? 0 : auditLogs.length, max: 120, loading: auditLoading },
  ]

  const completion = activityRows.map((row) => ({
    ...row,
    percent: row.loading ? 0 : Math.min(100, Math.round((row.value / row.max) * 100)),
  }))

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
      <div className="command-kpi-row" style={{ marginBottom: 0 }}>
        {stats.map((s, i) => (
          <motion.div key={s.label} className="command-kpi-block" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.05 }}>
            <div className="command-kpi-lbl">{s.label}</div>
            <div className="command-kpi-val" style={{ color: s.alert ? '#ff8a80' : '' }}>{s.value}</div>
            <div className="ledger-row-mono" style={{ marginTop: '0.5rem', opacity: 0.6 }}>{s.subtitle}</div>
          </motion.div>
        ))}
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '1.5rem' }}>
        <div className="ledger-feed" style={{ marginTop: 0 }}>
          <div className="ledger-feed-header" style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span>PLATEFORM ACTIVITY OVERVIEW</span>
            <span style={{ color: 'rgba(255,255,255,0.4)', fontFamily: 'monospace' }}>REALTIME</span>
          </div>
          <div style={{ padding: '1.2rem 1.5rem', background: '#0a0a0a' }}>
            {completion.map((row) => (
              <div key={row.label} style={{ marginBottom: '1rem' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontFamily: 'monospace', fontSize: '0.65rem', marginBottom: '0.35rem' }}>
                  <span style={{ color: 'rgba(255,255,255,0.75)' }}>{row.label}</span>
                  <span style={{ color: 'var(--gold)' }}>{row.loading ? '...' : row.value} ({row.percent}%)</span>
                </div>
                <div style={{ height: '10px', background: '#171717', border: '1px solid #262626' }}>
                  <div style={{ width: `${row.percent}%`, height: '100%', background: 'linear-gradient(90deg, #d4af37 0%, #f4d87b 100%)' }} />
                </div>
              </div>
            ))}
          </div>
        </div>

        <div className="ledger-feed" style={{ marginTop: 0 }}>
          <div className="ledger-feed-header" style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span>LIVE SECURITY INTERCEPT</span>
            <span style={{ color: 'rgba(255,255,255,0.4)', fontFamily: 'monospace' }}>AUDIT LOGS</span>
          </div>
          <div style={{ padding: '1.5rem', background: '#0a0a0a', height: '300px', overflowY: 'hidden', display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {auditLoading && <Spinner />}
            {!auditLoading && auditLogs.slice(0, 10).map((log) => (
              <div key={log.id} style={{ display: 'flex', gap: '1rem', borderBottom: '1px dotted #222', paddingBottom: '0.75rem', fontSize: '0.65rem', fontFamily: 'monospace' }}>
                <span style={{ color: 'var(--gold)', minWidth: '120px' }}>[{fmtDate(log.createdAt)}]</span>
                <span style={{ color: log.actionName.includes('DELETE') ? '#ff6b6b' : '#7ca4ff', flex: 1 }}>{log.actionName}</span>
                <span style={{ opacity: 0.4 }}>{log.userEmail}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(380px, 1fr))', gap: '1.5rem' }}>
        <div className="ledger-feed" style={{ marginTop: 0 }}>
          <div className="ledger-feed-header" style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span>JURIDICAL REGISTER</span>
            <span style={{ color: 'rgba(255,255,255,0.4)', fontFamily: 'monospace' }}>AFFAIRES</span>
          </div>
          <div style={{ padding: '1.5rem', background: '#0a0a0a', height: '300px', overflowY: 'hidden' }}>
            {affairesLoading && <Spinner />}
            {!affairesLoading && affaires.slice(0, 5).map((a) => (
              <div key={a.idAffaire} style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem', borderBottom: '2px solid var(--black)', paddingBottom: '0.75rem', marginBottom: '0.75rem' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span className="ledger-row-mono" style={{ color: 'var(--gold)' }}>{a.idAffaire}</span>
                  <span className="status-badge-admin">{a.statut || 'ORDINAIRE'}</span>
                </div>
                <div className="ledger-row-subject" style={{ fontSize: '0.7rem' }}>{a.titre}</div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}
