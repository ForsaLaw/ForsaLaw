-- ============================================================================
-- V11__rag_schema_bge_m3.sql — Refonte du schema RAG pour bge-m3 + 3 niveaux de corpus.
-- ----------------------------------------------------------------------------
-- La table creee en V1 etait un echafaudage : AUCUN code Java ne la referencait et elle
-- n'a jamais contenu de donnees. Elle peut donc etre remaniee sans precaution de migration.
--
-- Trois corrections :
--
--  1. embedding vector(1536) -> vector(1024)
--     1536 correspond aux modeles OpenAI. Le modele retenu est bge-m3 (auto-heberge, MIT,
--     1024 dimensions, 8192 tokens de contexte), choisi parce que les documents du niveau 3
--     sont des pieces confidentielles de cabinets : elles ne doivent pas transiter par une
--     API tierce pour etre vectorisees.
--     La colonne est SUPPRIMEE puis recreee plutot que convertie : vector(1536) et
--     vector(1024) ne sont pas convertibles implicitement, et la table est vide.
--
--  2. Index IVFFlat avec lists = 1 -> HNSW
--     Avec lists = 1, IVFFlat ne definit qu'une seule liste : chaque recherche parcourt la
--     totalite de la table. L'index existait sans rien accelerer. HNSW (m = 16,
--     ef_construction = 64) donne une vraie recherche approchee par voisinage.
--     Construire l'index maintenant, sur une table vide, est gratuit ; le faire plus tard
--     sur un corpus complet couterait des heures.
--
--  3. Colonnes de versionnement et de cloisonnement
--     status / effective_date : un article abroge reste consultable mais ne doit plus etre
--     cite comme droit en vigueur.
--     tier : 1 = legislation (JORT), 2 = jurisprudence (CEJJ), 3 = coffre prive de cabinet.
--     tenant_id : cabinet proprietaire pour le niveau 3.
-- ============================================================================

-- ─── 1. Dimension d'embedding ───────────────────────────────────────────────

-- L'index depend de la colonne : il doit disparaitre en premier.
DROP INDEX IF EXISTS idx_legal_document_chunk_embedding;

ALTER TABLE legal_document_chunk DROP COLUMN IF EXISTS embedding;
ALTER TABLE legal_document_chunk ADD COLUMN embedding vector(1024);

COMMENT ON COLUMN legal_document_chunk.embedding IS
    'Vecteur bge-m3 (1024 dimensions, similarite cosinus).';

-- ─── 2. Versionnement ───────────────────────────────────────────────────────

ALTER TABLE legal_document_chunk
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS effective_date DATE,
    ADD COLUMN IF NOT EXISTS tier INTEGER,
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20),
    ADD COLUMN IF NOT EXISTS source_reference VARCHAR(500);

ALTER TABLE legal_document_chunk
    DROP CONSTRAINT IF EXISTS chk_legal_document_chunk_status;
ALTER TABLE legal_document_chunk
    ADD CONSTRAINT chk_legal_document_chunk_status
        CHECK (status IN ('ACTIVE', 'SUPERSEDED'));

ALTER TABLE legal_document_chunk
    DROP CONSTRAINT IF EXISTS chk_legal_document_chunk_tier;
ALTER TABLE legal_document_chunk
    ADD CONSTRAINT chk_legal_document_chunk_tier
        CHECK (tier IN (1, 2, 3));

-- Un contenu de niveau 3 sans cabinet proprietaire serait visible de tous : interdit.
ALTER TABLE legal_document_chunk
    DROP CONSTRAINT IF EXISTS chk_legal_document_chunk_tenant;
ALTER TABLE legal_document_chunk
    ADD CONSTRAINT chk_legal_document_chunk_tenant
        CHECK ((tier = 3 AND tenant_id IS NOT NULL) OR (tier <> 3 AND tenant_id IS NULL));

COMMENT ON COLUMN legal_document_chunk.status IS
    'ACTIVE = droit en vigueur ; SUPERSEDED = abroge ou remplace, conserve pour l''historique.';
COMMENT ON COLUMN legal_document_chunk.effective_date IS
    'Date d''entree en vigueur (JORT) ou date de l''arret (jurisprudence).';
COMMENT ON COLUMN legal_document_chunk.tier IS
    '1 = legislation (JORT), 2 = jurisprudence (CEJJ), 3 = coffre prive de cabinet.';

-- Convention d'identifiant alignee sur le reste du schema (2026-FIRM-00001), et NON un UUID.
COMMENT ON COLUMN legal_document_chunk.tenant_id IS
    'Cabinet proprietaire pour le niveau 3 (format 2026-FIRM-00001). '
    'CLOISONNEMENT NON APPLIQUE : cette colonne ne protege rien par elle-meme. '
    'Une isolation reelle exige Postgres RLS, ou un predicat obligatoire impose a toutes '
    'les requetes. Tant que ce n''est pas en place, aucune API ne doit exposer le niveau 3.';

COMMENT ON COLUMN legal_document_chunk.source_reference IS
    'Provenance : numero de JORT, bulletin CEJJ, ou identifiant du document deverse.';

-- ─── 3. Index ───────────────────────────────────────────────────────────────

-- HNSW : m = nombre de liens par noeud, ef_construction = largeur d'exploration a la
-- construction. Ces valeurs sont les defauts recommandes de pgvector : bon compromis
-- rappel / memoire pour un corpus de cette taille.
CREATE INDEX idx_legal_document_chunk_embedding
    ON legal_document_chunk
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- Le filtre applique a presque toutes les recherches : niveau + droit en vigueur.
CREATE INDEX IF NOT EXISTS idx_legal_document_chunk_tier_status
    ON legal_document_chunk (tier, status);

-- Index partiel : seules les lignes de niveau 3 portent un cabinet.
CREATE INDEX IF NOT EXISTS idx_legal_document_chunk_tenant
    ON legal_document_chunk (tenant_id)
    WHERE tenant_id IS NOT NULL;

-- Resolution directe d'un article cite ("Code des obligations, article 242").
CREATE INDEX IF NOT EXISTS idx_legal_document_chunk_article
    ON legal_document_chunk (code_name, article_reference, status);

-- ─── 4. Propositions d'abrogation ───────────────────────────────────────────
-- Detecter automatiquement « يلغى وتعوض أحكام الفصل ... » puis marquer l'article
-- SUPERSEDED sans controle humain, c'est risquer que l'assistant presente du droit
-- abroge comme etant en vigueur — ou l'inverse. La detection PROPOSE donc, elle
-- n'applique pas : un administrateur confirme.

CREATE TABLE IF NOT EXISTS rag_supersede_proposal (
    id                BIGSERIAL PRIMARY KEY,
    code_name         VARCHAR(255) NOT NULL,
    article_reference VARCHAR(255) NOT NULL,
    effective_date    DATE,
    source_reference  VARCHAR(500),
    matched_text      TEXT,
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                      CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED')),
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at       TIMESTAMP,
    reviewed_by       VARCHAR(20)
);

CREATE INDEX IF NOT EXISTS idx_rag_supersede_proposal_status
    ON rag_supersede_proposal (status, created_at);

COMMENT ON TABLE rag_supersede_proposal IS
    'Abrogations detectees dans les textes ingeres, en attente de confirmation humaine. '
    'Aucune ligne de legal_document_chunk n''est passee a SUPERSEDED automatiquement.';

COMMENT ON TABLE legal_document_chunk IS
    'Chunks de textes juridiques tunisiens pour le RAG (embeddings bge-m3, 1024 dimensions).';
