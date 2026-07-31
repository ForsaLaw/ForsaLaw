import { Award } from 'lucide-react'
import LawyerAgendaSection from './LawyerAgendaSection.jsx'
import LawyerAppointmentsSection from './LawyerAppointmentsSection.jsx'
import LawyerSecuritySection from './LawyerSecuritySection.jsx'
import VerifiedLawyerBadge from '../lawyers/VerifiedLawyerBadge.jsx'

export default function LawyerApprovedView({
  t,
  profile,
  photoBlobUrl,
  canUseAvocatEndpoints,
  onRelogin,
  agendaConfig,
  setAgendaConfig,
  agendaBusy,
  handleSaveAgendaConfig,
  agendaSnapshot,
  handleDeletePlage,
  newPlage,
  setNewPlage,
  handleAddPlage,
  newException,
  setNewException,
  handleDeleteException,
  handleAddException,
  agendaMsg,
  agendaError,
  appointmentsLoading,
  appointments,
  fmtDateTime,
  proposeStart,
  setProposeStart,
  proposeEnd,
  setProposeEnd,
  proposeType,
  setProposeType,
  proposeComment,
  setProposeComment,
  onProposeSlot,
  onCancelAppointment,
  onOpenOnlineRoom,
  onGoVault,
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
      {!canUseAvocatEndpoints && (
        <div className="lawyer-space-banner lawyer-space-banner--warn">
          <p>{t('lawyer_space_relogin_hint')}</p>
          <button type="button" className="lawyer-space-btn-ghost" onClick={onRelogin}>
            {t('lawyer_space_relogin_btn')}
          </button>
        </div>
      )}

      <div className="client-pro-kpis lawyer-space-kpis">
        <div className="client-pro-kpi">
          <span className="client-pro-kpi__label">{t('lawyer_space_kpi_rating')}</span>
          <span className="client-pro-kpi__value">
            {profile.noteMoyenne != null ? profile.noteMoyenne.toFixed(1) : '—'}
          </span>
        </div>
        <div className="client-pro-kpi">
          <span className="client-pro-kpi__label">{t('lawyer_space_kpi_dossiers')}</span>
          <span className="client-pro-kpi__value">{profile.totalDossiers ?? 0}</span>
        </div>
      </div>

      <section className="client-pro-card lawyer-space-section">
        <div className="client-pro-card__head">
          <div className="client-pro-card__icon">
            <Award size={18} />
          </div>
          <div>
            <h2 className="client-pro-card__title">{t('lawyer_space_verified_title')}</h2>
            <p className="client-pro-card__sub">{t('lawyer_space_verified_sub')}</p>
          </div>
        </div>
        <div className="client-pro-card__body lawyer-space-verified-grid">
          <div>
            {profile.profilePhotoPublicUrl && !photoBlobUrl && (
              <div className="lawyer-space-public-photo-wrap">
                <img src={profile.profilePhotoPublicUrl} alt="" className="lawyer-space-public-photo" />
              </div>
            )}
            <p className="lawyer-space-identity-line">
              <strong>{profile.userPrenom} {profile.userNom}</strong>
              <VerifiedLawyerBadge avocat={profile} />
              <span className="lawyer-space-muted"> · {profile.userEmail}</span>
            </p>
            <p>
              {profile.domaineLibelle} · {profile.specialiteLibelle ?? profile.specialite}
            </p>
            <p>
              {profile.ville} · {profile.anneesExperience} {t('lawyer_space_years_short')}
            </p>
            {profile.description && <p className="lawyer-space-desc">{profile.description}</p>}
          </div>
          <div className="lawyer-space-readonly-block">
            <p><strong>{t('lawyer_space_bar_id')}:</strong> {profile.numeroCarteProfessionnelle}</p>
            <p><strong>{t('lawyer_space_cin')}:</strong> {profile.cin}</p>
            <p><strong>{t('lawyer_space_barreau')}:</strong> {profile.barreau}</p>
            {/* Absent des profils anterieurs a V16, ou la colonne est restee NULL : on masque la
                ligne plutot que d'afficher un libelle suivi du vide. */}
            {profile.numeroOnat && (
              <p><strong>{t('lawyer_space_onat')}:</strong> {profile.numeroOnat}</p>
            )}
          </div>
        </div>
      </section>

      {canUseAvocatEndpoints && (
        <>
          <LawyerAgendaSection
            agendaConfig={agendaConfig}
            setAgendaConfig={setAgendaConfig}
            agendaBusy={agendaBusy}
            handleSaveAgendaConfig={handleSaveAgendaConfig}
            agendaSnapshot={agendaSnapshot}
            handleDeletePlage={handleDeletePlage}
            newPlage={newPlage}
            setNewPlage={setNewPlage}
            handleAddPlage={handleAddPlage}
            newException={newException}
            setNewException={setNewException}
            handleDeleteException={handleDeleteException}
            handleAddException={handleAddException}
            agendaMsg={agendaMsg}
            agendaError={agendaError}
          />

          <LawyerAppointmentsSection
            appointmentsLoading={appointmentsLoading}
            appointments={appointments}
            fmtDateTime={fmtDateTime}
            proposeStart={proposeStart}
            setProposeStart={setProposeStart}
            proposeEnd={proposeEnd}
            setProposeEnd={setProposeEnd}
            proposeType={proposeType}
            setProposeType={setProposeType}
            proposeComment={proposeComment}
            setProposeComment={setProposeComment}
            onProposeSlot={onProposeSlot}
            onCancelAppointment={onCancelAppointment}
            onOpenOnlineRoom={onOpenOnlineRoom}
          />

          <LawyerSecuritySection
            t={t}
            onGoVault={onGoVault}
            photoBlobUrl={photoBlobUrl}
            photoBusy={photoBusy}
            photoMsg={photoMsg}
            handlePhoto={handlePhoto}
            pwdCurrent={pwdCurrent}
            setPwdCurrent={setPwdCurrent}
            pwdNew={pwdNew}
            setPwdNew={setPwdNew}
            pwdMsg={pwdMsg}
            pwdBusy={pwdBusy}
            handlePassword={handlePassword}
            deactivateBusy={deactivateBusy}
            handleDeactivate={handleDeactivate}
          />
        </>
      )}
    </>
  )
}
