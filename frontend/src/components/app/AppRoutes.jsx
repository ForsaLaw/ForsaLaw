import { AnimatePresence, motion } from 'framer-motion'
import { Navigate, Route, Routes } from 'react-router-dom'

export default function AppRoutes({
  location,
  HomePage,
  ForumPage,
  LawyersPage,
  DossierPage,
  AiSanctumPage,
  LawyerSpacePage,
  ClientSpacePage,
  AdminSpacePage,
  SupportPage,
  CalendarPage,
  InboxPage,
  AppointmentBookingPage,
  OnlineMeetingRoomPage,
  AuthPage,
  GoogleOAuthCallbackPage,
  onNavigateByPageKey,
}) {
  return (
    <AnimatePresence mode="wait">
      <motion.div
        key={location.pathname}
        initial={{ opacity: 1 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        transition={{ duration: 0.2 }}
      >
        <Routes location={location}>
          <Route path="/" element={<HomePage onNavigate={onNavigateByPageKey} />} />
          <Route path="/forum" element={<ForumPage />} />
          <Route path="/lawyers" element={<LawyersPage />} />
          <Route path="/cases" element={<DossierPage />} />
          <Route path="/ai" element={<AiSanctumPage />} />
          <Route path="/lawyer-space" element={<LawyerSpacePage />} />
          <Route path="/client-space" element={<ClientSpacePage />} />
          <Route path="/admin-space" element={<AdminSpacePage />} />
          <Route path="/support" element={<SupportPage />} />
          <Route path="/calendar" element={<CalendarPage />} />
          <Route path="/inbox" element={<InboxPage />} />
          <Route path="/appointments/new/:avocatId" element={<AppointmentBookingPage />} />
          <Route path="/rendezvous/:idRendezVous/online-room" element={<OnlineMeetingRoomPage />} />
          <Route path="/auth" element={<AuthPage />} />
          <Route path="/auth/google/callback" element={<GoogleOAuthCallbackPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </motion.div>
    </AnimatePresence>
  )
}
