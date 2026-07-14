# Frontend Dev Rules — ForsaLaw (React)

Guide de reference officiel pour garder un frontend propre, evolutif et lisible en equipe.
Ce document s'applique a tout code dans `frontend/`.

---

## 1) Objectif

- Stabilite produit: reduire regressions et comportements incoherents.
- Lisibilite equipe: code comprenable par un autre developpeur sans explication orale.
- Scalabilite: permettre d'ajouter des features sans casser l'existant.

---

## 2) Scope First (regle obligatoire)

Avant toute implementation, confirmer le scope avec cette question:

> "Quelle zone frontend dois-je modifier exactement ?  
> Choisis une zone principale: `app-shell` | `home` | `lawyers` | `lawyer-space` | `client-space` | `admin-space` | `inbox` | `shared`."

Regles associees:

- Ne modifier que la zone concernee + dependances partagees strictement necessaires.
- Si une modification cross-feature est requise, l'expliquer dans la PR.
- Pas de changement "opportuniste" hors scope.

---

## 3) Structure projet (React)

Structure actuelle et cible:

```text
src/
  api/
  components/
    app/
    home/
    admin-space/
    client-space/
    lawyer-space/
    ...
  context/
  hooks/
  pages/
  styles/
  utils/
  i18n.js
  App.jsx
```

Principes:

- `pages/` = orchestration de page (routing + composition haut niveau).
- `components/` = UI decoupee par feature.
- `hooks/` = logique etats/effets/api reutilisable.
- `api/` = couche I/O HTTP uniquement (pas de logique UI).
- `styles/` = styles partages et styles de pages/features.

---

## 4) Regles d'architecture

### 4.1 Responsabilites

- Une page ne doit pas porter toute la logique metier.
- Les gros blocs UI doivent etre extraits en composants dedies.
- La logique de chargement/actions doit vivre dans des hooks (`useXxx`).

### 4.2 Taille cible (guideline)

- `Page`: idealement <= 250 lignes.
- `Component`: idealement <= 180 lignes.
- `Hook`: idealement <= 180 lignes.

Si depassement durable: refactor obligatoire.

### 4.3 Refactor progressif

- Pas de "big bang" risqué.
- Refactor par tranche fonctionnelle.
- Toujours verifier build apres chaque tranche.

---

## 5) Regles React

### 5.1 Composants

- Un composant = une responsabilite.
- Eviter les composants "god component".
- Props explicites, noms coherents, pas d'ambiguite.

### 5.2 Hooks

- Nom obligatoire en `useXxx`.
- Un hook expose une API claire: `state`, `loading`, `error`, `actions`.
- Eviter melange: reseau + animation + rendu dans le meme bloc.

### 5.3 useEffect et derivees

- `useEffect` minimal, dependencies correctes.
- Nettoyer listeners/timers/object URLs dans le cleanup.
- Deriver proprement via `useMemo`/`useCallback` si necessaire.

---

## 6) API & Data Fetching

- Toutes les requetes HTTP passent par `src/api/*`.
- Pas de `fetch` inline dans plusieurs composants pour la meme ressource.
- Uniformiser la gestion d'erreur (`parseApiError` ou equivalent).
- Mapper/normaliser les reponses backend quand elles sont heterogenes.

Pattern recommande:

- `api/*.js`: transport brut I/O.
- `hooks/*.js`: orchestration des appels et etats d'ecran.

---

## 7) Routing

- Routes globales dans `AppRoutes`.
- Lazy loading systematique des pages (`lazyRoute` / `React.lazy`).
- Ne pas casser la separation entre shell applicatif et pages feature.
- Toute nouvelle route doit avoir une raison metier claire.

---

## 8) CSS / UI

- Eviter les styles inline (sauf exception ponctuelle justifiee).
- Prioriser des classes explicites et stables.
- Conserver l'identite visuelle ForsaLaw (palette, densite, animations).
- Les animations doivent enrichir, pas bloquer l'action utilisateur.

---

## 9) Accessibilite minimale (obligatoire)

- `aria-label` pour les actions non explicites.
- Focus clavier visible.
- Contrastes suffisants.
- Structure semantique (`main`, `section`, `header`, etc.).
- Ne jamais transmettre une information uniquement par la couleur.

---

## 10) i18n

- Toute nouvelle string visible dans une zone i18n-ready passe par `i18n`.
- Eviter hardcode FR/EN/AR dans les composants existants traduits.
- Garder des cles stables et explicites.

---

## 11) Hard Rules

- Ne pas installer de package sans validation prealable.
- Ne pas renommer massivement fichiers/dossiers sans ticket dedie.
- Ne pas modifier backend dans une tache frontend (sauf demande explicite).
- Ne pas melanger refactor lourd + nouvelle feature sans justification claire.

---

## 12) Checklist avant PR

- [ ] Scope confirme (feature cible explicite).
- [ ] Build frontend OK (`npm run build`).
- [ ] Pas d'erreur lint introduite.
- [ ] Parcours manuel des flux impactes.
- [ ] Responsive valide sur la zone modifiee.
- [ ] Pas de code mort / logs debug oublies.
- [ ] Description PR: `Summary` + `Test plan`.

---

## 13) Definition of Done

Une tache frontend est consideree `Done` si:

- le besoin metier est couvert,
- la structure respecte ce guide,
- build/lint sont propres,
- la UI reste coherente avec ForsaLaw,
- le code est maintenable par un autre ingenieur.

---

## 14) Anti-patterns interdits

- Page monolithique 500+ lignes sans decoupage.
- Duplication de logique role/auth partout.
- Appels API dupliques et incoherents pour la meme donnee.
- Fix rapide qui degrade la lisibilite a moyen terme.


