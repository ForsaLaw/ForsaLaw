import { useState } from 'react'
import { motion } from 'framer-motion'
import {
  UserCog,
  BadgeCheck,
  ClipboardList,
  MessagesSquare,
  ShieldAlert,
  Search,
  LayoutGrid,
  Loader2,
  AlertTriangle,
  FileText,
  Calendar,
  Lock,
  Smartphone,
} from 'lucide-react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import AdminAffairesSection from '../components/admin-space/AdminAffairesSection.jsx'
import AdminAuditSection from '../components/admin-space/AdminAuditSection.jsx'
import AdminAvocatsSection from '../components/admin-space/AdminAvocatsSection.jsx'
import AdminDocumentsSection from '../components/admin-space/AdminDocumentsSection.jsx'
import AdminMessengerSection from '../components/admin-space/AdminMessengerSection.jsx'
import PageHeader from '../components/PageHeader'
import AdminRendezvousSection from '../components/admin-space/AdminRendezvousSection.jsx'
import AdminReclamationsSection from '../components/admin-space/AdminReclamationsSection.jsx'
import AdminUsersSection from '../components/admin-space/AdminUsersSection.jsx'
import AdminOverviewSection from '../components/admin-space/AdminOverviewSection.jsx'
import AdminWhatsAppSection from '../components/admin-space/AdminWhatsAppSection.jsx'
import { useAdminSpaceData } from '../hooks/useAdminSpaceData.js'
import '../styles/PlatformSpaces.css'

const MODULES = [
  { key: 'overview',     label: 'Vue d\'ensemble',    icon: LayoutGrid },
  { key: 'avocats',      label: 'Avocats',             icon: BadgeCheck },
  { key: 'users',        label: 'Utilisateurs',        icon: UserCog },
  { key: 'reclamations', label: 'Réclamations',        icon: ClipboardList },
  { key: 'affaires',     label: 'Affaires Juridiques', icon: FileText },
  { key: 'rendezvous',   label: 'Rendez-vous',         icon: Calendar },
  { key: 'documents',    label: 'Documents (Vault)',   icon: Lock },
  { key: 'messenger',    label: 'Messagerie Globale',  icon: MessagesSquare },
  { key: 'whatsapp',     label: 'Système WhatsApp',    icon: Smartphone },
  { key: 'audit',        label: 'Journal Sécurité',    icon: ShieldAlert },
]

