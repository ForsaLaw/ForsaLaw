-- ============================================================================
-- V2__fix_audit_bytea.sql — Corrige les colonnes audit_log créées en bytea.
-- ----------------------------------------------------------------------------
-- Portage de l'ancien AuditLogSchemaNormalizer (@PostConstruct, supprimé).
-- Sur certaines bases, des colonnes texte de audit_log ont été créées en bytea.
-- Cette migration les reconvertit en text en préservant les données :
--   * d'abord convert_from(..., 'UTF8') ;
--   * repli sur encode(..., 'escape') si des octets non-UTF8 sont présents.
--
-- Idempotente : chaque colonne n'est modifiée QUE si elle est encore de type bytea.
-- No-op sur une base neuve (V0 crée déjà ces colonnes en varchar/text).
-- Flyway s'exécute AVANT la validation JPA, ce qui permet ddl-auto=validate.
-- ============================================================================

DO $$
DECLARE
    col  text;
    cols text[] := ARRAY[
        'id', 'actor_user_id', 'module_name', 'action', 'method',
        'endpoint', 'resource_id', 'ip_address', 'user_agent', 'details'
    ];
BEGIN
    FOREACH col IN ARRAY cols LOOP
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'audit_log'
              AND column_name = col
              AND data_type = 'bytea'
        ) THEN
            BEGIN
                EXECUTE format(
                    'ALTER TABLE public.audit_log ALTER COLUMN %I TYPE text USING convert_from(%I, ''UTF8'')',
                    col, col);
            EXCEPTION WHEN character_not_in_repertoire OR untranslatable_character THEN
                EXECUTE format(
                    'ALTER TABLE public.audit_log ALTER COLUMN %I TYPE text USING encode(%I, ''escape'')',
                    col, col);
            END;
            RAISE NOTICE 'audit_log.% converti de bytea vers text.', col;
        END IF;
    END LOOP;
END $$;
