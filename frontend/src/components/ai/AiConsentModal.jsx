import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { AI_CONSENT_VERSION, enregistrerConsentementServeur } from './aiConsent.js'

/**
 * Modale de consentement bloquante affichee avant tout usage de l'assistant IA.
 *
 * Le bouton d'acceptation reste desactive tant que la case n'est pas cochee : l'acceptation
 * doit resulter d'un geste explicite, non d'un clic reflexe sur le bouton par defaut.
 *
 * @param {() => void} props.onAccept  appele apres enregistrement du consentement
 * @param {() => void} props.onRefuse  appele si l'utilisateur refuse (retour en arriere attendu)
 */
export default function AiConsentModal({ onAccept, onRefuse }) {
  const { t } = useTranslation()
  const [caseCochee, setCaseCochee] = useState(false)
  const refDialogue = useRef(null)

  // Le focus part sur la modale : sans cela un lecteur d'ecran resterait sur le contenu
  // sous-jacent, qui est justement ce que la modale doit empecher d'utiliser.
  useEffect(() => {
    refDialogue.current?.focus()
  }, [])

  useEffect(() => {
    const surTouche = (e) => {
      if (e.key === 'Escape') onRefuse?.()
    }
    window.addEventListener('keydown', surTouche)
    return () => window.removeEventListener('keydown', surTouche)
  }, [onRefuse])

  const accepter = () => {
    if (!caseCochee) return
    // Le cache local est ecrit immediatement par enregistrerConsentementServeur, l'appel
    // reseau se poursuit en arriere-plan : l'utilisateur n'attend pas le serveur pour entrer.
    enregistrerConsentementServeur()
    onAccept?.()
  }

  return (
    <div className="ai-consent-overlay" role="presentation">
      <div
        className="ai-consent-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="ai-consent-titre"
        tabIndex={-1}
        ref={refDialogue}
      >
        <h2 id="ai-consent-titre">{t('ai_consent_title')}</h2>

        <p className="ai-consent-lead">{t('ai_consent_intro')}</p>

        <ul className="ai-consent-points">
          <li>{t('ai_consent_point_not_advice')}</li>
          <li>{t('ai_consent_point_demo')}</li>
          <li>{t('ai_consent_point_no_data')}</li>
          <li>{t('ai_consent_point_lawyer')}</li>
        </ul>

        <label className="ai-consent-checkbox">
          <input
            type="checkbox"
            checked={caseCochee}
            onChange={(e) => setCaseCochee(e.target.checked)}
          />
          <span>{t('ai_consent_checkbox')}</span>
        </label>

        <div className="ai-consent-actions">
          <button type="button" className="ai-consent-refuse" onClick={onRefuse}>
            {t('ai_consent_decline')}
          </button>
          <button
            type="button"
            className="ai-consent-accept"
            onClick={accepter}
            disabled={!caseCochee}
          >
            {t('ai_consent_accept')}
          </button>
        </div>

        <p className="ai-consent-version">
          {t('ai_consent_version', { version: AI_CONSENT_VERSION })}
        </p>
      </div>
    </div>
  )
}
