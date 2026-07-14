import { motion } from 'framer-motion'
import { ABOUT_BLOCKS } from './homeData.js'

export default function HomeAboutSection({ t, fadeUp }) {
  return (
    <section className="home-about" aria-labelledby="home-about-heading">
      <motion.div className="home-section-header" {...fadeUp}>
        <p className="home-eyebrow">{t('home_about_eyebrow')}</p>
        <h2 id="home-about-heading" className="home-section-title">
          {t('home_about_title')}
        </h2>
      </motion.div>

      <motion.p
        className="home-about-lead"
        initial={{ opacity: 0, y: 28 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true, margin: '-50px' }}
        transition={{ duration: 0.65, ease: [0.45, 0, 0.55, 1], delay: 0.06 }}
      >
        {t('home_about_lead')}
      </motion.p>
      <motion.figure
        className="home-about-hero"
        initial={{ opacity: 0, y: 22 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true, margin: '-40px' }}
        transition={{ duration: 0.55 }}
      >
        <img src="/home/home-hero-palais.jpg" alt="Facade du palais de justice" />
      </motion.figure>

      <motion.div
        className="home-about-seal"
        aria-hidden="true"
        initial={{ opacity: 0, scale: 0.96 }}
        whileInView={{ opacity: 1, scale: 1 }}
        viewport={{ once: true, margin: '-30px' }}
        transition={{ duration: 0.5, delay: 0.12 }}
      >
        <span className="home-about-seal-line" />
        <span className="home-about-seal-icon">⚖</span>
        <span className="home-about-seal-line" />
      </motion.div>

      <div className="home-about-grid">
        {ABOUT_BLOCKS.map((block, i) => {
          const Icon = block.icon
          return (
            <motion.article
              key={block.titleKey}
              className="home-about-card"
              initial={{ opacity: 0, y: 32 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, margin: '-45px' }}
              transition={{
                type: 'spring',
                stiffness: 110,
                damping: 15,
                delay: i * 0.1,
              }}
            >
              <div className="home-about-card-icon">
                <Icon size={26} strokeWidth={1.35} aria-hidden />
              </div>
              <h3 className="home-about-card-title">{t(block.titleKey)}</h3>
              <p className="home-about-card-body">{t(block.bodyKey)}</p>
            </motion.article>
          )
        })}
      </div>
    </section>
  )
}
