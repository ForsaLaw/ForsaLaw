import { motion } from 'framer-motion'
import { CODEX_ARTICLES } from './homeData.js'

export default function HomeCodexSection({ fadeUp }) {
  return (
    <section className="home-codex">
      <motion.div className="home-section-header" {...fadeUp}>
        <p className="home-eyebrow">Procédure</p>
        <h2 className="home-section-title">Le Codex de la Procédure</h2>
      </motion.div>

      <div className="home-codex-scroll">
        {CODEX_ARTICLES.map((article, i) => (
          <motion.article
            key={article.roman}
            className="home-codex-article"
            initial={{ opacity: 0, y: 28 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, margin: '-50px' }}
            transition={{ type: 'spring', bounce: 0.4, duration: 0.8, delay: i * 0.1 }}
          >
            <span className="home-codex-roman">{article.roman}</span>
            <div className="home-codex-content">
              <h3 className="home-codex-title">{article.title}</h3>
              <p className="home-codex-body">{article.body}</p>
            </div>
            <div className="home-codex-media" aria-hidden="true">
              <img src={article.image} alt="" />
            </div>
          </motion.article>
        ))}
      </div>
    </section>
  )
}
