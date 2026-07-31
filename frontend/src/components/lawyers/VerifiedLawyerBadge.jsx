import { ShieldCheck } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import '../../styles/VerifiedBadge.css'

/**
 * Badge « Verifie par l'ONAT ».
 *
 * <p>Ne s'affiche QUE si le profil est reellement approuve. La verification est une affirmation
 * de la plateforme sur la qualite professionnelle d'une personne : l'afficher par defaut, ou
 * sur un simple `verifie` desynchronise du statut, reviendrait a cautionner un praticien que
 * personne n'a controle. En l'absence de preuve, on n'affiche rien plutot qu'un badge
 * « en attente », qui serait lu de travers.</p>
 *
 * <p>Les deux champs sont exiges ensemble : le backend les maintient coherents
 * (APPROVED ⇔ verifie=true, cf. AvocatService.assertVerifieCoherentAvecStatut), et exiger les
 * deux fait echouer l'affichage du cote sur si cette coherence venait a se rompre.</p>
 *
 * @param {'sm'|'md'} [props.taille] compact pour les listes, normal pour une fiche
 * @param {boolean} [props.avecLibelle] false pour n'afficher que l'icone (listes denses)
 */
export default function VerifiedLawyerBadge({ avocat, taille = 'md', avecLibelle = true }) {
  const { t } = useTranslation()

  const estVerifie = avocat?.verificationStatus === 'APPROVED' && avocat?.verifie === true
  if (!estVerifie) return null

  const libelle = t('lawyer_verified_onat')

  return (
    <span
      className={`verified-badge verified-badge--${taille}`}
      title={t('lawyer_verified_onat_hint')}
    >
      <ShieldCheck size={taille === 'sm' ? 13 : 15} aria-hidden="true" />
      {avecLibelle
        ? <span className="verified-badge__text">{libelle}</span>
        /* Sans libelle visible, l'information doit rester accessible aux lecteurs d'ecran :
           le title seul n'est pas restitue de maniere fiable. */
        : <span className="sr-only">{libelle}</span>}
    </span>
  )
}
