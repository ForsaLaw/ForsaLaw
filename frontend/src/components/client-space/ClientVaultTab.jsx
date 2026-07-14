import { Lock } from 'lucide-react'

export default function ClientVaultTab({ navigate }) {
  return (
    <div className="ledger-feed">
      <div className="ledger-feed-header" style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
        <Lock size={14} style={{ color: 'var(--gold)' }} />
        COFFRE-FORT NUMERIQUE
      </div>
      <div style={{ padding: '2rem', display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', padding: '1.5rem', border: '1px solid rgba(212,175,55,0.25)', background: 'rgba(212,175,55,0.04)' }}>
          <Lock size={32} style={{ color: 'var(--gold)', flexShrink: 0 }} />
          <div>
            <div style={{ fontFamily: 'monospace', fontSize: '0.8rem', color: 'var(--gold)', marginBottom: '0.4rem' }}>DEPOT SECURISE SHA-256</div>
            <p style={{ fontSize: '0.8rem', opacity: 0.7, margin: 0 }}>
              Tous vos documents sont horodates et scelles cryptographiquement. Chaque telechargement ou verification est enregistre dans le journal d'audit.
            </p>
          </div>
        </div>
        <button
          type="button"
          className="quick-action-btn"
          style={{ alignSelf: 'flex-start', background: 'var(--gold)', color: 'var(--black)', padding: '0.75rem 2rem' }}
          onClick={() => navigate('/cases')}
        >
          <Lock size={14} style={{ display: 'inline', marginRight: '0.5rem' }} />
          Acceder au Coffre-fort
        </button>
      </div>
    </div>
  )
}