function fmtDate(v) {
  if (!v) return '—'
  try { return new Date(v).toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' }) } catch { return String(v) }
}

export default function AdminSpacePage() {
  const { token, user, isAuthenticated } = useAuth()
  const [activeModule, setActiveModule] = useState('overview')
  const [query, setQuery] = useState('')
  const {
    busy,
    err,
    avocatsPending,
    avocatsLoading,
    users,
    usersTotal,
    usersLoading,
    reclamations,
    reclamationsTotal,
    reclamationsLoading,
    affaires,
    affairesTotal,
    affairesLoading,
    rdvs,
    rdvsTotal,
    rdvsLoading,
    docs,
    docsTotal,
    docsLoading,
    conversations,
    conversationsTotal,
    conversationsLoading,
    auditLogs,
    auditTotal,
    auditLoading,
    waStatus,
    waQr,
    waLoading,
    testPhone,
    setTestPhone,
    testMsg,
    setTestMsg,
    selectedAuditLog,
    setSelectedAuditLog,
    loadUsers,
    handleVerifyAvocat,
    handleToggleUser,
    handleUpdateReclamation,
    handleTriggerReminders,
    handleDeleteDocument,
    handleCloseConversation,
    handleGenerateWaQr,
    handleSendWaTest,
  } = useAdminSpaceData(token, activeModule, query)


  if (!isAuthenticated || !token) return <Navigate to="/" replace />
  if (user?.roleUser !== 'admin') return <Navigate to="/" replace />

  const Spinner = () => <Loader2 className="forsalaw-spin" size={18} style={{ color: 'var(--gold)' }} />

  return (
    <motion.main className="platform-page" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
      <PageHeader
        className="admin-console-header"
        tag="Chambre de Contrôle · OMNISCIENCE"
        tagClassName="platform-eyebrow"
        title="Console d'Administration Unifiée"
        titleClassName="platform-title"
      >
        <p className="platform-subtitle">
          Supervision absolue des utilisateurs, du coffre-fort légal, du réseau messagerie et du bridge WhatsApp.
        </p>
      </PageHeader>

      {err && (
        <div style={{ padding: '0.75rem 1rem', background: 'rgba(180,30,30,0.15)', border: '2px solid #b42020', marginBottom: '1rem', display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.8rem', color: '#ff8a80' }}>
          <AlertTriangle size={14} /> {err}
        </div>
      )}

      <div className="dossier-layout">
        {/* NAV: HORIZONTAL COMMAND ARRAY */}
        <div className="command-array-grid">
          {MODULES.map((m) => {
            const Icon = m.icon
            const active = m.key === activeModule
            return (
              <button
                key={m.key}
                className={`command-button${active ? ' active' : ''}`}
                onClick={() => { setActiveModule(m.key); setQuery('') }}
                type="button"
              >
                <Icon size={18} />
                <span>{m.label}</span>
                {m.key === 'avocats' && avocatsPending.length > 0 && (
                  <span className="status-badge-admin alert">{avocatsPending.length} PENDING</span>
                )}
              </button>
            )
          })}
        </div>

        {/* MAIN PANEL */}
        <section className="dossier-main-realm" style={{ marginTop: '2rem' }}>
          {['users', 'avocats', 'reclamations', 'affaires', 'documents'].includes(activeModule) && (
            <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: '1.5rem' }}>
              <label className="admin-search-brutal" style={{ display: 'flex', alignItems: 'center', border: '3px solid var(--black)', padding: '0.5rem 1rem', background: '#0a0a0a', width: '300px' }}>
                <Search size={14} style={{ color: 'var(--gold)' }} />
                <input
                  value={query}
                  onChange={(e) => {
                    setQuery(e.target.value)
                    if (activeModule === 'users') loadUsers(e.target.value)
                  }}
                  placeholder="ID / NOM / EMAIL..."
                  style={{ background: 'transparent', border: 'none', color: 'var(--white)', outline: 'none', marginLeft: '1rem', width: '100%', fontFamily: 'monospace', fontSize: '0.7rem' }}
                />
              </label>
            </div>
          )}

          {/* ── OVERVIEW (CLEAN LEDGER) ── */}
          {activeModule === 'overview' && (
            <AdminOverviewSection
              usersLoading={usersLoading}
              usersTotal={usersTotal}
              avocatsLoading={avocatsLoading}
              avocatsPending={avocatsPending}
              reclamationsLoading={reclamationsLoading}
              reclamationsTotal={reclamationsTotal}
              affairesLoading={affairesLoading}
              affairesTotal={affairesTotal}
              auditLoading={auditLoading}
              auditLogs={auditLogs}
              fmtDate={fmtDate}
              Spinner={Spinner}
              affaires={affaires}
              rdvsLoading={rdvsLoading}
              rdvsTotal={rdvsTotal}
              conversationsLoading={conversationsLoading}
              conversationsTotal={conversationsTotal}
              docsLoading={docsLoading}
              docsTotal={docsTotal}
            />
          )}

          {/* ── AVOCATS ── */}
          {activeModule === 'avocats' && (
            <AdminAvocatsSection
              avocatsPending={avocatsPending}
              avocatsLoading={avocatsLoading}
              Spinner={Spinner}
              busy={busy}
              handleVerifyAvocat={handleVerifyAvocat}
            />
          )}

          {/* ── USERS ── */}
          {activeModule === 'users' && (
            <AdminUsersSection
              users={users}
              usersLoading={usersLoading}
              Spinner={Spinner}
              busy={busy}
              handleToggleUser={handleToggleUser}
            />
          )}

          {/* ── RECLAMATIONS ── */}
          {activeModule === 'reclamations' && (
            <AdminReclamationsSection
              reclamationsTotal={reclamationsTotal}
              reclamationsLoading={reclamationsLoading}
              reclamations={reclamations}
              Spinner={Spinner}
              busy={busy}
              onUpdateStatus={handleUpdateReclamation}
              fmtDate={fmtDate}
            />
          )}

          {/* ── AFFAIRES ── */}
          {activeModule === 'affaires' && (
            <AdminAffairesSection
              affairesTotal={affairesTotal}
              affairesLoading={affairesLoading}
              Spinner={Spinner}
              affaires={affaires}
              fmtDate={fmtDate}
            />
          )}

          {/* ── RENDEZ-VOUS ── */}
          {activeModule === 'rendezvous' && (
            <AdminRendezvousSection
              rdvsTotal={rdvsTotal}
              busy={busy}
              handleTriggerReminders={handleTriggerReminders}
              Spinner={Spinner}
              rdvsLoading={rdvsLoading}
              rdvs={rdvs}
              fmtDate={fmtDate}
            />
          )}

          {/* ── DOCUMENTS (VAULT) ── */}
          {activeModule === 'documents' && (
            <AdminDocumentsSection
              docsTotal={docsTotal}
              docsLoading={docsLoading}
              Spinner={Spinner}
              docs={docs}
              busy={busy}
              handleDeleteDocument={handleDeleteDocument}
            />
          )}

          {/* ── MESSENGER ── */}
          {activeModule === 'messenger' && (
            <AdminMessengerSection
              conversationsTotal={conversationsTotal}
              conversationsLoading={conversationsLoading}
              Spinner={Spinner}
              conversations={conversations}
              fmtDate={fmtDate}
              busy={busy}
              handleCloseConversation={handleCloseConversation}
            />
          )}

          {/* ── WHATSAPP ── */}
          {activeModule === 'whatsapp' && (
            <AdminWhatsAppSection
              waStatus={waStatus}
              waLoading={waLoading}
              handleGenerateWaQr={handleGenerateWaQr}
              Spinner={Spinner}
              waQr={waQr}
              handleSendWaTest={handleSendWaTest}
              testPhone={testPhone}
              setTestPhone={setTestPhone}
              testMsg={testMsg}
              setTestMsg={setTestMsg}
              busy={busy}
            />
          )}

          {/* ── AUDIT ── */}
          {activeModule === 'audit' && (
            <AdminAuditSection
              auditTotal={auditTotal}
              auditLoading={auditLoading}
              Spinner={Spinner}
              auditLogs={auditLogs}
              selectedAuditLog={selectedAuditLog}
              setSelectedAuditLog={setSelectedAuditLog}
              fmtDate={fmtDate}
            />
          )}
          
        </section>
      </div>
    </motion.main>
  )
}
