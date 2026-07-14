import { motion } from 'framer-motion'
import { Send } from 'lucide-react'

export default function AdminWhatsAppSection({
  waStatus,
  waLoading,
  handleGenerateWaQr,
  Spinner,
  waQr,
  handleSendWaTest,
  testPhone,
  setTestPhone,
  testMsg,
  setTestMsg,
  busy,
}) {
  return (
    <div className="admin-module-pane">
      <div className="admin-stats-row" style={{ marginBottom: '2rem' }}>
        <motion.div className="admin-stat-card" style={{ border: waStatus?.connected ? '2px solid #558b2f' : '2px solid #b71c1c' }}>
          <span className="admin-stat-label">Statut du Bridge</span>
          <span className={`admin-stat-value ${waStatus?.connected ? 'gold' : 'alert'}`}>
            {waLoading ? 'SONDAGE...' : (waStatus?.connected ? 'EN LIGNE' : 'HORS LIGNE')}
          </span>
          <span className="admin-stat-delta">{waStatus?.message || 'Etat du reseau local'}</span>
        </motion.div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', flex: 1 }}>
          {!waStatus?.connected && (
            <button className="brutal-btn" onClick={handleGenerateWaQr} disabled={waLoading}>
              {waLoading ? <Spinner /> : 'Generer un QR Code de session'}
            </button>
          )}
          {waQr && (
            <div style={{ padding: '1rem', background: '#fff', alignSelf: 'flex-start' }}>
              <img src={waQr} alt="WhatsApp QR Code" style={{ width: 200, height: 200 }} />
            </div>
          )}
        </div>
      </div>

      <div className="admin-panel" style={{ padding: '2rem' }}>
        <div className="platform-section-header">
          <span className="platform-section-title">Essai de Notification WhatsApp</span>
        </div>
        <form onSubmit={handleSendWaTest} style={{ display: 'flex', gap: '1rem', marginTop: '1rem' }}>
          <input className="sanctum-input" style={{ width: '200px' }} placeholder="+216XXYYYZZZ" value={testPhone} onChange={(e) => setTestPhone(e.target.value)} required />
          <input className="sanctum-input" style={{ flex: 1 }} placeholder="Message de test" value={testMsg} onChange={(e) => setTestMsg(e.target.value)} />
          <button className="new-case-btn" type="submit" disabled={busy.testWa || !testPhone}>
            {busy.testWa ? <Spinner /> : <Send size={16} style={{ display: 'inline' }} />}
            Faire Sonner WhatsApp
          </button>
        </form>
      </div>
    </div>
  )
}
