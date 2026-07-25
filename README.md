<div align="center">

# ForsaLaw

**Plateforme LegalTech tunisienne** — mise en relation client-avocat, rendez-vous (présentiel / en ligne), notifications, authentification sécurisée et modules de collaboration.

[![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18-61DAFB?logo=react&logoColor=black)](https://react.dev/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-316192?logo=postgresql&logoColor=white)](https://www.postgresql.org/)

</div>

---

## Vue d'ensemble

> [!NOTE]
> ForsaLaw regroupe dans une même application la **gestion des comptes** (client, avocat, admin), la **prise de rendez-vous** avec agenda avancé côté avocat, les **notifications e-mail**, la **visioconférence** pour les RDV en ligne (liens Jitsi), la **sécurité** (JWT, OAuth2 Google, réinitialisation de mot de passe), ainsi que la **messagerie**, les **réclamations**, le **coffre-fort documents** et d’autres modules métier exposés en **API REST** et documentés via **Swagger**.




---

## Architecture physique

> [!IMPORTANT]
> Schéma aligné sur ce dépôt : **développement local** (ports et chemins issus de `application.properties`, `vite.config.js` et `backend/docker-compose.yml`).

```mermaid
flowchart TB
  subgraph poste["Poste développeur / machine hôte"]
    subgraph docker["Docker Compose (backend/)"]
      PG[("PostgreSQL 16\nimage pgvector\nport hôte 5433")]
      PGA[("pgAdmin 4\nport hôte 5051")]
    end
    JVM["Processus JVM — Spring Boot\nport 8081"]
    NODE["Processus Node — Vite dev\nport 3000"]
    FS["Stockage objet S3 / MinIO"]
  end

  subgraph externes["Services externes (configuration)"]
    SMTP[(SMTP / e-mail)]
    GOOGLE[(Google OAuth2)]
    JITSI[(Jitsi Meet — URLs)]
    WA[(Bridge WhatsApp HTTP\noptionnel)]
    CLAM[(ClamAV\noptionnel)]
  end

  BROWSER((Navigateur))
  BROWSER --> NODE
  NODE -->|proxy /api| JVM
  BROWSER -->|WebSocket STOMP| JVM
  JVM --> PG
  PGA --> PG
  JVM --> FS
  JVM --> SMTP
  JVM --> GOOGLE
  JVM --> JITSI
  JVM -.-> WA
  JVM -.-> CLAM

  FS --> D1["Coffre documents\n(bucket, préfixe documents/)"]
  FS --> D2["Pièces jointes messagerie\n(via le coffre, par ID document)"]
  FS --> D3["Fichiers réclamations\n(via le coffre, par ID document)"]
```

**Composants physiques concrets**

| Élément | Rôle |
|--------|------|
| **PostgreSQL** (conteneur `pgvector/pgvector:pg16`) | Base relationnelle + extension **pgvector** pour le futur / optionnel RAG (`legal_document_chunk`, migration SQL `V1` dans le dépôt). |
| **pgAdmin** | Administration de la base (développement). |
| **Spring Boot** | API unique, Tomcat embarqué, port **8081** par défaut. |
| **Vite + React** | SPA en dev sur **3000**, proxy `/api` → `8081`. |
| **Volumes Docker** | Persistance des données PostgreSQL (`forsalaw_pgdata`). |
| **Système de fichiers** | Stockage des documents coffre-fort, PJ messagerie, PJ réclamations (chemins paramétrables). |

---

## Architecture logique

> [!IMPORTANT]
> Découpage **réel** du backend : packages `com.forsalaw.*` et flux HTTP / données ci-dessous.

```mermaid
flowchart LR
  subgraph client["Client"]
    SPA["SPA React\n(i18n, React Router)"]
  end

  subgraph api["API Spring Boot"]
    SEC["Spring Security\nJWT + OAuth2"]
    CTRL["Controllers REST\n(@RestController)"]
    SVC["Services métier"]
    REPO["Spring Data JPA"]
  end

  subgraph data["Données"]
    DB[(PostgreSQL)]
  end

  subgraph temps_reel["Temps réel"]
    STOMP["Broker STOMP intégré\n(SimpleBroker)"]
  end

  SPA -->|HTTPS /api| SEC
  SEC --> CTRL
  CTRL --> SVC
  SVC --> REPO
  REPO --> DB
  SPA <-->|WebSocket / STOMP| STOMP
  SVC --> STOMP
```

**Modules métier (packages Java)**

| Package | Responsabilité |
|---------|----------------|
| `userManagement` | Utilisateurs, auth JWT, OAuth2, profils, photo de profil liée au coffre. |
| `avocatManagement` | Profil avocat, vérification, exposition publique / espace connecté. |
| `affaireManagement` | Dossiers / affaires et statuts. |
| `rdvManagement` | Rendez-vous, agenda avocat, créneaux, rappels. |
| `messengerManagement` | Conversations, messages, pièces jointes, **WebSocket** (`realtime/`). |
| `reclamationManagement` | Réclamations, pièces jointes, transitions de statut. |
| `documentManagement` | Coffre-fort, métadonnées, logs d’accès, signature PDF. |
| `notificationManagement` | E-mails et pont WhatsApp (selon configuration). |
| `forumManagement` | Forum (sujets / messages). |
| `avisAvocatManagement` | Avis sur les avocats. |
| `auditManagement` | Journalisation d’audit. |
| `security` | Filtres JWT, configuration d’accès. |
| `config` | Configuration transverse (ex. SpringDoc / OpenAPI). |
| `documentManagement.config` | Beans techniques liés au module documents (ex. correctif contrainte PostgreSQL au démarrage). |
| `util` | Utilitaires partagés (ex. hachage). |

**Persistance et schéma**

- **JPA / Hibernate** avec `spring.jpa.hibernate.ddl-auto=update` pour faire évoluer le schéma à partir des entités.
- **Flyway** présent dans les dépendances mais **désactivé par défaut** ; le dépôt contient une migration **RAG** (`db/migration/V1__...`) à appliquer manuellement ou en activant Flyway ponctuellement (voir `docs/FLYWAY-VS-JPA.md`).

---

## Stack technique

> [!NOTE]
> Badges indicatifs ; versions précises dans `pom.xml` et `package.json`.

![Java](https://img.shields.io/badge/Java-21-007396?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring%20Security-JWT-6DB33F?style=for-the-badge&logo=springsecurity&logoColor=white)
![Spring Data JPA](https://img.shields.io/badge/Spring%20Data%20JPA-Hibernate-59666C?style=for-the-badge&logo=hibernate&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-316192?style=for-the-badge&logo=postgresql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Containerized-2496ED?style=for-the-badge&logo=docker&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-Build-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white)
![Swagger](https://img.shields.io/badge/Swagger-OpenAPI-85EA2D?style=for-the-badge&logo=swagger&logoColor=black)
![React](https://img.shields.io/badge/React-Frontend-61DAFB?style=for-the-badge&logo=react&logoColor=black)
![Vite](https://img.shields.io/badge/Vite-Dev%20Server-646CFF?style=for-the-badge&logo=vite&logoColor=white)
![OAuth2](https://img.shields.io/badge/OAuth2-Google-EA4335?style=for-the-badge&logo=google&logoColor=white)
![GitHub Actions](https://img.shields.io/badge/GitHub%20Actions-CI-2088FF?style=for-the-badge&logo=githubactions&logoColor=white)

---

## Structure du dépôt

> [!TIP]
> - `backend/` — API Spring Boot, logique métier, persistance, `docker-compose.yml` (PostgreSQL + pgAdmin).
> - `frontend/` — Application React (Vite), proxy `/api` vers le backend en développement.
> - `docs/` — Guides techniques et workflows.
> - `.github/workflows/` — Pipelines CI.

---

## Démarrage rapide

![Démarrage](https://img.shields.io/badge/🚀_DÉMARRAGE_RAPIDE-238636?style=for-the-badge)

### 1. Cloner le dépôt

```bash
git clone https://github.com/ForsaLaw/ForsaLaw.git
cd ForsaLaw
```

### 2. Démarrer la base de données (PostgreSQL via Docker)

Depuis le dossier **`backend/`** :

```bash
cd backend
docker-compose up -d
```

**Ce que fait cette commande :**

- télécharge l’image **PostgreSQL 16** avec extension **pgvector** si nécessaire (`pgvector/pgvector:pg16`) ;
- crée et démarre le conteneur **`forsalaw-postgres`** ;
- mappe le port **5433** sur l’hôte vers **5432** dans le conteneur ;
- démarre aussi **pgAdmin** (`forsalaw-pgadmin`, port hôte **5051**) ;
- exécute les services en arrière-plan (`-d`).

**Vérifier que tout tourne :**

```bash
docker ps
```

Tu dois voir au minimum **`forsalaw-postgres`** (et **`forsalaw-pgadmin`** si tu n’as pas limité les services).

### 3. Variables d’environnement

Copier **`backend/.env.example`** vers **`.env`** (toujours dans `backend/`) et renseigner les valeurs nécessaires, par exemple :

- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` (alignés sur `docker-compose.yml` en local : `jdbc:postgresql://localhost:5433/forsalaw`, `forsalaw` / `forsalaw`) ;
- `JWT_SECRET` ;
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` (OAuth2) ;
- `MAIL_*`, `MAIL_FROM` si tu veux de vrais e-mails ;
- `ADMIN_SUPPORT_EMAIL`, `JITSI_BASE_URL`, etc.

### 4. Compiler le backend (Maven)

Toujours depuis **`backend/`** :

```bash
mvn clean install -DskipTests
```

*(Tu peux retirer `-DskipTests` si tu veux lancer les tests à la compilation.)*

### 5. Lancer l’API Spring Boot

```bash
mvn spring-boot:run
```

L’API écoute par défaut sur le port **8081** (`SERVER_PORT` dans `.env` pour changer).

### 6. Lancer le frontend (Vite + React)

Dans un **second terminal**, depuis la racine du dépôt puis **`frontend/`** :

```bash
cd frontend
npm install
npm run dev
```

Le serveur de dev Vite utilise le port **3000** et **proxy** les appels `/api` vers `http://localhost:8081`.

### 7. Vérifier que tout fonctionne

Ouvre un navigateur :

- **Swagger UI :** [http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html)  
  *(équivalent souvent disponible : `/swagger-ui/index.html`)*  
- **Frontend :** [http://localhost:3000](http://localhost:3000)

---

![Commandes](https://img.shields.io/badge/⚡_COMMANDES_ESSENTIELLES-f97316?style=for-the-badge)

```bash
# Compiler le backend
cd backend && mvn clean install -DskipTests

# Lancer l’API
cd backend && mvn spring-boot:run

# Démarrer PostgreSQL (+ pgAdmin) en arrière-plan
cd backend && docker-compose up -d

# Arrêter les conteneurs
cd backend && docker-compose down

# Suivre les logs PostgreSQL
cd backend && docker-compose logs -f postgres

# Conteneurs en cours d’exécution
docker ps
```

---

![URLs](https://img.shields.io/badge/🔗_URLS_IMPORTANTES-0969da?style=for-the-badge)

| Ressource | URL |
|-----------|-----|
| **API (backend)** | [http://localhost:8081](http://localhost:8081) |
| **Swagger UI** | [http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html) |
| **OpenAPI (JSON)** | [http://localhost:8081/v3/api-docs](http://localhost:8081/v3/api-docs) |
| **Frontend (Vite)** | [http://localhost:3000](http://localhost:3000) |
| **pgAdmin** | [http://localhost:5051](http://localhost:5051) *(identifiants par défaut dans `backend/docker-compose.yml`)* |

---

![Revue de code](https://img.shields.io/badge/📋_REVUE_DE_CODE-cf222e?style=for-the-badge)

**Nadhmi** et **Youssef** n’acceptent les fusions (merge requests / pull requests) que si le job de build CI est **vert** et que le code **ne dégrade** pas la qualité ni les performances du projet ForsaLaw.

---

## Workflow Git

> [!NOTE]
> Branche de travail cible : **`develop`**.
>
> 1. Mettre à jour `develop`  
> 2. Créer une branche `feature/...` ou `fix/...`  
> 3. Commits explicites  
> 4. Push et PR vers `develop`  
> 5. Fusion après revue et CI verte  
>
> Détails : [docs/GIT-WORKFLOW.md](docs/GIT-WORKFLOW.md), [docs/DEVOPS.md](docs/DEVOPS.md)

---

## Documentation interne

> [!NOTE]
> - [docs/DOCKER.md](docs/DOCKER.md)  
> - [docs/FLYWAY-VS-JPA.md](docs/FLYWAY-VS-JPA.md)  
> - [docs/RAG-INTEGRATION-NON-ENDPOINTS.md](docs/RAG-INTEGRATION-NON-ENDPOINTS.md)  
> - [docs/Liste_Des_Endpoints_USER_AVOCAT_AUTHENTIFIACTION.md](docs/Liste_Des_Endpoints_USER_AVOCAT_AUTHENTIFIACTION.md)

---

## Ressources

> [!TIP]
> - [Feuille Google Sheets ForsaLaw](https://docs.google.com/spreadsheets/d/1SuGkhR0qkSWrvWlVQsqcuU9j0ZIJUoWSm3061GFPxLo/edit?gid=1469894259#gid=1469894259) — accès au document (connexion Google si nécessaire).

---

## Contributeurs

> [!NOTE]
> - Nadhmi Rouissi  
> - Youssef Zaied  

---

<div align="center">

**ForsaLaw** — LegalTech Tunisia

</div>
