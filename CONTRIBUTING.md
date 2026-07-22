# Contributing to ForsaLaw

Merci de contribuer à **ForsaLaw** (plateforme LegalTech — Spring Boot + React + PostgreSQL).
Ce guide résume l'exécution locale, la convention de nommage des branches et les règles de PR.
Pour les détails d'architecture et de démarrage, voir le [README](README.md).

> Le dépôt Git est le dossier **`ForsaLaw/`** (backend Spring Boot + frontend React/Vite).

---

## 1. Lancer le projet en local

Prérequis : **JDK 17+**, **Maven**, **Node.js 18+**, **Docker**.

### a. Base de données (PostgreSQL + pgAdmin via Docker)

```bash
cd backend
docker-compose up -d
```

Démarre `forsalaw-postgres` (port hôte **5433**) et `forsalaw-pgadmin` (port **5051**).

### b. Variables d'environnement

Copier **`backend/.env.example`** vers **`backend/.env`** et renseigner au minimum les
variables **OBLIGATOIRES** (`DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `WHATSAPP_BRIDGE_TOKEN`,
`MAIL_USERNAME`, `MAIL_PASSWORD`). Charger le `.env` via l'IDE ou l'environnement du shell.

### c. Backend (Spring Boot)

```bash
cd backend
mvn clean install          # build + tests (mvn clean install -DskipTests pour sauter les tests)
mvn spring-boot:run        # API sur http://localhost:8081 (Swagger: /swagger-ui.html)
```

### d. Frontend (React + Vite)

```bash
cd frontend
npm install
npm run dev                # SPA sur http://localhost:3000 (proxy /api -> :8081)
```

### e. Vérifier

- Backend : `cd backend && mvn test`
- Frontend : `cd frontend && npm run build`

---

## 2. Convention de nommage des branches

| Préfixe | Usage |
|---------|-------|
| `feature/phase<N>-<sujet>` | Travaux planifiés de remédiation/évolution (ex. `feature/phase5-infrastructure`) |
| `feature/<sujet>` | Nouvelle fonctionnalité hors phase |
| `fix/<sujet>` | Correction de bug |

- Une branche = une unité de travail cohérente et revue-able.
- Noms en **kebab-case**, courts et explicites.

---

## 3. Règles de Pull Request

1. **Toujours cibler `develop`** comme branche de base (jamais `main` directement).
2. **Branches empilées (stacked)** : si votre travail dépend de code encore non fusionné
   (ex. des tests qui testent le code d'une phase précédente), basez votre branche sur **la
   branche de la phase** concernée et ciblez-la dans la PR. Une fois cette base fusionnée dans
   `develop`, **reciblez** la PR sur `develop`.
3. **CI verte obligatoire** : la PR n'est fusionnée que si le build/les tests passent et que le
   code ne dégrade ni la qualité ni les performances.
4. **Revue** : validation par les mainteneurs (Nadhmi Rouissi, Youssef Zaied) avant fusion.
5. **Commits explicites** (Conventional Commits encouragé : `feat:`, `fix:`, `test:`, `chore:` …).
6. Ne jamais committer de secrets : `.env` est ignoré ; n'ajoutez que `.env.example`.

---

## 4. Tests

- Backend : JUnit 5 + Mockito + `@WebMvcTest` (voir `backend/src/test/`). Lancer `mvn test`.
- Ajoutez/mettez à jour les tests pour tout changement de logique métier ou de sécurité.

Voir aussi : [docs/GIT-WORKFLOW.md](docs/GIT-WORKFLOW.md), [docs/DEVOPS.md](docs/DEVOPS.md).
