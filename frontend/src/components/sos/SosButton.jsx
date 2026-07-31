import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import SosArrestModal from './SosArrestModal.jsx'
import { chargerFeatures } from '../../api/features.js'
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
  // null tant que le serveur n'a pas repondu : on n'affiche RIEN pendant ce temps, plutot que
  // de faire apparaitre puis disparaitre un bouton d'urgence.
  const [actif, setActif] = useState(null)

  useEffect(() => {
    let annule = false
    chargerFeatures().then((f) => {
      if (!annule) setActif(Boolean(f?.sosArrest))
    })
    return () => {
      annule = true
    }
  }, [])

  // Le bouton ET la modale disparaissent : tant que le paiement et la mobilisation sont des
  // bouchons, laisser croire qu'un avocat a ete depeche serait plus grave que l'absence de la
  // fonctionnalite. Le serveur refuse d'ailleurs les appels (503) — ceci evite simplement de
  // proposer une porte fermee a quelqu'un dans l'urgence.
  if (!actif) return null

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
