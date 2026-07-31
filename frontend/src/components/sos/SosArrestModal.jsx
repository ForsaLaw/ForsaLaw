import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { signalerArrestation, consulterSignalement } from '../../api/sos.js'
import '../../styles/Sos.css'

/** Date/heure locale au format attendu par un <input type="datetime-local">. */
function maintenantLocal() {
  const d = new Date()
  d.setMinutes(d.getMinutes() - d.getTimezoneOffset())
  return d.toISOString().slice(0, 16)
}

/**
 * Saisie d'un signalement d'arrestation.
 *
 * Le formulaire est prerempli sur l'heure courante et reduit au strict necessaire : il est
 * rempli dans l'urgence, souvent par un proche bouleverse, et chaque champ supplementaire est
 * une occasion d'abandonner en cours de route.
 */
export default function SosArrestModal({ onClose }) {
  const { t } = useTranslation()

  const [nomDetenu, setNomDetenu] = useState('')
  const [lieuArrestation, setLieuArrestation] = useState('')
  const [dateHeureArrestation, setDateHeureArrestation] = useState(maintenantLocal)
  const [contactUrgence, setContactUrgence] = useState('')
  const [details, setDetails] = useState('')

  const [envoiEnCours, setEnvoiEnCours] = useState(false)
  const [erreur, setErreur] = useState(null)
  const [signalement, setSignalement] = useState(null)

  const premierChampRef = useRef(null)
  const sondageRef = useRef(null)

  useEffect(() => {
    premierChampRef.current?.focus()
  }, [])

  // Suivi de l'avancement (paiement puis mobilisation) apres l'envoi. Le sondage s'arrete de
  // lui-meme des que la mobilisation aboutit ou echoue : inutile d'interroger indefiniment.
  useEffect(() => {
    if (!signalement?.id) return undefined

    const termine = (s) => s.statutDispatch === 'DISPATCHED' || s.statutDispatch === 'FAILED'
    if (termine(signalement)) return undefined

    sondageRef.current = setInterval(async () => {
      try {
        const maj = await consulterSignalement(signalement.id)
        setSignalement(maj)
        if (termine(maj)) clearInterval(sondageRef.current)
      } catch {
        // Le signalement EST enregistre ; un suivi qui echoue ne doit pas alarmer l'utilisateur.
        clearInterval(sondageRef.current)
      }
    }, 1500)

    return () => clearInterval(sondageRef.current)
  }, [signalement])

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (envoiEnCours) return

    setEnvoiEnCours(true)
    setErreur(null)
    try {
      const cree = await signalerArrestation({
        nomDetenu: nomDetenu.trim(),
        lieuArrestation: lieuArrestation.trim(),
        // <input datetime-local> ne fournit pas les secondes : le backend attend un LocalDateTime.
        dateHeureArrestation: `${dateHeureArrestation}:00`,
        contactUrgence: contactUrgence.trim(),
        details: details.trim() || null,
      })
      setSignalement(cree)
    } catch (err) {
      setErreur(err.message)
    } finally {
      setEnvoiEnCours(false)
    }
  }

  const etiquetteDispatch = () => {
    if (signalement.statutDispatch === 'DISPATCHED') return t('sos_status_dispatched')
    if (signalement.statutDispatch === 'FAILED') return t('sos_status_dispatch_failed')
    if (signalement.statutPaiement === 'PAID') return t('sos_status_paid')
    return t('sos_status_pending')
  }

  return (
    <div className="sos-overlay" role="dialog" aria-modal="true" aria-labelledby="sos-titre">
      <div className="sos-modal">
        <div className="sos-modal-header">
          <h2 id="sos-titre">{t('sos_title')}</h2>
          <button type="button" className="sos-close" onClick={onClose} aria-label={t('sos_close')}>
            ×
          </button>
        </div>

        {signalement ? (
          <div className="sos-confirmation">
            <p className="sos-confirmation-ref">
              {t('sos_reference')} <strong>{signalement.id}</strong>
            </p>
            <p className="sos-confirmation-etat">{etiquetteDispatch()}</p>
            {signalement.statutDispatch !== 'DISPATCHED'
              && signalement.statutDispatch !== 'FAILED' && (
                <div className="sos-progress" aria-hidden="true"><span /></div>
              )}
            <p className="sos-note">{t('sos_keep_reference')}</p>
            <button type="button" className="sos-submit" onClick={onClose}>
              {t('sos_close')}
            </button>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="sos-form">
            <p className="sos-lead">{t('sos_lead')}</p>

            {erreur && <div className="sos-erreur" role="alert">{erreur}</div>}

            <label className="sos-label" htmlFor="sos-nom">{t('sos_field_name')}</label>
            <input
              id="sos-nom"
              ref={premierChampRef}
              className="sos-input"
              value={nomDetenu}
              onChange={(e) => setNomDetenu(e.target.value)}
              required
              maxLength={255}
            />

            <label className="sos-label" htmlFor="sos-lieu">{t('sos_field_place')}</label>
            <input
              id="sos-lieu"
              className="sos-input"
              value={lieuArrestation}
              onChange={(e) => setLieuArrestation(e.target.value)}
              required
              maxLength={255}
            />

            <label className="sos-label" htmlFor="sos-date">{t('sos_field_when')}</label>
            <input
              id="sos-date"
              type="datetime-local"
              className="sos-input"
              value={dateHeureArrestation}
              onChange={(e) => setDateHeureArrestation(e.target.value)}
              required
            />

            <label className="sos-label" htmlFor="sos-contact">{t('sos_field_contact')}</label>
            <input
              id="sos-contact"
              type="tel"
              className="sos-input"
              value={contactUrgence}
              onChange={(e) => setContactUrgence(e.target.value)}
              placeholder="+216 ..."
              required
            />

            <label className="sos-label" htmlFor="sos-details">{t('sos_field_details')}</label>
            <textarea
              id="sos-details"
              className="sos-input sos-textarea"
              value={details}
              onChange={(e) => setDetails(e.target.value)}
              rows={3}
              maxLength={4000}
            />

            <button type="submit" className="sos-submit" disabled={envoiEnCours}>
              {envoiEnCours ? t('sos_submitting') : t('sos_submit')}
            </button>
            <p className="sos-note">{t('sos_disclaimer')}</p>
          </form>
        )}
      </div>
    </div>
  )
}
