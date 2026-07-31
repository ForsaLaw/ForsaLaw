import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import SosArrestModal from './SosArrestModal.jsx'
import '../../styles/Sos.css'

/**
 * Bouton d'alerte « SOS Arrestation ».
 *
 * Autonome (etat + modale internes) : il peut ainsi etre depose dans n'importe quelle barre de
 * navigation sans faire remonter d'etat au parent.
 *
 * Affiche pour TOUT LE MONDE, connecte ou non — l'endpoint de depot est ouvert, et une
 * arrestation est presque toujours signalee par un proche qui n'a pas de compte.
 */
export default function SosButton() {
  const { t } = useTranslation()
  const [ouvert, setOuvert] = useState(false)

  return (
    <>
      <button
        type="button"
        className="sos-trigger"
        onClick={() => setOuvert(true)}
        title={t('sos_button_title')}
      >
        <span className="sos-trigger-pulse" aria-hidden="true" />
        {t('sos_button')}
      </button>
      {ouvert && <SosArrestModal onClose={() => setOuvert(false)} />}
    </>
  )
}
