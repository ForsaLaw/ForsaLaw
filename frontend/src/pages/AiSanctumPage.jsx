import { useState, useEffect, useRef } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { SendIcon } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import AiConsentModal from '../components/ai/AiConsentModal.jsx'
import AiDisclaimerBanner from '../components/ai/AiDisclaimerBanner.jsx'
import { aConsentiVersionCourante } from '../components/ai/aiConsent.js'
import { apiFetch, parseApiError } from '../api/client.js'
import { lireFluxSse } from '../api/sse.js'
import '../styles/AiSanctum.css'

// Effet de frappe pour le SEUL message scripte (l'introduction de Fellawra) : un texte fixe,
// connu a l'avance, revele caractere par caractere. Les reponses reelles de l'assistant ne
// passent PAS par ce hook : elles arrivent deja progressivement via le flux SSE, les reveler
// une deuxieme fois ferait rejouer une animation sur un texte deja affiche (voir handleSubmit).
const useTypewriter = (text, startTyping, speed = 30) => {
  const [displayedText, setDisplayedText] = useState('')
  const [isTyping, setIsTyping] = useState(false)

  const skipTyping = () => {
    setDisplayedText(text)
    setIsTyping(false)
  }

  useEffect(() => {
    if (!startTyping || !text) return

    setDisplayedText('')
    setIsTyping(true)
    let i = 0

    const intervalId = setInterval(() => {
      setDisplayedText(text.slice(0, i + 1))
      i++
      if (i >= text.length) {
        clearInterval(intervalId)
        setIsTyping(false)
      }
    }, speed)

    return () => clearInterval(intervalId)
  }, [text, startTyping, speed])

  return { displayedText, isTyping, skipTyping }
}

