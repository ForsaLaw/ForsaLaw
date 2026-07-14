export default function WitnessRegistryFooter({ onNavigate }) {
  return (
    <footer className="witness-registry">
      <div className="registry-header">
        <div className="registry-header-inner">
          <span className="registry-header-rule" />
          <span className="registry-header-label">REGISTRE DES TÉMOINS</span>
          <span className="registry-header-rule" />
        </div>
        <div className="registry-header-ticker">
          <div className="registry-ticker-track">
            <span>JURIDICTION NUMÉRIQUE</span>
            <span className="rtick-sep">·</span>
            <span>LEX EST REX</span>
            <span className="rtick-sep">·</span>
            <span>AUDI ALTERAM PARTEM</span>
            <span className="rtick-sep">·</span>
            <span>FIAT JUSTITIA</span>
            <span className="rtick-sep">·</span>
            <span>IN NOMINE LEGIS</span>
            <span className="rtick-sep">·</span>
            <span>FORSALAW © 2026</span>
            <span className="rtick-sep">·</span>
            <span>JURIDICTION NUMÉRIQUE</span>
            <span className="rtick-sep">·</span>
            <span>LEX EST REX</span>
            <span className="rtick-sep">·</span>
            <span>AUDI ALTERAM PARTEM</span>
            <span className="rtick-sep">·</span>
            <span>FIAT JUSTITIA</span>
            <span className="rtick-sep">·</span>
            <span>IN NOMINE LEGIS</span>
            <span className="rtick-sep">·</span>
            <span>FORSALAW © 2026</span>
            <span className="rtick-sep">·</span>
          </div>
        </div>
      </div>

      <div className="registry-container">
        <div className="registry-folder">
          <div className="registry-tab">
            <span className="tab-code">DOC. 01</span>
            <h4 className="tab-title">AUDIENCE</h4>
          </div>
          <div className="registry-content">
            <div className="registry-content-inner">
              <p className="registry-desc">Sollicitez une audience avec le Palais.</p>
              <a href="mailto:contact@forsalaw.tn" className="registry-link">contact@forsalaw.tn</a>
              <span className="registry-detail">+216 71 000 000</span>
            </div>
          </div>
        </div>

        <div className="registry-folder">
          <div className="registry-tab">
            <span className="tab-code">DOC. 02</span>
            <h4 className="tab-title">PRÉCÉDENTS</h4>
          </div>
          <div className="registry-content">
            <div className="registry-content-inner">
              <ul className="registry-grid">
                <li><button onClick={() => onNavigate?.('about')}>Le Projet</button></li>
                <li><button onClick={() => onNavigate?.('faq')}>Protocole</button></li>
                <li><button onClick={() => onNavigate?.('privacy')}>Confidentialité</button></li>
                <li><button onClick={() => onNavigate?.('terms')}>Législation</button></li>
              </ul>
            </div>
          </div>
        </div>

        <div className="registry-folder">
          <div className="registry-tab">
            <span className="tab-code">DOC. 03</span>
            <h4 className="tab-title">SCEAUX</h4>
          </div>
          <div className="registry-content">
            <div className="registry-content-inner">
              <div className="registry-socials">
                <a href="#" className="social-pill">LINKEDIN</a>
                <a href="#" className="social-pill">TWITTER</a>
                <a href="#" className="social-pill">FACEBOOK</a>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div className="registry-bottom-bar">
        <p className="registry-copyright">F O R S A L A W · ⚖ · LA JUSTICE EST DIGITALE · © 2026</p>
      </div>
    </footer>
  )
}
