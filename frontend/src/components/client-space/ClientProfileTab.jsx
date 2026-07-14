import { Bell, Camera, Loader2, Shield, Trash2, User } from 'lucide-react'

export default function ClientProfileTab({
  t,
  me,
  formatApiDate,
  photoBlobUrl,
  initials,
  handlePhotoChange,
  photoUploading,
  photoMsg,
  profileEditing,
  setProfileEditing,
  profileSaving,
  handleSaveProfile,
  editPrenom,
  setEditPrenom,
  editNom,
  setEditNom,
  editEmail,
  setEditEmail,
  setProfileMsg,
  profileMsg,
  pwdCurrent,
  setPwdCurrent,
  pwdNew,
  setPwdNew,
  handleChangePassword,
  pwdSaving,
  pwdMsg,
  notifLoading,
  notif,
  toggleNotif,
  handleSaveNotif,
  notifSaving,
  notifMsg,
  deleteBusy,
  handleDeleteAccount,
}) {
  return (
    <div className="client-pro">
      <header className="client-pro__intro">
        <p className="client-pro__eyebrow">{t('client_space_tag')}</p>
        <h2 className="client-pro__title">{t('client_nav_profile')}</h2>
        <p className="client-pro__lede">{t('client_profile_intro')}</p>
      </header>

      <section className="client-pro-card client-pro-card--accent">
        <div className="client-pro-card__head">
          <div className="client-pro-card__title-wrap">
            <Shield size={18} className="client-pro-card__icon" aria-hidden />
            <div>
              <h3 className="client-pro-card__title">{t('client_profile_info')}</h3>
              <p className="client-pro-card__sub">{t('client_profile_info_sub')}</p>
            </div>
          </div>
        </div>
        <div className="client-pro-card__body">
          <div className="client-pro-kpis">
            <div className="client-pro-kpi">
              <span className="client-pro-kpi__label">{t('client_profile_status')}</span>
              <span className={`client-pro-pill ${me?.actif ? 'client-pro-pill--ok' : 'client-pro-pill--off'}`}>
                {me?.actif ? t('client_profile_active_yes') : t('client_profile_active_no')}
              </span>
            </div>
            <div className="client-pro-kpi">
              <span className="client-pro-kpi__label">{t('client_member_since')}</span>
              <span className="client-pro-kpi__value">{formatApiDate(me?.dateCreation)}</span>
            </div>
          </div>
        </div>
      </section>

      <section className="client-pro-card">
        <div className="client-pro-card__head">
          <div className="client-pro-card__title-wrap">
            <Camera size={18} className="client-pro-card__icon" aria-hidden />
            <div>
              <h3 className="client-pro-card__title">{t('client_photo_section')}</h3>
              <p className="client-pro-card__sub">{t('client_photo_hint')}</p>
            </div>
          </div>
        </div>
        <div className="client-pro-card__body">
          <div className="client-pro-photo">
            <div className="client-pro-photo__preview" aria-hidden>
              {photoBlobUrl ? <img src={photoBlobUrl} alt="" /> : <span className="client-pro-photo__placeholder">{initials}</span>}
            </div>
            <div className="client-pro-photo__actions">
              <label className="client-pro-btn client-pro-btn--primary">
                <input type="file" accept="image/jpeg,image/png,image/gif,image/webp" onChange={handlePhotoChange} disabled={photoUploading} />
                {photoUploading ? <Loader2 className="forsalaw-spin" size={16} /> : <Camera size={16} />}
                {t('client_photo_choose')}
              </label>
              {photoMsg && <p className="client-pro-feedback">{photoMsg}</p>}
            </div>
          </div>
        </div>
      </section>

      <section className="client-pro-card">
        <div className="client-pro-card__head client-pro-card__head--split">
          <div className="client-pro-card__title-wrap">
            <User size={18} className="client-pro-card__icon" aria-hidden />
            <div>
              <h3 className="client-pro-card__title">{t('client_profile_identity')}</h3>
              <p className="client-pro-card__sub">{t('client_profile_identity_sub')}</p>
            </div>
          </div>
          {!profileEditing ? (
            <button type="button" className="client-pro-btn client-pro-btn--sm client-pro-btn--goldline" onClick={() => setProfileEditing(true)}>
              {t('client_profile_edit')}
            </button>
          ) : null}
        </div>
        <div className="client-pro-card__body">
          <form className="client-pro-form" onSubmit={handleSaveProfile}>
            <div className="client-pro-form__grid">
              <label className="client-pro-field">
                <span className="client-pro-field__label">{t('client_profile_fn')}</span>
                <input value={editPrenom} onChange={(e) => setEditPrenom(e.target.value)} disabled={!profileEditing || profileSaving} autoComplete="given-name" />
              </label>
              <label className="client-pro-field">
                <span className="client-pro-field__label">{t('client_profile_ln')}</span>
                <input value={editNom} onChange={(e) => setEditNom(e.target.value)} disabled={!profileEditing || profileSaving} autoComplete="family-name" />
              </label>
            </div>
            <label className="client-pro-field client-pro-field--full">
              <span className="client-pro-field__label">{t('client_profile_email')}</span>
              <input type="email" value={editEmail} onChange={(e) => setEditEmail(e.target.value)} disabled={!profileEditing || profileSaving} autoComplete="email" />
            </label>
            {profileEditing && (
              <div className="client-pro-form__actions">
                <button type="submit" className="client-pro-btn client-pro-btn--primary" disabled={profileSaving}>
                  {profileSaving ? <Loader2 className="forsalaw-spin" size={16} /> : null}
                  {t('client_profile_save')}
                </button>
                <button
                  type="button"
                  className="client-pro-btn client-pro-btn--ghost"
                  onClick={() => {
                    setProfileEditing(false)
                    setEditNom(me?.nom ?? '')
                    setEditPrenom(me?.prenom ?? '')
                    setEditEmail(me?.email ?? '')
                    setProfileMsg(null)
                  }}
                  disabled={profileSaving}
                >
                  {t('client_profile_cancel')}
                </button>
              </div>
            )}
            {profileMsg && <p className="client-pro-feedback">{profileMsg}</p>}
          </form>
        </div>
      </section>

      <section className="client-pro-card">
        <div className="client-pro-card__head">
          <div className="client-pro-card__title-wrap">
            <Shield size={18} className="client-pro-card__icon" aria-hidden />
            <div>
              <h3 className="client-pro-card__title">{t('client_password_section')}</h3>
              <p className="client-pro-card__sub">{t('client_password_sub')}</p>
            </div>
          </div>
        </div>
        <div className="client-pro-card__body">
          <form className="client-pro-form" onSubmit={handleChangePassword}>
            <div className="client-pro-form__grid">
              <label className="client-pro-field">
                <span className="client-pro-field__label">{t('client_password_current')}</span>
                <input type="password" value={pwdCurrent} onChange={(e) => setPwdCurrent(e.target.value)} autoComplete="current-password" />
              </label>
              <label className="client-pro-field">
                <span className="client-pro-field__label">{t('client_password_new')}</span>
                <input type="password" value={pwdNew} onChange={(e) => setPwdNew(e.target.value)} autoComplete="new-password" />
              </label>
            </div>
            <div className="client-pro-form__actions">
              <button type="submit" className="client-pro-btn client-pro-btn--primary" disabled={pwdSaving}>
                {pwdSaving ? <Loader2 className="forsalaw-spin" size={16} /> : null}
                {t('client_password_save')}
              </button>
            </div>
            {pwdMsg && <p className="client-pro-feedback">{pwdMsg}</p>}
          </form>
        </div>
      </section>

      <section className="client-pro-card">
        <div className="client-pro-card__head">
          <div className="client-pro-card__title-wrap">
            <Bell size={18} className="client-pro-card__icon" aria-hidden />
            <div>
              <h3 className="client-pro-card__title">{t('client_notif_section')}</h3>
              <p className="client-pro-card__sub">{t('client_notif_sub')}</p>
            </div>
          </div>
        </div>
        <div className="client-pro-card__body">
          {notifLoading && (
            <div className="client-pro-loading">
              <Loader2 className="forsalaw-spin" size={22} />
            </div>
          )}
          {notif && (
            <>
              <ul className="client-pro-notif-list">
                {[
                  ['emailRdvDemandeRecue', t('client_notif_demand')],
                  ['emailRdvCreneauPropose', t('client_notif_slot')],
                  ['emailRdvRappelJ1', t('client_notif_j1')],
                  ['emailRdvRappelH1', t('client_notif_h1')],
                  ['emailRdvAnnulation', t('client_notif_cancel')],
                ].map(([key, label]) => (
                  <li key={key}>
                    <label className="client-pro-notif-row">
                      <span className="client-pro-notif-row__text">{label}</span>
                      <input type="checkbox" className="client-pro-notif-row__check" checked={Boolean(notif[key])} onChange={() => toggleNotif(key)} />
                    </label>
                  </li>
                ))}
              </ul>
              <div className="client-pro-form__actions">
                <button type="button" className="client-pro-btn client-pro-btn--primary" onClick={handleSaveNotif} disabled={notifSaving}>
                  {notifSaving ? <Loader2 className="forsalaw-spin" size={16} /> : null}
                  {t('client_notif_save')}
                </button>
              </div>
            </>
          )}
          {notifMsg && <p className="client-pro-feedback">{notifMsg}</p>}
        </div>
      </section>

      <section className="client-pro-card client-pro-card--danger">
        <div className="client-pro-card__head">
          <div className="client-pro-card__title-wrap">
            <Trash2 size={18} className="client-pro-card__icon client-pro-card__icon--danger" aria-hidden />
            <div>
              <h3 className="client-pro-card__title client-pro-card__title--danger">{t('client_delete_section')}</h3>
              <p className="client-pro-card__sub">{t('client_delete_hint')}</p>
            </div>
          </div>
        </div>
        <div className="client-pro-card__body">
          <button type="button" className="client-pro-btn client-pro-btn--danger" onClick={handleDeleteAccount} disabled={deleteBusy}>
            {deleteBusy ? <Loader2 className="forsalaw-spin" size={16} /> : null}
            {t('client_delete_account')}
          </button>
        </div>
      </section>
    </div>
  )
}
