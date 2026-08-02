import { motion } from 'framer-motion'
import { useParams, Link } from 'react-router-dom'
import { ScrollText, ShieldCheck, Building2, AlertTriangle } from 'lucide-react'
import '../styles/Legal.css'

/**
 * Pages legales : conditions d'utilisation, confidentialite, mentions legales.
 *
 * POURQUOI CES PAGES SONT DES SQUELETTES.
 *
 * Le site n'en comportait aucune, dans aucune des trois langues, alors qu'il traite des
 * signalements d'arrestation, des pieces d'identite d'avocats et des documents de dossiers.
 * L'absence etait donc a combler. Mais le texte qui engage — duree de conservation, base
 * legale du traitement, limitation de responsabilite sur une reponse d'IA, coordonnees du
 * responsable de traitement declare a l'INPDP — ne peut pas etre redige par defaut : mal
 * ecrit, il vaut moins que rien, car il donne aux utilisateurs une garantie que l'exploitant
 * n'a jamais examinee et qu'il devra pourtant tenir.
 *
 * Ce qui est livre ici : la structure, les routes, les liens de pied de page, le plan des
 * rubriques que la loi 2004-63 et le RGPD imposent de couvrir, et un avertissement visible
 * tant que le contenu n'est pas valide. Ce qui reste a faire : faire rediger chaque rubrique
 * par un juriste, puis retirer <AvertissementBrouillon />.
 */

const RUBRIQUES = {
  conditions: {
    icone: ScrollText,
    titre: "Conditions générales d'utilisation",
    sections: [
      ["Objet du service", "Ce que ForsaLaw met à disposition, et ce qu'il ne fait pas."],
      ["Comptes et éligibilité", "Conditions d'inscription des clients et des avocats."],
      ["Assistant juridique automatisé",
        "Point central : l'assistant restitue des extraits de textes et peut se tromper. "
        + "Il ne constitue pas une consultation juridique et ne remplace pas un avocat. "
        + "La portée exacte de cette limitation doit être arrêtée par un juriste."],
      ["Mise en relation avec un avocat",
        "ForsaLaw met en relation ; la relation client-avocat, ses honoraires et sa "
        + "responsabilité relèvent de l'avocat et du barreau dont il dépend."],
      ["Obligations de l'utilisateur", "Usages interdits, exactitude des informations fournies."],
      ["Responsabilité et disponibilité", "Limites de responsabilité, interruptions de service."],
      ["Résiliation", "Suppression du compte à l'initiative de l'utilisateur ou de la plateforme."],
      ["Droit applicable", "Droit tunisien, juridiction compétente."],
    ],
  },
  confidentialite: {
    icone: ShieldCheck,
    titre: 'Politique de confidentialité',
    sections: [
      ["Responsable du traitement",
        "Identité et coordonnées, et référence de la déclaration auprès de l'INPDP "
        + "(Instance Nationale de Protection des Données Personnelles), exigée par la "
        + "loi organique n° 2004-63."],
      ["Données collectées",
        "Compte (nom, e-mail, téléphone), pièces d'identité professionnelles des avocats "
        + "(CIN, carte professionnelle, numéro ONAT), documents de dossiers, messages, "
        + "questions posées à l'assistant, journaux de connexion."],
      ["Finalités et base légale", "Pourquoi chaque catégorie est traitée, et à quel titre."],
      ["Durées de conservation",
        "À définir par catégorie. Un point est déjà tranché techniquement : les sauvegardes "
        + "sont conservées 30 jours, ce qui borne le délai réel d'un effacement."],
      ["Destinataires et sous-traitants",
        "Hébergeur, service d'envoi d'e-mails. L'assistant juridique fonctionne sur un modèle "
        + "auto-hébergé : les questions posées ne sont transmises à aucun fournisseur tiers."],
      ["Vos droits",
        "Accès, rectification, effacement, opposition. L'effacement est déjà implémenté : "
        + "il anonymise le compte de façon irréversible (voir docs/ERASURE_POLICY.md)."],
      ["Cookies", "Cookie d'authentification et jeton anti-CSRF ; aucun cookie publicitaire."],
      ["Sécurité", "Chiffrement en transit, journal d'audit inaltérable, contrôle des accès."],
    ],
  },
  mentions: {
    icone: Building2,
    titre: 'Mentions légales',
    sections: [
      ["Éditeur", "Dénomination sociale, forme juridique, siège, registre de commerce."],
      ["Directeur de la publication", "Nom et qualité."],
      ["Hébergeur", "Raison sociale et adresse de l'hébergeur."],
      ["Contact", "Adresse électronique et téléphone."],
      ["Propriété intellectuelle",
        "Statut des contenus de la plateforme. Les textes juridiques diffusés sont des textes "
        + "officiels ; leurs sources doivent être créditées."],
    ],
  },
}

function AvertissementBrouillon() {
  return (
    <div className="legal-warning" role="alert">
      <AlertTriangle size={18} aria-hidden="true" />
      <p>
        <strong>Document non finalisé.</strong> Le plan ci-dessous liste les points que ce
        document doit couvrir. Il n&apos;a pas encore été rédigé ni validé par un juriste et
        n&apos;a, en l&apos;état, aucune valeur contractuelle.
      </p>
    </div>
  )
}

export default function LegalPage() {
  const { rubrique } = useParams()
  const contenu = RUBRIQUES[rubrique] ?? RUBRIQUES.conditions
  const Icone = contenu.icone

  return (
    <motion.main
      className="legal-page"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
    >
      <header className="legal-header">
        <Icone size={26} aria-hidden="true" />
        <h1>{contenu.titre}</h1>
      </header>

      <nav className="legal-nav" aria-label="Documents légaux">
        {Object.entries(RUBRIQUES).map(([cle, r]) => (
          <Link
            key={cle}
            to={`/legal/${cle}`}
            className={`legal-nav-link${cle === (rubrique ?? 'conditions') ? ' active' : ''}`}
          >
            {r.titre}
          </Link>
        ))}
      </nav>

      <AvertissementBrouillon />

      <ol className="legal-sections">
        {contenu.sections.map(([titre, resume]) => (
          <li key={titre}>
            <h2>{titre}</h2>
            <p>{resume}</p>
          </li>
        ))}
      </ol>
    </motion.main>
  )
}
