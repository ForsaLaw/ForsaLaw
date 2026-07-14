import { useState } from 'react'
import { motion } from 'framer-motion'
import {
  FileText,
  Calendar,
  MessageSquare,
  FilePlus2,
  User,
  Loader2,
  Lock,
} from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import * as rdvApi from '../api/rdv.js'
import * as documentsApi from '../api/documents.js'
import ClientAppointmentsTab from '../components/client-space/ClientAppointmentsTab.jsx'
import ClientCasesTab from '../components/client-space/ClientCasesTab.jsx'
import ClientMessagesTab from '../components/client-space/ClientMessagesTab.jsx'
import ClientProfileTab from '../components/client-space/ClientProfileTab.jsx'
import ClientVaultTab from '../components/client-space/ClientVaultTab.jsx'
import { formatApiDate, fmtDateTime, useClientSpaceData } from '../hooks/useClientSpaceData.js'
import '../styles/PlatformSpaces.css'

const VALID_TABS = new Set(['cases', 'appointments', 'messages', 'profile', 'vault'])

export default function ClientSpacePage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const { token, user, isAuthenticated, logout, refreshUser } = useAuth()

  const tabFromUrl = searchParams.get('tab')
  const activeTab = VALID_TABS.has(tabFromUrl) ? tabFromUrl : 'cases'

  const setActiveTab = (key) => {
    setSearchParams(key === 'cases' ? {} : { tab: key })
  }

  const {
    me,
    loadError,
    loadingMe,
    cases,
    casesLoading,
    editNom,
    setEditNom,
    editPrenom,
    setEditPrenom,
    editEmail,
    setEditEmail,
    profileEditing,
    setProfileEditing,
    profileSaving,
    profileMsg,
    setProfileMsg,
    pwdCurrent,
    setPwdCurrent,
    pwdNew,
    setPwdNew,
    pwdSaving,
    pwdMsg,
    notif,
    notifLoading,
    notifSaving,
    notifMsg,
    photoBlobUrl,
    photoUploading,
    photoMsg,
    deleteBusy,
    appointments,
    appointmentsLoading,
    initials,
    displayName,
    reloadAppointments,
    handleSaveProfile,
    handleChangePassword,
    handleSaveNotif,
    handlePhotoChange,
    handleDeleteAccount,
    toggleNotif,
  } = useClientSpaceData({
    token,
    user,
    isAuthenticated,
    activeTab,
    t,
    navigate,
    logout,
    refreshUser,
  })

  const CLIENT_NAV = [
    { key: 'cases', label: t('client_nav_cases'), icon: <FileText size={16} /> },
    { key: 'appointments', label: t('client_nav_appointments'), icon: <Calendar size={16} /> },
    { key: 'messages', label: t('client_nav_messages'), icon: <MessageSquare size={16} /> },
    { key: 'vault', label: 'COFFRE-FORT', icon: <Lock size={16} /> },
    { key: 'profile', label: t('client_nav_profile'), icon: <User size={16} /> },
  ]

  if (!isAuthenticated || !token) {
    return <Navigate to="/" replace />
  }

  if (user?.roleUser !== 'client' && user?.roleUser !== 'admin') {
    return <Navigate to="/" replace />
  }

  return (
    <motion.main
      className="platform-page"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
    >
      <p className="platform-eyebrow">{t('client_space_tag')}</p>
      <h1 className="platform-title">{t('client_space_title')}</h1>
      <div className="platform-title-divider" />
      <p className="platform-subtitle">{t('client_space_subtitle')}</p>

      {loadError && (
        <p className="client-banner-error" style={{ color: '#ff8a80', marginBottom: '1rem', fontSize: '0.85rem' }}>
          {loadError}
        </p>
      )}

      <div className="dossier-layout">
        {/* THE SEAL HEADER */}
        <header className="dossier-seal-header">
          <div className="dossier-seal-avatar">
            {photoBlobUrl ? (
              <img src={photoBlobUrl} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
            ) : (
              initials
            )}
            {loadingMe && (
              <div style={{ position: 'absolute', background: 'rgba(0,0,0,0.6)', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <Loader2 className="forsalaw-spin" size={24} />
              </div>
            )}
          </div>
          <div className="dossier-seal-info">
            <h2 className="dossier-seal-name">{displayName}</h2>
            <div className="dossier-seal-meta">
              <span>{me?.email ?? user?.email}</span>
              <span>·</span>
              <span className="role-badge client">{(me?.roleUser ?? user?.roleUser ?? 'CLIENT').toString().toUpperCase()}</span>
              <span>·</span>
              <span>INSERIT LE {formatApiDate(me?.dateCreation)}</span>
            </div>
          </div>
          <div>
            <button type="button" className="quick-action-btn" onClick={() => navigate('/cases')} style={{ padding:'0.75rem 1.5rem', marginBottom:'0.5rem', background: 'var(--gold)', color:'var(--black)' }}>
              <FilePlus2 size={16} /> NOUVEAU DOSSIER
            </button>
            <button type="button" className="quick-action-btn" onClick={() => logout()} style={{ padding:'0.75rem 1.5rem', border: '2px solid rgba(255,107,107,0.5)', color: '#ff6b6b' }}>
               QUITTER LE PALAIS
            </button>
          </div>
        </header>

        {/* FOLDER TABS */}
        <nav className="judicial-registry-tabs">
          {CLIENT_NAV.map((item, idx) => (
            <button
              key={item.key}
              type="button"
              className={`judicial-tab ${activeTab === item.key ? 'active' : ''}`}
              onClick={() => setActiveTab(item.key)}
            >
              <span className="judicial-tab-code">DOC. 0{idx + 1}</span>
              <h4 className="judicial-tab-title">{item.label}</h4>
            </button>
          ))}
        </nav>

        {/* LEDGER CONTENT */}
        <div className="dossier-main-realm">
          {activeTab === 'cases' && (
            <ClientCasesTab cases={cases} casesLoading={casesLoading} navigate={navigate} />
          )}

          {activeTab === 'vault' && (
            <ClientVaultTab navigate={navigate} />
          )}

          {activeTab === 'appointments' && (
            <ClientAppointmentsTab
              token={token}
              appointments={appointments}
              appointmentsLoading={appointmentsLoading}
              fmtDateTime={fmtDateTime}
              rdvApi={rdvApi}
              reloadAppointments={reloadAppointments}
            />
          )}

          {activeTab === 'messages' && (
            <ClientMessagesTab t={t} navigate={navigate} />
          )}

          {activeTab === 'profile' && (
            <ClientProfileTab
              t={t}
              me={me}
              formatApiDate={formatApiDate}
              photoBlobUrl={photoBlobUrl}
              initials={initials}
              handlePhotoChange={handlePhotoChange}
              photoUploading={photoUploading}
              photoMsg={photoMsg}
              profileEditing={profileEditing}
              setProfileEditing={setProfileEditing}
              profileSaving={profileSaving}
              handleSaveProfile={handleSaveProfile}
              editPrenom={editPrenom}
              setEditPrenom={setEditPrenom}
              editNom={editNom}
              setEditNom={setEditNom}
              editEmail={editEmail}
              setEditEmail={setEditEmail}
              setProfileMsg={setProfileMsg}
              profileMsg={profileMsg}
              pwdCurrent={pwdCurrent}
              setPwdCurrent={setPwdCurrent}
              pwdNew={pwdNew}
              setPwdNew={setPwdNew}
              handleChangePassword={handleChangePassword}
              pwdSaving={pwdSaving}
              pwdMsg={pwdMsg}
              notifLoading={notifLoading}
              notif={notif}
              toggleNotif={toggleNotif}
              handleSaveNotif={handleSaveNotif}
              notifSaving={notifSaving}
              notifMsg={notifMsg}
              deleteBusy={deleteBusy}
              handleDeleteAccount={handleDeleteAccount}
            />
          )}
        </div>
      </div>
    </motion.main>
  )
}
