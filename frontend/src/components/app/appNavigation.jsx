import {
  Award,
  Calendar as LucideCalendar,
  FileText,
  LayoutGrid,
  Mail,
  MessageCircle,
  MessageSquare,
  Shield,
  Sparkles,
  UserCog,
  Users,
} from 'lucide-react'

export function getNavItems(isAuthenticated) {
  const items = [
    { key: 'nav_home', path: '/', icon: <LayoutGrid size={28} /> },
    { key: 'nav_cases', path: '/cases', icon: <FileText size={28} /> },
    { key: 'nav_support', path: '/support', icon: <MessageSquare size={28} /> },
    { key: 'nav_calendar', path: '/calendar', icon: <LucideCalendar size={28} /> },
    { key: 'nav_inbox', path: '/inbox', icon: <Mail size={28} /> },
    { key: 'nav_lawyers', path: '/lawyers', icon: <Users size={28} /> },
    { key: 'nav_lawyer_space', path: '/lawyer-space', icon: <Award size={28} /> },
    { key: 'nav_forum', path: '/forum', icon: <MessageCircle size={28} /> },
    { key: 'nav_ai', path: '/ai', icon: <Sparkles size={28} /> },
    { key: 'nav_admin', path: '/admin-space', icon: <Shield size={28} /> },
  ]

  if (isAuthenticated) {
    items.splice(6, 0, { key: 'nav_client_space', path: '/client-space', icon: <UserCog size={28} /> })
  }

  return items
}

const PAGE_KEY_TO_PATH = {
  home: '/',
  cases: '/cases',
  support: '/support',
  calendar: '/calendar',
  inbox: '/inbox',
  lawyers: '/lawyers',
  'client-space': '/client-space',
  'lawyer-space': '/lawyer-space',
  forum: '/forum',
  ai: '/ai',
  'admin-space': '/admin-space',
  // Le pied de page proposait deja « Confidentialite » et « Legislation ». Faute d'entree
  // ici, mapPageKeyToPath repliait sur '/' : les deux liens ramenaient silencieusement a
  // l'accueil, ce qui se lit comme un site qui n'a pas de politique de confidentialite.
  privacy: '/legal/confidentialite',
  terms: '/legal/conditions',
  legal: '/legal/mentions',
}

export function mapPageKeyToPath(pageKey) {
  return PAGE_KEY_TO_PATH[pageKey] ?? '/'
}
