-- ============================================================================
-- V7__drop_document_access_log_action_check.sql
-- ----------------------------------------------------------------------------
-- Remplace l'ancien ForsaLawApplication.databaseFix (CommandLineRunner supprime).
-- Supprime la contrainte CHECK obsolete sur document_access_log.action, qui, sur
-- d'anciennes bases, ne connaissait pas la valeur SIGNATURE et bloquait la signature
-- de documents. Comportement identique au correctif runtime precedent (simple DROP).
-- ============================================================================

ALTER TABLE document_access_log DROP CONSTRAINT IF EXISTS document_access_log_action_check;
