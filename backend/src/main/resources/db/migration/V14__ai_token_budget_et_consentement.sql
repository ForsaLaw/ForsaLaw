-- Phase A — garde-fous IA : budget de jetons et consentement cote serveur.
--
-- Les colonnes ajoutees a "users" sont soit NOT NULL AVEC DEFAUT, soit nullables. Un
-- NOT NULL sans defaut echouerait sur les lignes existantes (defaut deja constate sur
-- rendez_vous.date_mise_a_jour, qui fait echouer le ddl-auto d'Hibernate a chaque demarrage).

-- ─── Budget de jetons par utilisateur ────────────────────────────────────────────────
-- Plafond QUOTIDIEN, surchargeable par compte (un cabinet peut avoir un quota superieur).
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS daily_token_budget INTEGER NOT NULL DEFAULT 50000;

-- ─── Consentement IA horodate ────────────────────────────────────────────────────────
-- Nullables a dessein : NULL = jamais consenti. Le consentement existant n'est connu que du
-- localStorage du navigateur et ne peut donc pas etre retro-rempli ici.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS ai_consent_version INTEGER;
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS ai_consented_at TIMESTAMP;

-- ─── Journal de consommation de jetons ───────────────────────────────────────────────
-- Une ligne par requete IA. Table dediee plutot qu'un simple compteur sur "users" : le
-- compteur ne permettrait ni les tableaux de bord de cout prevus en Phase 11, ni de savoir
-- APRES COUP quel compte a epuise le budget.
--
-- Cycle de vie d'une ligne : creee AVANT la generation avec le depot forfaitaire, puis
-- ajustee au reel a l'arrivee de la trame "done". Une requete abandonnee en cours de flux
-- n'est jamais ajustee et conserve donc son depot (statut DEPOT) : c'est ce qui rend une
-- boucle d'abandon couteuse.
CREATE TABLE IF NOT EXISTS ai_token_usage (
    id                BIGSERIAL PRIMARY KEY,
    user_id           VARCHAR(20)  NOT NULL REFERENCES users (id),
    tokens_invite     INTEGER      NOT NULL DEFAULT 0,
    tokens_reponse    INTEGER      NOT NULL DEFAULT 0,
    tokens_total      INTEGER      NOT NULL,
    statut            VARCHAR(16)  NOT NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    settled_at        TIMESTAMP,
    CONSTRAINT ai_token_usage_statut_check CHECK (statut IN ('DEPOT', 'REGLE'))
);

-- Le controle de budget somme les jetons d'un utilisateur sur la journee courante : cet index
-- evite un balayage complet de la table a CHAQUE requete IA.
CREATE INDEX IF NOT EXISTS idx_ai_token_usage_user_date
    ON ai_token_usage (user_id, created_at);

-- Le plafond mensuel global somme toutes les lignes du mois, tous comptes confondus.
CREATE INDEX IF NOT EXISTS idx_ai_token_usage_date
    ON ai_token_usage (created_at);
