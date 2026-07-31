import { motion } from 'framer-motion'
import { Loader2 } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import LawyerApplicationFormSection from '../components/lawyer-space/LawyerApplicationFormSection.jsx'
import LawyerApprovedView from '../components/lawyer-space/LawyerApprovedView.jsx'
import LawyerPendingProfileSection from '../components/lawyer-space/LawyerPendingProfileSection.jsx'
import { useLawyerSpaceData } from '../hooks/useLawyerSpaceData.js'
import '../styles/PlatformSpaces.css'
import '../styles/LawyerSpacePage.css'

export default function LawyerSpacePage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { token, isAuthenticated, logout, refreshUser } = useAuth()
  const {
    domaines,
    domainesError,
    profile,
    noProfile,
    loadError,
    loading,
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
    createBusy,
    createMsg,
    editSpec,
    setEditSpec,
    editYears,
    setEditYears,
    editVille,
    setEditVille,
    editDesc,
    setEditDesc,
    editBusy,
    editError,
    editSaved,
    pwdCurrent,
    setPwdCurrent,
    pwdNew,
    setPwdNew,
    pwdBusy,
    pwdMsg,
    photoBlobUrl,
    photoBusy,
    photoMsg,
    deactivateBusy,
    appointments,
    appointmentsLoading,
    proposeStart,
    setProposeStart,
    proposeEnd,
    setProposeEnd,
    proposeType,
    setProposeType,
    proposeComment,
    setProposeComment,
    agendaSnapshot,
    agendaBusy,
    agendaMsg,
    agendaError,
    agendaConfig,
    setAgendaConfig,
    newPlage,
    setNewPlage,
    newException,
    setNewException,
    canUseAvocatEndpoints,
    selectedDomainRow,
    editDomainRow,
    isApproved,
    isPending,
    isRejected,
    fmtDateTime,
    reloadProfile,
    handleSaveAgendaConfig,
    handleAddPlage,
    handleDeletePlage,
    handleAddException,
    handleDeleteException,
    handleCreate,
    handleEdit,
    handlePassword,
    handlePhoto,
    handleDeactivate,
    handleProposeAppointmentSlot,
    handleCancelAppointment,
    handleOpenOnlineRoom,
    relogin,
  } = useLawyerSpaceData({
    token,
    isAuthenticated,
    refreshUser,
    t,
    navigate,
    logout,
  })

  if (!isAuthenticated || !token) {
    return <Navigate to="/" replace />
  }

  // Admin is now allowed to view Lawyer Space, no strict block.

  return (
    <motion.main
      className="platform-page lawyer-space-page"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
    >
      <p className="platform-eyebrow">{t('lawyer_space_tag')}</p>
      <h1 className="platform-title">{t('lawyer_space_title')}</h1>
      <div className="platform-title-divider" />
      <p className="platform-subtitle">{t('lawyer_space_subtitle')}</p>

      {domainesError && (
        <p className="lawyer-space-banner lawyer-space-banner--error">{domainesError}</p>
      )}
      {loadError && <p className="lawyer-space-banner lawyer-space-banner--error">{loadError}</p>}

      {loading && (
        <div className="lawyer-space-loading">
          <Loader2 className="forsalaw-spin" size={28} />
        </div>
      )}

      {!loading && noProfile && !domaines.length && !domainesError && (
        <div className="lawyer-space-loading">
          <Loader2 className="forsalaw-spin" size={28} />
        </div>
      )}

      {!loading && noProfile && (domaines.length > 0 || domainesError) && (
        <LawyerApplicationFormSection
          t={t}
          domaines={domaines}
          selectedDomainRow={selectedDomainRow}
          createDomain={createDomain}
          setCreateDomain={setCreateDomain}
          createSpec={createSpec}
          setCreateSpec={setCreateSpec}
          createYears={createYears}
          setCreateYears={setCreateYears}
          createVille={createVille}
          setCreateVille={setCreateVille}
          createDesc={createDesc}
          setCreateDesc={setCreateDesc}
          createCarte={createCarte}
          setCreateCarte={setCreateCarte}
          createCin={createCin}
          setCreateCin={setCreateCin}
          createBarreau={createBarreau}
          createOnat={createOnat}
          setCreateOnat={setCreateOnat}
          setCreateBarreau={setCreateBarreau}
          createMsg={createMsg}
          createBusy={createBusy}
          onCreate={handleCreate}
        />
      )}

      {!loading && profile && !isApproved && (
        <LawyerPendingProfileSection
          t={t}
          profile={profile}
          isRejected={isRejected}
          isPending={isPending}
          reloadProfile={reloadProfile}
          domaines={domaines}
          domainesError={domainesError}
          editDomainRow={editDomainRow}
          editSpec={editSpec}
          setEditSpec={setEditSpec}
          editYears={editYears}
          setEditYears={setEditYears}
          editVille={editVille}
          setEditVille={setEditVille}
          editDesc={editDesc}
          setEditDesc={setEditDesc}
          editSaved={editSaved}
          editError={editError}
          editBusy={editBusy}
          onEdit={handleEdit}
        />
      )}

      {!loading && profile && isApproved && (
        <>
          <LawyerApprovedView
            t={t}
            profile={profile}
            photoBlobUrl={photoBlobUrl}
            canUseAvocatEndpoints={canUseAvocatEndpoints}
            onRelogin={relogin}
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
            onProposeSlot={handleProposeAppointmentSlot}
            onCancelAppointment={handleCancelAppointment}
            onOpenOnlineRoom={handleOpenOnlineRoom}
            onGoVault={() => navigate('/cases')}
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
    </motion.main>
  )
}
