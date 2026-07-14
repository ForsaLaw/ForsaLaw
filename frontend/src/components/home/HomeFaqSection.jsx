import { useId, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { Calendar, ChevronDown, FileText, Shield, Sparkles, UserCog, Users } from 'lucide-react'

const FAQ_ITEMS = [
  { code: '01', icon: Users, qKey: 'home_faq_q1', aKey: 'home_faq_a1' },
  { code: '02', icon: Calendar, qKey: 'home_faq_q2', aKey: 'home_faq_a2' },
  { code: '03', icon: FileText, qKey: 'home_faq_q3', aKey: 'home_faq_a3' },
  { code: '04', icon: Shield, qKey: 'home_faq_q4', aKey: 'home_faq_a4' },
  { code: '05', icon: Sparkles, qKey: 'home_faq_q5', aKey: 'home_faq_a5' },
  { code: '06', icon: UserCog, qKey: 'home_faq_q6', aKey: 'home_faq_a6' },
]

export default function HomeFaqSection({ t, fadeUp }) {
  const [openFaq, setOpenFaq] = useState(null)
  const faqBaseId = useId()

  return (
    <section className="home-faq" aria-labelledby="home-faq-heading">
      <motion.div className="home-section-header" {...fadeUp}>
        <p className="home-eyebrow">{t('home_faq_eyebrow')}</p>
        <h2 id="home-faq-heading" className="home-section-title">
          {t('home_faq_title')}
        </h2>
        <p className="home-faq-subtitle">{t('home_faq_subtitle')}</p>
      </motion.div>

      <div className="home-faq-list" role="list">
        {FAQ_ITEMS.map((item, i) => {
          const Icon = item.icon
          const isOpen = openFaq === i
          const panelId = `${faqBaseId}-panel-${i}`
          const triggerId = `${faqBaseId}-trigger-${i}`
          return (
            <motion.div
              key={item.qKey}
              className={`home-faq-row${isOpen ? ' home-faq-row--open' : ''}`}
              role="listitem"
              initial={{ opacity: 0, y: 16 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, margin: '-40px' }}
              transition={{ type: 'spring', stiffness: 120, damping: 16, delay: i * 0.06 }}
            >
              <div className={`home-faq-question-cell${isOpen ? ' home-faq-question-cell--open' : ''}`}>
                <button
                  type="button"
                  id={triggerId}
                  className={`home-faq-trigger${isOpen ? ' home-faq-trigger--open' : ''}`}
                  aria-expanded={isOpen}
                  aria-controls={panelId}
                  onClick={() => setOpenFaq(isOpen ? null : i)}
                >
                  <span className="home-faq-code">{item.code}</span>
                  <div className="home-faq-icon-wrap">
                    <Icon size={22} strokeWidth={1.5} aria-hidden />
                  </div>
                  <span className="home-faq-question">{t(item.qKey)}</span>
                  <ChevronDown
                    className={`home-faq-chevron${isOpen ? ' home-faq-chevron--open' : ''}`}
                    size={22}
                    strokeWidth={1.75}
                    aria-hidden
                  />
                </button>
              </div>

              <div className="home-faq-answer-slot" aria-hidden={!isOpen}>
                <AnimatePresence initial={false} mode="wait">
                  {isOpen && (
                    <motion.div
                      key={panelId}
                      id={panelId}
                      role="region"
                      aria-labelledby={triggerId}
                      className="home-faq-pixel-wrap"
                      initial={{ opacity: 0, scale: 0.96, x: 8 }}
                      animate={{ opacity: 1, scale: 1, x: 0 }}
                      exit={{ opacity: 0, scale: 0.98, x: 6 }}
                      transition={{ duration: 0.24, ease: [0.45, 0, 0.55, 1] }}
                    >
                      <div className="home-faq-bubble-cluster">
                        <div className="home-faq-pixel-bridge" aria-hidden>
                          <div className="home-faq-pixel-tail-stack">
                            <div className="home-faq-pixel-dots-row">
                              <span className="home-faq-pixel-dot" />
                              <span className="home-faq-pixel-dot" />
                              <span className="home-faq-pixel-dot" />
                            </div>
                            <span className="home-faq-pixel-seg home-faq-pixel-seg--5" />
                            <span className="home-faq-pixel-seg home-faq-pixel-seg--4" />
                            <span className="home-faq-pixel-seg home-faq-pixel-seg--3" />
                            <span className="home-faq-pixel-seg home-faq-pixel-seg--2" />
                            <span className="home-faq-pixel-seg home-faq-pixel-seg--1" />
                          </div>
                        </div>
                        <div className="home-faq-pixel-bubble">
                          <p className="home-faq-pixel-text" dir="auto">
                            {t(item.aKey)}
                          </p>
                        </div>
                      </div>
                    </motion.div>
                  )}
                </AnimatePresence>
              </div>
            </motion.div>
          )
        })}
      </div>
    </section>
  )
}
