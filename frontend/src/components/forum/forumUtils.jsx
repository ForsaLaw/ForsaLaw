export function fmtTime(v) {
  if (!v) return '—'
  try {
    const d = new Date(v)
    const diff = (Date.now() - d.getTime()) / 1000
    if (diff < 60) return 'À l\'instant'
    if (diff < 3600) return `Il y a ${Math.floor(diff / 60)} min`
    if (diff < 86400) return `Il y a ${Math.floor(diff / 3600)} h`
    if (diff < 604800) return `Il y a ${Math.floor(diff / 86400)} j`
    return d.toLocaleDateString('fr-FR')
  } catch {
    return String(v)
  }
}

export function initials(nom) {
  if (!nom) return '?'
  return nom
    .split(' ')
    .map((p) => p[0] ?? '')
    .join('')
    .toUpperCase()
    .slice(0, 2)
}

export function roleBadge(role) {
  if (!role) return null
  if (role === 'AVOCAT') return <span className="forum-role-badge avocat">Avocat</span>
  if (role === 'ADMIN') return <span className="forum-role-badge admin">Admin</span>
  return null
}

export const TRENDING_TAGS = ['#Réclamation', '#Divorce', '#Travail', '#Immobilier', '#Pénal', '#Tutelle', '#Succession', '#CNSS']
