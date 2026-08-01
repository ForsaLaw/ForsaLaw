import { Suspense, useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { motion } from 'framer-motion'
import { Menu, X } from 'lucide-react'
import { useLocation, useNavigate } from 'react-router-dom'
import AppErrorBoundary from './components/AppErrorBoundary'
import { getNavItems, mapPageKeyToPath } from './components/app/appNavigation.jsx'
import AppRoutes from './components/app/AppRoutes'
import AuthIntroOverlay from './components/app/AuthIntroOverlay'
import AuthModal from './components/app/AuthModal'
import NavGalleryOverlay from './components/app/NavGalleryOverlay'
import TopRightControls from './components/app/TopRightControls'
import RouteLoadingScreen from './components/RouteLoadingScreen'
import ScrollToTop from './components/ScrollToTop'
import ScrollToTopButton from './components/ScrollToTopButton'
import SiteVideoBackground from './components/SiteVideoBackground'
import { useAppAuthFlow } from './hooks/useAppAuthFlow.js'
import { useEscapeKeyClose } from './hooks/useEscapeKeyClose.js'
import './styles/App.css'
import { lazyRoute } from './utils/lazyRoute'
import { useAuth } from './context/AuthContext.jsx'

const HomePage = lazyRoute(() => import('./pages/HomePage'))
const ForumPage = lazyRoute(() => import('./pages/ForumPage'))
const LawyersPage = lazyRoute(() => import('./pages/LawyersPage'))
const DossierPage = lazyRoute(() => import('./pages/DossierPage'))
const LawyerSpacePage = lazyRoute(() => import('./pages/LawyerSpacePage'))
const ClientSpacePage = lazyRoute(() => import('./pages/ClientSpacePage'))
const SupportPage = lazyRoute(() => import('./pages/SupportPage'))
const CalendarPage = lazyRoute(() => import('./pages/CalendarPage'))
const AiSanctumPage = lazyRoute(() => import('./pages/AiSanctumPage'))
const AdminSpacePage = lazyRoute(() => import('./pages/AdminSpacePage'))
const InboxPage = lazyRoute(() => import('./pages/InboxPage'))
const AuthPage = lazyRoute(() => import('./pages/AuthPage'))
const GoogleOAuthCallbackPage = lazyRoute(() => import('./pages/GoogleOAuthCallbackPage'))
const AppointmentBookingPage = lazyRoute(() => import('./pages/AppointmentBookingPage'))
const LegalPage = lazyRoute(() => import('./pages/LegalPage'))
const OnlineMeetingRoomPage = lazyRoute(() => import('./pages/OnlineMeetingRoomPage'))

function App() {
  const { t, i18n } = useTranslation()
  const { user, isAuthenticated, login, register, logout } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const [isNavOpen,   setIsNavOpen]   = useState(false)
  const {
    authMode,
    authIntroMode,
    authIntroStriking,
    formNom,
    formPrenom,
    formEmail,
    formPassword,
    authFormError,
    authFormSuccess,
    authSubmitting,
    authCardRef,
    openAuth,
    closeAuth,
    closeAuthIntro,
    handleAuthIntroStrike,
    handleAuthSubmit,
    setFormNom,
    setFormPrenom,
    setFormEmail,
    setFormPassword,
    setAuthMode,
  } = useAppAuthFlow({ login, register })

  useEffect(() => {
    document.body.dir = i18n.dir()
  }, [i18n.language])

  useEscapeKeyClose({
    authIntroMode,
    authMode,
    isNavOpen,
    closeAuthIntro,
    closeAuth,
    closeNav: () => setIsNavOpen(false),
  })

  const NAV_ITEMS = useMemo(() => getNavItems(isAuthenticated), [isAuthenticated])

  const toggleLanguage = () => {
    const cycle = { fr: 'ar', ar: 'en', en: 'fr' }
    i18n.changeLanguage(cycle[i18n.language] ?? 'fr')
  }

  const isInboxRoute = location.pathname === '/inbox'

  const navigateToPageKey = (pageKey) => {
    const path = mapPageKeyToPath(pageKey)
    setIsNavOpen(false)
    navigate(path)
  }

  return (
    <div className={`app-root${isInboxRoute ? ' app-root--inbox' : ''}`}>
      <ScrollToTop />
      <ScrollToTopButton />
      {location.pathname === '/' && <SiteVideoBackground />}
      {/* Architectural background columns */}
      <div className="arch-bg">
        {[...Array(8)].map((_, i) => (
          <div key={i} className="arch-column" />
        ))}
      </div>

      {/* Nav Toggle Button */}
      <motion.button
        className="nav-toggle-btn"
        whileHover={{ scale: 1.05 }}
        whileTap={{ scale: 0.95 }}
        onClick={() => setIsNavOpen(o => !o)}
        aria-label="Toggle navigation"
      >
        {isNavOpen ? <X size={22} /> : <Menu size={22} />}
      </motion.button>

      <TopRightControls
        isInboxRoute={isInboxRoute}
        isAuthenticated={isAuthenticated}
        user={user}
        t={t}
        language={i18n.language.toUpperCase()}
        onOpenLogin={() => openAuth('login', true)}
        onNavigateClientSpace={() => navigate('/client-space?tab=profile')}
        onNavigateLawyerSpace={() => navigate('/lawyer-space')}
        onLogout={() => {
          logout()
          setAuthMode(null)
        }}
        onToggleLanguage={toggleLanguage}
      />

      <div className="app-main-shell">
        <AppErrorBoundary>
          <Suspense fallback={<RouteLoadingScreen />}>
            <AppRoutes
              location={location}
              HomePage={HomePage}
              ForumPage={ForumPage}
              LawyersPage={LawyersPage}
              DossierPage={DossierPage}
              AiSanctumPage={AiSanctumPage}
              LawyerSpacePage={LawyerSpacePage}
              ClientSpacePage={ClientSpacePage}
              AdminSpacePage={AdminSpacePage}
              SupportPage={SupportPage}
              CalendarPage={CalendarPage}
              InboxPage={InboxPage}
              AppointmentBookingPage={AppointmentBookingPage}
              OnlineMeetingRoomPage={OnlineMeetingRoomPage}
              AuthPage={AuthPage}
              GoogleOAuthCallbackPage={GoogleOAuthCallbackPage}
              LegalPage={LegalPage}
              onNavigateByPageKey={navigateToPageKey}
            />
          </Suspense>
        </AppErrorBoundary>
      </div>

      <NavGalleryOverlay
        isOpen={isNavOpen}
        navItems={NAV_ITEMS}
        t={t}
        onClose={() => setIsNavOpen(false)}
        onNavigate={(path) => {
          setIsNavOpen(false)
          navigate(path)
        }}
      />

      <AuthIntroOverlay
        authIntroMode={authIntroMode}
        authIntroStriking={authIntroStriking}
        onStrike={handleAuthIntroStrike}
      />

      <AuthModal
        authMode={authMode}
        authCardRef={authCardRef}
        authSubmitting={authSubmitting}
        authFormError={authFormError}
        authFormSuccess={authFormSuccess}
        formNom={formNom}
        formPrenom={formPrenom}
        formEmail={formEmail}
        formPassword={formPassword}
        onClose={closeAuth}
        onOpenMode={(mode) => openAuth(mode)}
        onSubmit={handleAuthSubmit}
        setFormNom={setFormNom}
        setFormPrenom={setFormPrenom}
        setFormEmail={setFormEmail}
        setFormPassword={setFormPassword}
      />
    </div>
  )
}

export default App
