import { motion } from 'framer-motion'
import { AlertTriangle, Loader2, Shield, UploadCloud, X } from 'lucide-react'
import { useState } from 'react'
import * as documentsApi from '../../api/documents.js'

export default function UploadDocumentModal({ token, onUploaded, onClose }) {
  const [file, setFile] = useState(null)
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState(null)

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!file) {
      setErr('Veuillez sélectionner un fichier.')
      return
    }
    setBusy(true)
    setErr(null)
    try {
      const dto = await documentsApi.uploadDocument(token, file)
      onUploaded(dto)
    } catch (error) {
      setErr(error?.message || String(error))
    } finally {
      setBusy(false)
    }
  }

  return (
    <motion.div
      className="dossier-modal-overlay"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      onClick={onClose}
    >
      <motion.div
        className="dossier-modal"
        initial={{ y: 30, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        exit={{ y: 30, opacity: 0 }}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="dossier-modal__header">
          <h2>Dépôt Sécurisé</h2>
          <button type="button" className="dossier-modal__close" onClick={onClose}>
            <X size={18} />
          </button>
        </div>
        <form className="dossier-modal__form" onSubmit={handleSubmit}>
          <div style={{ marginBottom: '1rem', color: 'var(--gold)', fontSize: '0.85rem', display: 'flex', gap: '0.5rem', alignItems: 'center', background: 'rgba(212,175,55,0.1)', padding: '1rem', border: '1px solid var(--gold)' }}>
            <Shield size={24} style={{ flexShrink: 0 }} />
            <p>Tout fichier déposé dans le coffre-fort sera horodaté et son intégrité scellée via une empreinte cryptographique SHA-256 inviolable.</p>
          </div>
          <label className="dossier-modal__label">
            <span>Fichier à sceller</span>
            <input
              type="file"
              onChange={(e) => setFile(e.target.files?.[0] || null)}
              required
              disabled={busy}
              style={{ border: '2px solid var(--black)', padding: '0.5rem' }}
            />
          </label>

          {err && (
            <p className="dossier-modal__error">
              <AlertTriangle size={14} /> {err}
            </p>
          )}
          <button type="submit" className="dossier-modal__submit" disabled={busy || !file}>
            {busy ? <Loader2 className="forsalaw-spin" size={16} /> : <UploadCloud size={16} />}
            Sceller et Uploader
          </button>
        </form>
      </motion.div>
    </motion.div>
  )
}
