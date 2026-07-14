import { motion } from 'framer-motion'
import { Loader2 } from 'lucide-react'

const STATUT_DISPLAY = {
  OUVERTE: { label: 'OUVERTE', cls: 'open' },
  EN_COURS: { label: 'EN COURS', cls: 'in-progress' },
  RESOLUE: { label: 'RESOLUE', cls: 'closed' },
  FERMEE: { label: 'FERMEE', cls: 'closed' },
}

export default function ClientCasesTab({ cases, casesLoading, navigate }) {
  return (
    <div className="ledger-feed">
      <div className="ledger-feed-header">
        REGISTRE DES AFFAIRES - {cases.length} ENTRIES
      </div>
      {casesLoading && (
        <div style={{ padding: '1rem', color: 'var(--gold)' }}>
          <Loader2 className="forsalaw-spin" size={18} />
        </div>
      )}
      {!casesLoading && cases.length === 0 && (
        <div style={{ padding: '1rem', opacity: 0.6, fontSize: '0.82rem' }}>
          Aucune reclamation pour le moment.
          <button
            type="button"
            style={{ marginLeft: '0.75rem', color: 'var(--gold)', background: 'none', border: 'none', cursor: 'pointer', textDecoration: 'underline', fontSize: 'inherit' }}
            onClick={() => navigate('/cases')}
          >
            Creer une reclamation
          </button>
        </div>
      )}
      {!casesLoading && cases.map((c, i) => {
        const { label, cls } = STATUT_DISPLAY[c.statut] || { label: c.statut, cls: 'open' }
        return (
          <motion.div
            key={c.id}
            className="ledger-row"
            initial={{ opacity: 0, x: 12 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ delay: i * 0.07 }}
            style={{ cursor: 'pointer' }}
            onClick={() => navigate('/cases')}
          >
            <div className="ledger-row-mono">ID: {c.id}</div>
            <div>
              <div className="ledger-row-subject">{c.titre}</div>
              <div className="ledger-row-mono" style={{ marginTop: '4px' }}>CAT: {c.categorie || '-'}</div>
            </div>
            <span className={`status-badge-admin ${cls}`}>{label}</span>
          </motion.div>
        )
      })}
    </div>
  )
}
