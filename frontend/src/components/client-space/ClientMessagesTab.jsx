import { MessageSquare } from 'lucide-react'

export default function ClientMessagesTab({ t, navigate }) {
  return (
    <div className="ledger-feed">
      <div className="ledger-feed-header">
        {t('client_nav_messages')}
      </div>
      <div style={{ padding: '2rem 1.5rem' }}>
        <p className="ledger-row-mono" style={{ marginBottom: '1.5rem' }}>{t('client_msg_notice')}</p>
        <button
          type="button"
          className="tbl-btn approve"
          onClick={() => navigate('/inbox')}
          style={{ display: 'inline-flex', fontSize: '0.7rem', padding: '0.6rem 1rem' }}
        >
          <MessageSquare size={16} style={{ marginRight: '8px' }} />
          OUVRIR LA MESSAGERIE
        </button>
      </div>
    </div>
  )
}
