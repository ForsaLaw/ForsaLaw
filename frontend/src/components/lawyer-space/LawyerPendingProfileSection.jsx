import { Award, Loader2, RefreshCw } from 'lucide-react'

function statusPillClass(status) {
  if (status === 'APPROVED') return 'lawyer-space-pill lawyer-space-pill--ok'
  if (status === 'REJECTED') return 'lawyer-space-pill lawyer-space-pill--bad'
  return 'lawyer-space-pill lawyer-space-pill--wait'
}

export default function LawyerPendingProfileSection({
  t,
  profile,
  isRejected,
  isPending,
  reloadProfile,
  domaines,
  domainesError,
  editDomainRow,
  editSpec,
  setEditSpec,
  editYears,
  setEditYears,
  editVille,
  setEditVille,
  editDesc,
  setEditDesc,
  editSaved,
  editError,
  editBusy,
  onEdit,
}) {
  return (
    <>
      <div className={`lawyer-space-status ${isRejected ? 'lawyer-space-status--bad' : ''}`}>
        <div className="lawyer-space-status__row">
          <span className={statusPillClass(profile.verificationStatus)}>{profile.verificationStatus}</span>
          <button type="button" className="lawyer-space-btn-ghost" onClick={reloadProfile}>
            <RefreshCw size={16} />
            {t('lawyer_space_refresh')}
          </button>
        </div>
        <p className="lawyer-space-status__comment">{profile.verificationComment}</p>
        {isPending && <p className="lawyer-space-status__hint">{t('lawyer_space_pending_hint')}</p>}
        {isRejected && <p className="lawyer-space-status__hint">{t('lawyer_space_rejected_hint')}</p>}
      </div>

      {domaines.length === 0 && !domainesError && (
        <div className="lawyer-space-loading">
          <Loader2 className="forsalaw-spin" size={28} />
        </div>
      )}

      {(domaines.length > 0 || domainesError) && (
        <section className="client-pro-card lawyer-space-section">
          <div className="client-pro-card__head">
            <div className="client-pro-card__icon">
              <Award size={18} />
            </div>
            <div>
              <h2 className="client-pro-card__title">{t('lawyer_space_update_request')}</h2>
              <p className="client-pro-card__sub">{t('lawyer_space_update_request_sub')}</p>
            </div>
          </div>
          <div className="client-pro-card__body">
            <form className="client-pro-form" onSubmit={onEdit}>
              <p className="lawyer-space-muted">
                <strong>{t('lawyer_space_domain')}:</strong> {profile.domaineLibelle ?? profile.domaine}
              </p>
              <div className="client-pro-form__grid">
                <label className="lawyer-space-label">
                  <span>{t('lawyer_space_specialty')}</span>
                  <select
                    value={editSpec}
                    onChange={(ev) => setEditSpec(ev.target.value)}
                    required
                  >
                    {(editDomainRow?.specialites ?? []).map((s) => (
                      <option key={s.code} value={s.code}>
                        {s.libelle}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="lawyer-space-label">
                  <span>{t('lawyer_space_years')}</span>
                  <input
                    type="number"
                    min={0}
                    value={editYears}
                    onChange={(ev) => {
                      const v = parseInt(ev.target.value, 10)
                      setEditYears(Number.isFinite(v) ? v : 0)
                    }}
                  />
                </label>
                <label className="lawyer-space-label">
                  <span>{t('lawyer_space_city')}</span>
                  <input
                    value={editVille}
                    onChange={(ev) => setEditVille(ev.target.value)}
                    maxLength={100}
                  />
                </label>
              </div>
              <label className="lawyer-space-label">
                <span>{t('lawyer_space_description')}</span>
                <textarea
                  value={editDesc}
                  onChange={(ev) => setEditDesc(ev.target.value)}
                  rows={4}
                  maxLength={2000}
                />
              </label>
              <div className="lawyer-space-readonly-block">
                <p>
                  <strong>{t('lawyer_space_bar_id')}:</strong> {profile.numeroCarteProfessionnelle}
                </p>
                <p>
                  <strong>{t('lawyer_space_cin')}:</strong> {profile.cin}
                </p>
                <p>
                  <strong>{t('lawyer_space_barreau')}:</strong> {profile.barreau}
                </p>
              </div>
              {editSaved && <p className="lawyer-space-form-msg">{t('lawyer_space_saved')}</p>}
              {editError && <p className="lawyer-space-form-msg lawyer-space-form-msg--err">{editError}</p>}
              <button type="submit" className="lawyer-space-btn-primary" disabled={editBusy}>
                {editBusy ? <Loader2 className="forsalaw-spin" size={18} /> : t('lawyer_space_save_changes')}
              </button>
            </form>
          </div>
        </section>
      )}
    </>
  )
}
