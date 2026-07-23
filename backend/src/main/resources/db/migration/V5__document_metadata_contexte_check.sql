-- ============================================================================
-- V5__document_metadata_contexte_check.sql
-- ----------------------------------------------------------------------------
-- Remplace l'ancien DocumentMetadataContexteCheckPatcher (ApplicationRunner supprime).
-- Normalise la contrainte CHECK sur document_metadata.contexte_type pour inclure
-- toutes les valeurs de l'enum ContexteDocument (dont PROFIL_UTILISATEUR).
--
-- Sur une base existante : remplace une contrainte obsolete (sans PROFIL_UTILISATEUR).
-- Sur une base neuve : V0 a deja cree une contrainte identique ; on la recree a l'identique.
-- ============================================================================

ALTER TABLE document_metadata DROP CONSTRAINT IF EXISTS document_metadata_contexte_type_check;

ALTER TABLE document_metadata ADD CONSTRAINT document_metadata_contexte_type_check
    CHECK (contexte_type IN ('RECLAMATION', 'MESSENGER', 'DOSSIER', 'GENERAL', 'PROFIL_UTILISATEUR'));
