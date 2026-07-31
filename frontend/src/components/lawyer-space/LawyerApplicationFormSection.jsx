import { Loader2, Scale } from 'lucide-react'

export default function LawyerApplicationFormSection({
  t,
  domaines,
  selectedDomainRow,
  createDomain,
  setCreateDomain,
  createSpec,
  setCreateSpec,
  createYears,
  setCreateYears,
  createVille,
  setCreateVille,
  createDesc,
  setCreateDesc,
  createCarte,
  setCreateCarte,
  createCin,
  setCreateCin,
  createBarreau,
  createOnat,
  setCreateOnat,
  setCreateBarreau,
  createMsg,
  createBusy,
  onCreate,
}) {
  return (
    <section className="client-pro-card lawyer-space-section">
      <div className="client-pro-card__head">
        <div className="client-pro-card__icon">
          <Scale size={18} />
        </div>
        <div>
          <h2 className="client-pro-card__title">{t('lawyer_space_request_title')}</h2>
          <p className="client-pro-card__sub">{t('lawyer_space_request_sub')}</p>
        </div>
      </div>
      <div className="client-pro-card__body">
        <form className="client-pro-form" onSubmit={onCreate}>
          <div className="client-pro-form__grid">
            <label className="lawyer-space-label">
              <span>{t('lawyer_space_domain')}</span>
              <select
                value={createDomain}
                onChange={(ev) => {
                  setCreateDomain(ev.target.value)
                  setCreateSpec('')
                }}
                required
              >
                {domaines.map((d) => (
                  <option key={d.code} value={d.code}>
                    {d.libelle}
                  </option>
                ))}
              </select>
            </label>
            <label className="lawyer-space-label">
              <span>{t('lawyer_space_specialty')}</span>
              <select
                value={createSpec}
                onChange={(ev) => setCreateSpec(ev.target.value)}
                required
              >
                <option value="">{t('lawyer_space_pick_specialty')}</option>
                {(selectedDomainRow?.specialites ?? []).map((s) => (
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
                value={createYears}
                onChange={(ev) => {
                  const v = parseInt(ev.target.value, 10)
                  setCreateYears(Number.isFinite(v) ? v : 0)
                }}
              />
            </label>
            <label className="lawyer-space-label">
              <span>{t('lawyer_space_city')}</span>
              <input
                value={createVille}
                onChange={(ev) => setCreateVille(ev.target.value)}
                required
                maxLength={100}
              />
            </label>
          </div>

          <label className="lawyer-space-label">
            <span>{t('lawyer_space_description')}</span>
            <textarea
              value={createDesc}
              onChange={(ev) => setCreateDesc(ev.target.value)}
              rows={4}
              maxLength={2000}
            />
          </label>

          <div className="client-pro-form__grid">
            <label className="lawyer-space-label">
              <span>{t('lawyer_space_bar_id')}</span>
              <input
                value={createCarte}
                onChange={(ev) => setCreateCarte(ev.target.value)}
                required
                maxLength={100}
              />
            </label>
            <label className="lawyer-space-label">
              <span>{t('lawyer_space_cin')}</span>
              <input
                value={createCin}
                onChange={(ev) => setCreateCin(ev.target.value)}
                required
                maxLength={50}
              />
            </label>
            <label className="lawyer-space-label lawyer-space-label--span2">
              <span>{t('lawyer_space_barreau')}</span>
              <input
                value={createBarreau}
                onChange={(ev) => setCreateBarreau(ev.target.value)}
                required
                maxLength={100}
              />
            </label>
            <label className="lawyer-space-label lawyer-space-label--span2">
              <span>{t('lawyer_space_onat')}</span>
              <input
                value={createOnat}
                onChange={(ev) => setCreateOnat(ev.target.value)}
                required
                maxLength={100}
              />
              {/* L'utilisateur doit comprendre POURQUOI ce numero est demande : il n'est pas
                  affiche publiquement, il sert a confronter la demande au tableau de l'Ordre. */}
              <small className="lawyer-space-hint">{t('lawyer_space_onat_hint')}</small>
            </label>
          </div>

          {createMsg && <p className="lawyer-space-form-msg lawyer-space-form-msg--err">{createMsg}</p>}
          <button type="submit" className="lawyer-space-btn-primary" disabled={createBusy}>
            {createBusy ? <Loader2 className="forsalaw-spin" size={18} /> : t('lawyer_space_submit_request')}
          </button>
        </form>
      </div>
    </section>
  )
}
