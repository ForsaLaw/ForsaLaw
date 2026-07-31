import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { apiFetch } from '../../api/client.js'
import VerifiedLawyerBadge from '../lawyers/VerifiedLawyerBadge.jsx'

/**
 * Carte de recommandation affichee sous la reponse de l'assistant, quand un domaine a pu etre
 * identifie.
 *
 * <p>Le domaine vient du meme referentiel que les profils avocats ({@code DomaineJuridique}) :
 * la carte n'a donc pas a traduire ni deviner quoi que ce soit, elle interroge directement
 * /api/avocats/match.</p>
 *
 * <p>Ne s'affiche PAS tant que la requete n'a pas abouti : une carte vide sous une reponse
 * juridique suggererait qu'il n'existe aucun avocat, alors que le reseau peut simplement avoir
 * echoue.</p>
 */
export default function AvocatsRecommandesCard({ domaine }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [avocats, setAvocats] = useState(null)
  const [echec, setEchec] = useState(false)

  useEffect(() => {
    if (!domaine?.code) return undefined
    let annule = false

    apiFetch(`/api/avocats/match?domaine=${encodeURIComponent(domaine.code)}&limite=3`)
      .then((res) => (res.ok ? res.json() : Promise.reject(new Error('indisponible'))))
      .then((liste) => {
        if (!annule) setAvocats(liste)
      })
      .catch(() => {
        if (!annule) setEchec(true)
      })

    return () => {
      annule = true
    }
  }, [domaine])

  // Echec reseau : on n'affiche rien plutot qu'un message d'erreur. La recommandation est un
  // complement ; signaler sa panne parasiterait la reponse juridique, qui, elle, est arrivee.
  if (echec || avocats === null) return null

  return (
    <div className="avocats-reco">
      <div className="avocats-reco-entete">
        <span className="avocats-reco-titre">{t('sanctum_lawyers_title')}</span>
        <span className="avocats-reco-domaine">
          {t('sanctum_lawyers_domain')} <strong>{domaine.libelle}</strong>
        </span>
      </div>

      {avocats.length === 0 ? (
        <p className="avocats-reco-vide">{t('sanctum_lawyers_empty')}</p>
      ) : (
        <ul className="avocats-reco-liste">
          {avocats.map((a) => (
            <li key={a.id} className="avocats-reco-item">
              <div className="avocats-reco-nom">
                {a.userPrenom} {a.userNom}
                {/* Badge partage plutot qu'une coche locale : la coche s'appuyait sur le seul
                    champ `verifie`, sans verifier le statut d'approbation. */}
                <VerifiedLawyerBadge avocat={a} taille="sm" avecLibelle={false} />
              </div>
              <div className="avocats-reco-meta">
                {a.specialiteLibelle}
                {a.ville ? ` · ${a.ville}` : ''}
                {a.anneesExperience > 0 ? ` · ${a.anneesExperience} ans` : ''}
              </div>
            </li>
          ))}
        </ul>
      )}

      <button type="button" className="avocats-reco-lien" onClick={() => navigate('/lawyers')}>
        {t('sanctum_lawyers_see_all')}
      </button>
    </div>
  )
}
