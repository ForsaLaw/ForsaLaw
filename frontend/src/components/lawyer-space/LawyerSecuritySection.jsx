import { Camera, Key, Loader2, Lock, Trash2 } from 'lucide-react'

export default function LawyerSecuritySection({
  t,
  onGoVault,
  photoBlobUrl,
  photoBusy,
  photoMsg,
  handlePhoto,
  pwdCurrent,
  setPwdCurrent,
  pwdNew,
  setPwdNew,
  pwdMsg,
  pwdBusy,
  handlePassword,
  deactivateBusy,
  handleDeactivate,
}) {
  return (
    <>
      <section className="client-pro-card lawyer-space-section">
        <div className="client-pro-card__head">
          <div className="client-pro-card__icon">
            <Lock size={18} />
          </div>
          <div>
            <h2 className="client-pro-card__title">Coffre-fort Numérique</h2>
            <p className="client-pro-card__sub">Déposez et sécurisez vos documents juridiques via scellement cryptographique SHA-256 inaltéré et horodaté.</p>
          </div>
        </div>
        <div className="client-pro-card__body">
          <div className="lawyer-safe-box-cta">
            <p className="lawyer-safe-box-cta__text">
              Vos documents sont accessibles uniquement par vous. Chaque accès est enregistré dans le journal d'audit.
            </p>
            <button
              type="button"
              className="lawyer-space-btn-primary lawyer-safe-box-cta__btn"
              onClick={onGoVault}
            >
              <Lock size={14} />
              Accéder au Coffre-fort
            </button>
          </div>
        </div>
      </section>

      <section className="client-pro-card lawyer-space-section">
        <div className="client-pro-card__head">
          <div className="client-pro-card__icon">
            <Camera size={18} />
          </div>
          <div>
            <h2 className="client-pro-card__title">{t('lawyer_space_photo_section')}</h2>
            <p className="client-pro-card__sub">{t('lawyer_space_photo_hint')}</p>
          </div>
        </div>
        <div className="client-pro-card__body">
          <div className="client-pro-photo">
            <div className="client-pro-photo__preview">
              {photoBlobUrl ? (
                <img src={photoBlobUrl} alt="" />
              ) : (
                <div className="client-pro-photo__placeholder">—</div>
              )}
              {photoBusy && (
                <div className="lawyer-space-photo-overlay">
                  <Loader2 className="forsalaw-spin" size={22} />
                </div>
              )}
            </div>
            <div className="client-pro-photo__actions">
              <label className="lawyer-space-btn-secondary">
                {t('lawyer_space_photo_choose')}
                <input type="file" accept="image/jpeg,image/png,image/gif,image/webp" hidden onChange={handlePhoto} />
              </label>
              {photoMsg && <p className="lawyer-space-form-msg">{photoMsg}</p>}
            </div>
          </div>
        </div>
      </section>

      <section className="client-pro-card lawyer-space-section">
        <div className="client-pro-card__head">
          <div className="client-pro-card__icon">
            <Key size={18} />
          </div>
          <div>
            <h2 className="client-pro-card__title">{t('lawyer_space_password_section')}</h2>
            <p className="client-pro-card__sub">{t('lawyer_space_password_sub')}</p>
          </div>
        </div>
        <div className="client-pro-card__body">
          <form className="client-pro-form" onSubmit={handlePassword}>
            <div className="client-pro-form__grid">
              <label className="lawyer-space-label">
                <span>{t('lawyer_space_password_current')}</span>
                <input
                  type="password"
                  autoComplete="current-password"
                  value={pwdCurrent}
                  onChange={(ev) => setPwdCurrent(ev.target.value)}
                />
              </label>
              <label className="lawyer-space-label">
                <span>{t('lawyer_space_password_new')}</span>
                <input
                  type="password"
                  autoComplete="new-password"
                  value={pwdNew}
                  onChange={(ev) => setPwdNew(ev.target.value)}
                />
              </label>
            </div>
            {pwdMsg && <p className="lawyer-space-form-msg">{pwdMsg}</p>}
            <button type="submit" className="lawyer-space-btn-primary" disabled={pwdBusy}>
              {pwdBusy ? <Loader2 className="forsalaw-spin" size={18} /> : t('lawyer_space_password_save')}
            </button>
          </form>
        </div>
      </section>

      <section className="client-pro-card client-pro-card--danger lawyer-space-section">
        <div className="client-pro-card__head">
          <div className="client-pro-card__icon client-pro-card__icon--danger">
            <Trash2 size={18} />
          </div>
          <div>
            <h2 className="client-pro-card__title client-pro-card__title--danger">{t('lawyer_space_deactivate_section')}</h2>
            <p className="client-pro-card__sub">{t('lawyer_space_deactivate_hint')}</p>
          </div>
        </div>
        <div className="client-pro-card__body">
          <button
            type="button"
            className="lawyer-space-btn-danger"
            disabled={deactivateBusy}
            onClick={handleDeactivate}
          >
            {deactivateBusy ? <Loader2 className="forsalaw-spin" size={18} /> : t('lawyer_space_deactivate')}
          </button>
        </div>
      </section>
    </>
  )
}