const AiSanctumPage = () => {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [consentementDonne, setConsentementDonne] = useState(() => aConsentiVersionCourante())
  const [history, setHistory] = useState([])

  // Phase intro (scriptee, typewriter) : uniquement avant la premiere question posee.
  const [currentThought, setCurrentThought] = useState(t('sanctum_intro'))
  const [dialogueTrigger, setDialogueTrigger] = useState(true)
  const { displayedText, isTyping, skipTyping } = useTypewriter(currentThought, dialogueTrigger, 25)

  // Phase reponse reelle (flux SSE) : des la premiere question, toutes les reponses de
  // Fellawra passent par ici, jamais plus par le typewriter ci-dessus.
  const [phaseIntroTerminee, setPhaseIntroTerminee] = useState(false)
  const [reponseEnCours, setReponseEnCours] = useState('')
  const [streamingActif, setStreamingActif] = useState(false)
  const [erreurAssistant, setErreurAssistant] = useState(null)

  const [userInput, setUserInput] = useState('')
  const [waitingForResponse, setWaitingForResponse] = useState(false)

  const historyEndRef = useRef(null)

  useEffect(() => {
    historyEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [history])

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!userInput.trim() || isTyping || streamingActif) return

    // Le dernier message de Fellawra (intro ou reponse precedente) part dans l'historique.
    const dernierMessage = phaseIntroTerminee ? reponseEnCours : currentThought
    if (dernierMessage) {
      setHistory((prev) => [...prev, { sender: 'System', text: dernierMessage }])
    }

    const userMessage = userInput.trim()
    setHistory((prev) => [...prev, { sender: 'User', text: userMessage }])

    setUserInput('')
    setCurrentThought('')
    setDialogueTrigger(false)
    setPhaseIntroTerminee(true)
    setReponseEnCours('')
    setErreurAssistant(null)
    setWaitingForResponse(true)

    // Pas de signal d'AbortController ici : combine a credentials:'include' sur cet
    // environnement, il fait echouer l'envoi du cookie d'authentification (constate : la
    // meme requete passe de 200 a 403/anonyme des qu'un signal est attache, sans autre
    // changement). Le flux se termine proprement cote serveur si le client se deconnecte
    // (voir AiChatService.surJeton, qui capture l'IOException).
    try {
      const res = await apiFetch('/api/ai/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ question: userMessage, tier: 1 }),
      })

      if (!res.ok) {
        setWaitingForResponse(false)
        setErreurAssistant(await parseApiError(res))
        return
      }

      let premierJetonRecu = false
      await lireFluxSse(res, ({ event, data }) => {
        if (event === 'jeton') {
          if (!premierJetonRecu) {
            premierJetonRecu = true
            setWaitingForResponse(false)
            setStreamingActif(true)
          }
          // Jeton transporte en chaine JSON (voir AiChatService.encoderJeton) : le SSE mange
          // sinon les espaces en debut de jeton et casse les trames sur un saut de ligne.
          let morceau
          try {
            morceau = JSON.parse(data)
          } catch {
            morceau = data
          }
          setReponseEnCours((prev) => prev + morceau)
        } else if (event === 'erreur') {
          setWaitingForResponse(false)
          setStreamingActif(false)
          setErreurAssistant(data)
        }
      })
    } catch (erreur) {
      setErreurAssistant(t('sanctum_error') || erreur.message)
    } finally {
      setWaitingForResponse(false)
      setStreamingActif(false)
    }
  }

  const handleBoxClick = () => {
    if (isTyping) {
      skipTyping()
    }
  }

  const texteAffiche = phaseIntroTerminee ? reponseEnCours : displayedText
  const enCoursDeFrappe = phaseIntroTerminee ? streamingActif : isTyping

  return (
    <div className="sanctum-page">
      {!consentementDonne && (
        <AiConsentModal
          onAccept={() => setConsentementDonne(true)}
          onRefuse={() => navigate(-1)}
        />
      )}

      <AiDisclaimerBanner />

      <div className="sanctum-sprite-container">
        <motion.img
          src="/fellawra.png"
          alt="Fellawra Oracle"
          className="sanctum-sprite"
          initial={{ opacity: 0, scale: 0.9 }}
          animate={{
            opacity: 1,
            scale: 1,
            filter: waitingForResponse ? 'drop-shadow(0 0 20px rgba(212, 175, 55, 0.8))' : 'drop-shadow(0 0 0px rgba(212, 175, 55, 0))'
          }}
          transition={{
            duration: waitingForResponse ? 1.5 : 1.5,
            ease: "easeOut",
            repeat: waitingForResponse ? Infinity : 0,
            repeatType: "reverse"
          }}
        />
      </div>

      <motion.div
        className="dialogue-interface"
        initial={{ y: 100, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        transition={{ delay: 0.5, type: 'spring', stiffness: 50 }}
      >

        {history.length > 0 && (
          <div className="dialogue-history">
            {history.map((msg, idx) => (
              <motion.div
                key={idx}
                className={`history-bubble ${msg.sender === 'System' ? 'ai' : 'user'}`}
                initial={{ opacity: 0, x: msg.sender === 'User' ? 20 : -20 }}
                animate={{ opacity: 1, x: 0 }}
              >
                <div className="history-sender">{msg.sender === 'System' ? 'Fellawra' : t('sanctum_you')}</div>
                {msg.text}
              </motion.div>
            ))}
            <div ref={historyEndRef} />
          </div>
        )}

        {erreurAssistant && (
          <div className="dialogue-box dialogue-box-erreur" role="alert">
            <div className="dialogue-name-tab">FELLAWRA</div>
            <div className="dialogue-text">{erreurAssistant}</div>
          </div>
        )}

        <AnimatePresence>
          {!erreurAssistant && (texteAffiche || waitingForResponse) && (
            <motion.div
              className="dialogue-box"
              onClick={handleBoxClick}
              initial={{ opacity: 0, scaleY: 0.8 }}
              animate={{ opacity: 1, scaleY: 1 }}
              exit={{ opacity: 0, scaleY: 0.8 }}
              transition={{ duration: 0.2 }}
            >
              <div className="dialogue-name-tab">FELLAWRA</div>

              <div className="dialogue-text">
                {waitingForResponse ? (
                  <span style={{ color: 'rgba(255,255,255,0.5)' }}>{t('sanctum_thinking')}</span>
                ) : (
                  <>
                    {texteAffiche}
                    {enCoursDeFrappe && <span className="dialogue-cursor" />}
                  </>
                )}
              </div>

              {!enCoursDeFrappe && !waitingForResponse && <div className="dialogue-continue" />}
            </motion.div>
          )}
        </AnimatePresence>

        <form className="sanctum-input-container" style={{ border: '4px solid var(--black)', boxShadow: '12px 12px 0px 0px var(--black)', background: 'var(--white)', padding: 0 }} onSubmit={handleSubmit}>
          <input
            type="text"
            className="sanctum-input"
            style={{ color: 'var(--black)', background: 'transparent' }}
            placeholder={t('sanctum_placeholder')}
            value={userInput}
            onChange={(e) => setUserInput(e.target.value)}
            disabled={enCoursDeFrappe || waitingForResponse}
            autoFocus
          />
          <button
            type="submit"
            className="sanctum-submit"
            style={{
              background: 'var(--gold)',
              borderLeft: '4px solid var(--black)',
              color: 'var(--black)',
              padding: '0 2rem',
              opacity: (!userInput.trim() || enCoursDeFrappe || waitingForResponse) ? 0.3 : 1,
              transition: 'all 0.2s'
            }}
            disabled={!userInput.trim() || enCoursDeFrappe || waitingForResponse}
          >
            <SendIcon size={24} strokeWidth={2.5} />
          </button>
        </form>

      </motion.div>
    </div>
  )
}

export default AiSanctumPage
