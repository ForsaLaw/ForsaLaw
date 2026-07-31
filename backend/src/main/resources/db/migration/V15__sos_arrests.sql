-- ============================================================================
-- V15__sos_arrests.sql
-- ----------------------------------------------------------------------------
-- Prise en charge d'urgence « SOS Arrestation » : un proche signale une garde a vue et
-- demande la mobilisation immediate d'un avocat penaliste.
--
-- Le declarant n'est PAS forcement la personne arretee, et pas necessairement un compte
-- existant : c'est souvent un proche qui appelle dans l'urgence. user_id est donc NULLABLE,
-- et contact_urgence est obligatoire — sans numero joignable, la demande est inexploitable,
-- alors qu'un compte manquant ne l'empeche pas d'etre traitee.
--
-- DONNEES PERSONNELLES SENSIBLES : cette table revele qu'une personne nommee a ete privee de
-- liberte. Elle releve des memes obligations que le reste de la base, avec une sensibilite
-- superieure — voir docs/ERASURE_POLICY.md.
-- ============================================================================

CREATE TABLE IF NOT EXISTS sos_arrests (
    id                      VARCHAR(20)  PRIMARY KEY,

    -- Declarant, quand la demande emane d'un compte connu. NULL si signalement par un proche
    -- non inscrit : exiger un compte ici couterait des minutes au moment ou elles comptent.
    user_id                 VARCHAR(20)  REFERENCES users (id),

    nom_detenu              VARCHAR(255) NOT NULL,
    lieu_arrestation        VARCHAR(255) NOT NULL,
    date_heure_arrestation  TIMESTAMP    NOT NULL,
    contact_urgence         VARCHAR(30)  NOT NULL,

    -- Precisions libres (motif invoque, poste de police, etat de sante...).
    details                 TEXT,

    statut_paiement         VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    statut_dispatch         VARCHAR(16)  NOT NULL DEFAULT 'WAITING',

    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at                 TIMESTAMP,
    dispatched_at           TIMESTAMP,

    CONSTRAINT sos_arrests_statut_paiement_check
        CHECK (statut_paiement IN ('PENDING', 'PAID', 'FAILED')),
    CONSTRAINT sos_arrests_statut_dispatch_check
        CHECK (statut_dispatch IN ('WAITING', 'DISPATCHED', 'FAILED'))
);

-- Le suivi operationnel consiste a lister les demandes en attente, les plus recentes d'abord.
CREATE INDEX IF NOT EXISTS idx_sos_arrests_statut_dispatch
    ON sos_arrests (statut_dispatch, created_at DESC);

-- Un declarant connecte doit pouvoir retrouver ses propres signalements.
CREATE INDEX IF NOT EXISTS idx_sos_arrests_user
    ON sos_arrests (user_id, created_at DESC);
