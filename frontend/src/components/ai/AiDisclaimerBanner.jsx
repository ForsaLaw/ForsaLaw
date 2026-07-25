import { useTranslation } from 'react-i18next'
import { AlertTriangleIcon } from 'lucide-react'

/**
 * Avertissement permanent affiche au-dessus de l'assistant IA.
 *
 * Volontairement NON refermable : un utilisateur qui pose une question juridique doit avoir
 * l'avertissement sous les yeux au moment ou il lit la reponse, pas seulement au moment ou il
 * a ouvert la page. C'est la difference entre un avertissement utile et une case cochee.
 */
export default function AiDisclaimerBanner() {
  const { t } = useTranslation()

  return (
    <div className="ai-disclaimer-banner" role="note" aria-live="polite">
      <AlertTriangleIcon size={18} strokeWidth={2.5} aria-hidden="true" />
      <p>
        <strong>{t('ai_disclaimer_title')}</strong> {t('ai_disclaimer_body')}
      </p>
    </div>
  )
}
