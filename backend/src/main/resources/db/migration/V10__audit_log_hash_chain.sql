-- ============================================================================
-- V10__audit_log_hash_chain.sql — Chainage cryptographique du journal d'audit.
-- ----------------------------------------------------------------------------
-- POURQUOI :
--   Le trigger de V8 empeche les UPDATE/DELETE passant par SQL, ce qui couvre les bugs
--   applicatifs et les manipulations naives. Il ne couvre PAS quelqu'un capable de
--   desactiver le trigger (ALTER TABLE ... DISABLE TRIGGER) ni la restauration d'une
--   sauvegarde falsifiee. Sans chainage, "verifier que le journal n'a pas ete altere"
--   n'est pas realisable : rien ne lie une ligne a la precedente.
--
--   Chaque ligne porte donc :
--     prev_hash = row_hash de la ligne precedente (ordre des id)
--     row_hash  = sha256( representation canonique de la ligne || '|' || prev_hash )
--   Modifier, supprimer ou inserer une ligne au milieu casse toutes les empreintes
--   suivantes : l'alteration devient detectable.
--
-- LIMITE ASSUMEE :
--   Une chaine stockee dans la meme base peut etre RECALCULEE en totalite par qui a
--   un acces complet a cette base. La tamper-evidence n'est reellement acquise que si
--   la tete de chaine est ancree a l'exterieur (export periodique vers le bucket de
--   sauvegarde, cf. scripts/backup-db.sh). Voir docs/ERASURE_POLICY.md.
--
-- ORDRE DE CHAINAGE :
--   Les identifiants ont la forme 'AAAA-ADT-NNNNN' (zero-padding) : l'ordre
--   lexicographique coincide donc avec l'ordre chronologique d'insertion, y compris
--   au passage a 6 chiffres ('...-099999' < '...-100000').
-- ============================================================================

ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS row_hash  varchar(64);
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS prev_hash varchar(64);

-- Le trigger de V8 rejette tout UPDATE : il faut le neutraliser le temps du remplissage
-- initial, puis le remettre. C'est la seule ecriture de ce type autorisee sur la table,
-- et elle est tracee par l'historique Flyway.
ALTER TABLE audit_log DISABLE TRIGGER trg_audit_log_no_mutation;

WITH RECURSIVE ordered AS (
    SELECT
        a.id,
        row_number() OVER (ORDER BY a.id) AS rn,
        -- Representation canonique : DOIT rester identique a celle de AuditChainService.java
        -- (meme ordre de champs, meme separateur, meme format d'horodatage a la microseconde).
        a.id                                                    || '|' ||
        coalesce(a.module_name, '')                             || '|' ||
        coalesce(a.action, '')                                  || '|' ||
        coalesce(a.method, '')                                  || '|' ||
        coalesce(a.endpoint, '')                                || '|' ||
        coalesce(a.resource_id, '')                             || '|' ||
        coalesce(a.http_status::text, '')                       || '|' ||
        coalesce(a.ip_address, '')                              || '|' ||
        coalesce(a.user_agent, '')                              || '|' ||
        coalesce(a.details, '')                                 || '|' ||
        coalesce(a.actor_user_id, '')                           || '|' ||
        to_char(a.created_at, 'YYYY-MM-DD"T"HH24:MI:SS.US')     AS canonical
    FROM audit_log a
),
chain AS (
    SELECT
        o.rn,
        o.id,
        repeat('0', 64) AS prev_hash,
        encode(sha256(convert_to(o.canonical || '|' || repeat('0', 64), 'UTF8')), 'hex') AS row_hash
    FROM ordered o
    WHERE o.rn = 1

    UNION ALL

    SELECT
        o.rn,
        o.id,
        c.row_hash,
        encode(sha256(convert_to(o.canonical || '|' || c.row_hash, 'UTF8')), 'hex')
    FROM ordered o
    JOIN chain c ON o.rn = c.rn + 1
)
UPDATE audit_log a
   SET row_hash  = c.row_hash,
       prev_hash = c.prev_hash
  FROM chain c
 WHERE a.id = c.id;

ALTER TABLE audit_log ENABLE TRIGGER trg_audit_log_no_mutation;

-- NOT NULL : toute future ecriture qui oublierait de chainer echoue bruyamment plutot que
-- de creer un trou silencieux dans la chaine.
ALTER TABLE audit_log ALTER COLUMN row_hash  SET NOT NULL;
ALTER TABLE audit_log ALTER COLUMN prev_hash SET NOT NULL;
