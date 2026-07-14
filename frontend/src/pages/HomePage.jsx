import { motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import HeroScale from '../components/HeroScale'
import HomeAboutSection from '../components/home/HomeAboutSection.jsx'
import HomeCodexSection from '../components/home/HomeCodexSection.jsx'
import HomeFaqSection from '../components/home/HomeFaqSection.jsx'
import HomeTrustSection from '../components/home/HomeTrustSection.jsx'
import WitnessRegistryFooter from '../components/home/WitnessRegistryFooter.jsx'
import '../styles/HomePage.css'
import '../styles/FooterRegistry.css'

const fadeUp = {
  initial: { opacity: 0, y: 32 },
  whileInView: { opacity: 1, y: 0 },
  viewport: { once: true, margin: '-60px' },
  transition: { duration: 0.6, ease: [0.45, 0, 0.55, 1] },
}

export default function HomePage({ onNavigate }) {
  const { t } = useTranslation()

  return (
    <motion.main
      className="home-page"
      initial={{ opacity: 1 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.35 }}
    >
      {/* ═══════════════════════════════════════════════════════
          SECTION I — THE GRAND ATRIUM
          Full viewport. The Scale of Fate is the only gateway.
      ═══════════════════════════════════════════════════════ */}
      <section className="home-atrium">
        <div className="home-atrium-bg" aria-hidden="true" />
        <div className="home-atrium-inner">
          <motion.div
            className="home-atrium-headline"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.2, duration: 0.7 }}
          >
            <div className="home-brand-block" aria-label="ForsaLaw brand statement">
              <img src="/fellawra.png" alt="ForsaLaw logo" className="home-brand-block__logo" />
              <p className="home-brand-block__slogan">ForsaLaw — Comprenez vos droits, trouvez votre avocat.</p>
            </div>
          </motion.div>
          <motion.div
            initial={{ opacity: 0, scale: 0.97 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ delay: 0.4, duration: 0.8 }}
            className="home-atrium-scale"
          >
            <HeroScale onNavigate={onNavigate} />
          </motion.div>
        </div>

        {/* ── THE GREAT LEDGER (Live Activity Ticker) ── */}
        <div className="great-ledger">
          <div className="great-ledger-track">
            {/* Doubled for seamless scrolling */}
            <span className="great-ledger-text">
              <span className="gl-hilite">⚖ DOSSIER #4022 SCELLÉ SOUS L'AUTORITÉ DE FELLAWRA</span> · 4 NOUVEAUX AVOCATS HABILITÉS · <span className="gl-hilite">PLAIDOIRIE PÉNALE ARCHIVÉE</span> · AUDIENCE EN DIRECT DANS LA PLACE PUBLIQUE · <span className="gl-hilite">RÉSOLUTION DU DOSSIER #3991</span> · 
            </span>
            <span className="great-ledger-text">
              <span className="gl-hilite">⚖ DOSSIER #4022 SCELLÉ SOUS L'AUTORITÉ DE FELLAWRA</span> · 4 NOUVEAUX AVOCATS HABILITÉS · <span className="gl-hilite">PLAIDOIRIE PÉNALE ARCHIVÉE</span> · AUDIENCE EN DIRECT DANS LA PLACE PUBLIQUE · <span className="gl-hilite">RÉSOLUTION DU DOSSIER #3991</span> · 
            </span>
          </div>
        </div>
      </section>

      {/* ── THE LOWER REALM: Framed by Side Pillars starting from here ── */}
      <div className="home-lower-realm">

      {/* ═══════════════════════════════════════════════════════
          TRUST — Pourquoi nous (sans chiffres ni logos tant qu’absents)
      ═══════════════════════════════════════════════════════ */}
      <HomeTrustSection t={t} fadeUp={fadeUp} />

      {/* ═══════════════════════════════════════════════════════
          SECTION — WHO WE ARE (before Chambers)
      ═══════════════════════════════════════════════════════ */}
      <HomeAboutSection t={t} fadeUp={fadeUp} />

      {/* ═══════════════════════════════════════════════════════
          SECTION II — FAQ (toggle answers, no navigation)
      ═══════════════════════════════════════════════════════ */}
      <HomeFaqSection t={t} fadeUp={fadeUp} />

      {/* ═══════════════════════════════════════════════════════
          SECTION III — THE SACRED CODEX
          Three articles. Typography-driven. No photos.
      ═══════════════════════════════════════════════════════ */}
      <HomeCodexSection fadeUp={fadeUp} />

      </div> {/* End of home-lower-realm (Pillars stop here) */}

      {/* ── THE WITNESS REGISTRY (The Interactive Footer) ── */}
      <WitnessRegistryFooter onNavigate={onNavigate} />
    </motion.main>
  )
}
