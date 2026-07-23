-- ============================================================================
-- V8__audit_log_immutable.sql — Journal d'audit append-only (immuable).
-- ----------------------------------------------------------------------------
-- Rejette physiquement tout UPDATE / DELETE sur audit_log via un trigger, meme si
-- un bug applicatif tente de muter une ligne. Les INSERT restent autorises.
--
-- Pourquoi un trigger et non un REVOKE ?
--   * Le role applicatif (`forsalaw` en prod) est PROPRIETAIRE de la table : sous
--     PostgreSQL, le proprietaire conserve tous les droits, un REVOKE UPDATE/DELETE
--     est donc SANS EFFET sur lui.
--   * Le role varie selon l'environnement (ex. `test` sous Testcontainers/CI), donc
--     un REVOKE ... FROM forsalaw code en dur casserait la migration en CI.
--   * Un trigger s'applique a TOUS les roles, proprietaire inclus : c'est l'enforcement
--     fiable et portable.
-- ============================================================================

CREATE OR REPLACE FUNCTION forsalaw_reject_audit_log_mutation()
    RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only: % is not permitted', TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$;

DROP TRIGGER IF EXISTS trg_audit_log_no_mutation ON audit_log;

CREATE TRIGGER trg_audit_log_no_mutation
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW
    EXECUTE FUNCTION forsalaw_reject_audit_log_mutation();
