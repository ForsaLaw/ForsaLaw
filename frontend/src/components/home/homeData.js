import {
  Calendar,
  Landmark,
  Lock,
  MapPin,
  Shield,
  UserCheck,
} from 'lucide-react'

export const TRUST_PILLARS = [
  { icon: Lock, titleKey: 'home_trust_p1_title', bodyKey: 'home_trust_p1_body' },
  { icon: UserCheck, titleKey: 'home_trust_p2_title', bodyKey: 'home_trust_p2_body' },
  { icon: Shield, titleKey: 'home_trust_p3_title', bodyKey: 'home_trust_p3_body' },
  { icon: MapPin, titleKey: 'home_trust_p4_title', bodyKey: 'home_trust_p4_body' },
]

export const ABOUT_BLOCKS = [
  { icon: Landmark, titleKey: 'home_about_card1_title', bodyKey: 'home_about_card1_body' },
  { icon: Shield, titleKey: 'home_about_card2_title', bodyKey: 'home_about_card2_body' },
  { icon: Calendar, titleKey: 'home_about_card3_title', bodyKey: 'home_about_card3_body' },
]

export const CODEX_ARTICLES = [
  {
    roman: 'I',
    title: 'CONSULTATION',
    body: 'Exposez votre situation à Fellawra ou à un avocat certifié. Le dialogue précède le dossier.',
    image: '/home/home-phase-consultation.jpg',
  },
  {
    roman: 'II',
    title: 'DOSSIER',
    body: 'Chaque affaire est formalisée, archivée, et suivie dans nos registres numériques immuables.',
    image: '/home/home-phase-dossier.jpg',
  },
  {
    roman: 'III',
    title: 'VERDICT',
    body: 'La Justice est rendue. Le système trace, préserve, et protège chaque étape de votre parcours.',
    image: '/home/home-phase-justice.jpg',
  },
]
