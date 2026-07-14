import { motion } from 'framer-motion'
import { TRUST_PILLARS } from './homeData.js'

export default function HomeTrustSection({ t, fadeUp }) {
  return (
    <section className="home-trust" aria-labelledby="home-trust-heading">
      <motion.div className="home-section-header" {...fadeUp}>
        <p className="home-eyebrow">{t('home_trust_eyebrow')}</p>
        <h2 id="home-trust-heading" className="home-section-title">
          {t('home_trust_title')}
        </h2>
      </motion.div>
      <motion.figure
        className="home-trust-visual"
        initial={{ opacity: 0, y: 20 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true, margin: '-50px' }}
        transition={{ duration: 0.55 }}
      >
        <img src="/home/home-trust-law.png" alt="Balance, marteau de juge et livre de droit" />
      </motion.figure>
      <ul className="home-trust-grid">
        {TRUST_PILLARS.map((pillar, i) => {
          const Icon = pillar.icon
          return (
            <motion.li
              key={pillar.titleKey}
              className="home-trust-item"
              initial={{ opacity: 0, y: 24 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, margin: '-40px' }}
              transition={{ duration: 0.45, delay: i * 0.07, ease: [0.45, 0, 0.55, 1] }}
            >
              <div className="home-trust-icon" aria-hidden>
                <Icon size={22} strokeWidth={1.4} />
              </div>
              <h3 className="home-trust-item-title">{t(pillar.titleKey)}</h3>
              <p className="home-trust-item-body">{t(pillar.bodyKey)}</p>
            </motion.li>
          )
        })}
      </ul>
    </section>
  )
}
