# syntax=docker/dockerfile:1
#
# Image de production du frontend ForsaLaw (React / Vite servi par Nginx).
# Build : docker build -t forsalaw-frontend ./frontend

# ─── Etape 1 : compilation du bundle ────────────────────────────────────────
FROM node:20-alpine AS build
WORKDIR /build

# Manifestes seuls d'abord : la couche npm ci est reutilisee tant que les dependances
# ne changent pas.
COPY package.json package-lock.json ./
# `npm ci` (et non `npm install`) : installation reproductible, strictement conforme au lock.
RUN npm ci

COPY . .
RUN npm run build

# ─── Etape 2 : service statique ─────────────────────────────────────────────
FROM nginx:1.27-alpine AS runtime

# Aucune URL d'API n'est figee dans le bundle : le frontend n'utilise que des chemins relatifs
# (/api, /ws), relayes par Nginx. La MEME image fonctionne donc en recette et en production.
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html

EXPOSE 80

# nginx:alpine demarre deja ses workers sous l'utilisateur non privilegie "nginx" ; seul le
# processus maitre reste root, ce qui est requis pour ecouter sur le port 80.
CMD ["nginx", "-g", "daemon off;"]
